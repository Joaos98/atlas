# Spec: Deduplicate Health Connect Webhook Syncs

**Status:** Ready for implementation
**Date:** 2026-08-04
**Supersedes:** `webhook-sync-deduplication-plan.md`
**Component:** `aio-fitness` (Atlas backend) — `SyncService`, `WorkoutLog`, `WorkoutLogRepository`

> **Implementation note (2026-08-12):** this document was lost and reconstructed from
> transcript on 2026-08-12. The work itself is complete: migration applied to
> production, jar deployed (commit `792b7e2`), verified. Sections §7–§8 read as
> historical; the BLOCKING §10.1 is resolved (local profile now points at a Neon dev
> branch with `ddl-auto=validate`, later restored to production with `validate` kept).

> **Partly superseded (2026-08-12) by [`sync-source-allowlist-spec.md`](sync-source-allowlist-spec.md).**
> Two decisions in this document have been overtaken and must not be followed as written:
>
> - **§4, "keep the origin/method filter exactly as-is."** The hardcoded
>   `com.xiaomi.wearable` + `automatically_recorded` filter is replaced by a configurable
>   allow-list with quarantine of rejected entries. The *reason* for the filter recorded
>   here — Google activity-detection records drift, so an instant-based signature cannot
>   dedup them — remains the evidence base for that work and is unchanged.
> - **§5.2, "do not add `unique = true`."** Correct under `ddl-auto=validate`, where
>   Hibernate ignores constraints and the §5.1 hand-run migration was the single source of
>   truth. The self-hosting refactor switched the application to
>   `ddl-auto=update` on SQLite, which inverts the premise: the annotation is now the only
>   thing that would create the index, and its absence means a fresh install has **no**
>   unique index on `sync_signature` and therefore no cross-sync dedup at all. The
>   annotation is being added. Neon is unaffected — it already carries
>   `ux_workout_logs_sync_signature` from §5.1.
>
> Everything else here — §1 problem statement, §2 evidence, §3 signature and transaction
> design, §5.4–§5.6, §6 — stands.

> **BLOCKING PREREQUISITE — see §10.1.** The local profile currently runs
> `ddl-auto=update` against the production database (confirmed 2026-08-04). Implementing
> §5.2 while that is true will alter the production schema out-of-band, and none of the
> §8 verification can be run without polluting real workout data. Resolve §10.1 first.

---

## 1. Problem

`POST /api/sync` inserts duplicate `workout_logs` rows.

The HC Webhook app fires its single scheduled job **twice** on most days, roughly 5 seconds
apart. Each firing generates its own payload with identical exercise content. Because every
sync hits a Render cold start and takes 144–173 seconds to respond, the two requests overlap
almost completely.

The current guard in `SyncService` is a read-before-write:

```java
boolean exists = workoutLogRepository.existsByLogDateAndWorkoutTypeAndDurationMinutes(...);
if (exists) { skipped++; continue; }
```

Under concurrency both requests evaluate `exists == false` and both insert.

Two secondary defects in the same code:

- **The uniqueness key is wrong.** `(logDate, workoutType, durationMinutes)` with duration
  rounded up to whole minutes will silently discard a genuine second workout of the same
  type and similar length on the same day.
- **The log date is computed in the wrong timezone.** `ZoneId.systemDefault()` resolves to
  the container's zone (UTC on Render), not the user's (America/Sao_Paulo, UTC−3).

### 1.1 Not the cause

The original plan attributed the duplicates to client timeout retries. Delivery logs
disprove this: the two Aug 4 deliveries fired at `01:00:00.893Z` and `01:00:06.160Z`,
5.27 s apart, each with a freshly generated payload, and **both returned 200**. A timeout
retry cannot fire 5 seconds into a 173-second request. This is a duplicate trigger inside
the app, not a retry. Attempts to fix it app-side have failed.

The distinction does not change the fix, but it does mean the duplicate sends will
continue and the backend must be authoritative.

---

## 2. Evidence base

Six deliveries analysed (Jul 29 – Aug 3 local, daily at 22:00 local / 01:00 UTC).

| Finding | Evidence |
|---|---|
| App double-fires most days | Two Aug 4 deliveries 5.27 s apart, identical exercise content, both 200 |
| Every sync is a cold start | Response times 144 522 / 152 358 / 156 622 / 169 522 / 173 457 ms |
| App sends a **delta**, not a time window | Jul 31 15:09 workout sent once and never again, while Jul 31 19:15 (six hours later, same day) was re-sent after being modified |
| Each sync carries over the previous sync's newest workout | Consistent across all four consecutive pairs |
| Xiaomi records are stable | Whole-second timestamps; byte-identical across re-sends (`2026-08-01T18:30:27Z`, `2026-08-03T20:59:32Z`) |
| Google-origin records are **not** stable | Same session re-sent as `22:15:36.897Z`/1744 s then `22:15:41.624Z`/1814 s |
| Payload timestamp formats vary within one payload | `2026-08-02T15:13:36.618Z` and `2026-08-03T20:59:32Z` |
| Google `automatically_recorded` entries are phone activity-detection | Fractional-second timestamps, never a Xiaomi twin, `type 79`, 28–30 min |
| Google `unknown` entries are mirrors | Whole-second, always paired with a Xiaomi record, identical field-for-field |

---

## 3. Solution

Replace the read-before-write check with a **database-enforced unique constraint** on a
deterministic per-exercise signature. Inserts go through JPA inside a dedicated
`REQUIRES_NEW` transaction; a duplicate raises `DataIntegrityViolationException`, which the
caller catches and counts as skipped.

Signature is derived from the two fields that identify a Xiaomi-recorded session and never
change: **start instant (epoch millis) and Health Connect exercise type.**

```
signature := Instant.parse(start_time).toEpochMilli() + "|" + healthConnectType
```

Duration is deliberately excluded — it is mutable data, not identity.

A native `INSERT ... ON CONFLICT DO NOTHING` was the original choice and is **rejected** —
see §5.1.3. `workout_logs_seq` is `INCREMENT BY 50`, so a native statement calling
`nextval()` would compete with Hibernate's pooled ID allocator and produce intermittent
primary-key collisions.

The transaction boundary is the load-bearing detail of the replacement. Two failure modes
must both be avoided:

- **Catching the violation in the same transaction that raised it does not work.** The
  transaction is already marked rollback-only; swallowing the exception only relocates the
  failure to commit time as `UnexpectedRollbackException`.
- **Sharing a transaction with the rest of the batch does not work.** If `sync()` is
  transactional and the insert joins it, one duplicate marks the whole transaction
  rollback-only, the method reports success, and every legitimate workout in the batch is
  lost at commit.

The insert therefore lives in its own bean under `REQUIRES_NEW`, the exception propagates
out of it, and the catch sits in the caller.

---

## 4. Explicit non-changes

These were considered and deliberately rejected. Do not "improve" them during implementation.

| Decision | Rationale |
|---|---|
| **Keep the origin/method filter exactly as-is** (`com.xiaomi.wearable` + `automatically_recorded`) | The Google `automatically_recorded` orphans are phone activity-detection, not recorded workouts. Their start times also drift between syncs, so an instant-based signature would not dedup them. |
| **`DO NOTHING`, not `DO UPDATE`** | The app has a manual edit path (`PUT /api/workout-logs/{id}`). `DO UPDATE` would silently revert manual duration corrections on the next sync. Record revision was observed only in the excluded Google origin. |
| **No request-level idempotency key** | The webhook envelope `id` is a per-attempt local log id and is not sent to the server. `payload.timestamp` differs between the two firings, so body hashing also fails. Per-entry keying is the only level that works. |
| **No `metadata.id`** | Confirmed absent from the payload. Metadata carries only `data_origin`, `recording_method`, `device`. |
| **Keep-alive ping is optional and separate** | It would remove the concurrency window but not the double-send. It is a mitigation, not the fix, and must not be treated as one. |

---

## 5. Changes

### 5.1 Database migration

Run **before** deploying the new jar. There is no Flyway/Liquibase in this project; schema
is hand-managed and `spring.jpa.hibernate.ddl-auto=validate`.

Run each statement separately, in this order, so a problem with the cleanup in step 3 cannot
affect the dedup fix.

```sql
-- 1. Signature column
ALTER TABLE workout_logs ADD COLUMN sync_signature VARCHAR(100);

-- 2. Uniqueness guarantee — this is the actual fix
CREATE UNIQUE INDEX ux_workout_logs_sync_signature ON workout_logs (sync_signature);

-- 3. Cleanup: orphaned column from a removed feature (see §5.1.1)
ALTER TABLE workout_logs DROP COLUMN calories;
```

The signature column is nullable. Postgres treats `NULL`s as distinct in a unique index, so
existing rows and manually created workouts (which have no signature) do not collide with
each other or with synced rows.

Rehearse all three on the §10.1 dev branch before running them against production.

#### 5.1.1 Dropping `calories`

`workout_logs.calories` exists in the database but is not mapped by the `WorkoutLog` entity —
a leftover from a removed feature, surviving because `ddl-auto=update` never drops columns
and `validate` ignores unmapped ones. Confirmed for removal 2026-08-04.

This is independent of the dedup work and carries no deploy-ordering constraint: the current
code never references the column, so the drop is safe before or after the jar goes out.
`DROP COLUMN` in Postgres is a metadata-only operation and does not rewrite the table.

**This drop discards real data — it is not an empty-column cleanup.** Measured 2026-08-04:

```
total = 435, with_data = 428
```

428 rows carry calorie values, entered manually under a previous version of the feature. The
feature was removed because Health Connect's webhook payload contains no calorie data for
exercise entries, so the column can never be repopulated by sync. Discarding the historical
values was decided knowingly on 2026-08-04.

The drop is irreversible once Neon's point-in-time restore window elapses. If the values are
ever wanted, export before running it:

```sql
SELECT id, log_date, calories FROM workout_logs WHERE calories IS NOT NULL;
```

#### 5.1.2 Resolved: `id` generation

Confirmed 2026-08-04 — `workout_logs.id` has **no column default and is not an identity
column**:

```
column_default = null, is_identity = NO, identity_generation = null
```

Hibernate therefore supplies the value from a standalone sequence, and the native insert in
§5.3 **must** provide `id` explicitly via `nextval('<SEQUENCE_NAME>')`. Omitting the column
is not an option here.

Sequence name confirmed 2026-08-04: **`workout_logs_seq`** — the only sequence in the `public`
schema, name matching the table.

#### 5.1.3 RESOLVED — native upsert rejected

Measured 2026-08-04:

```
increment_by = 50, start_value = 1, last_value = 751, cache_size = 1
```

`INCREMENT BY 50` means Hibernate is using its default pooled optimizer: it draws one value
from the sequence and serves a block of 50 IDs from memory without touching the database. A
native insert calling bare `nextval('workout_logs_seq')` would draw from the same sequence
independently and hand out IDs inside a range Hibernate has already reserved — intermittent
primary-key collisions, surfacing days after deployment, with no obvious connection to this
change.

**The native `ON CONFLICT` insert is therefore abandoned.** Inserts go through JPA so
Hibernate retains sole ownership of ID allocation. See §5.4.

This was the runner-up approach in the original review. It is acceptable here because
conflict volume is low — roughly one per sync, two or three on a double-fire day — which
makes the exception-churn objection to it nearly worthless. Transaction safety, the only
remaining argument for the native insert, is obtained just as well from an explicit
`REQUIRES_NEW` boundary in its own bean.

Also rejected: `ALTER SEQUENCE workout_logs_seq INCREMENT BY 1` combined with
`allocationSize = 1` on the entity. It alters ID generation for every `WorkoutLog` write in
order to accommodate a single query.

Note that the unique index from §5.1 remains essential — it is what raises the exception.

### 5.2 Entity — `WorkoutLog`

```java
@Column(name = "sync_signature", length = 100)
private String syncSignature;
```

Do **not** add `unique = true`. Under `ddl-auto=validate` Hibernate does not verify
constraints or indexes, so the annotation would be inert documentation that implies the
schema is JPA-managed when it is not. The migration in §5.1 is the single source of truth.

Lombok `@Getter`/`@Setter` on the class already cover the accessors.

### 5.3 Repository — `WorkoutLogRepository`

**Remove** (only caller is `SyncService`; verified by grep):

```java
boolean existsByLogDateAndWorkoutTypeAndDurationMinutes(LocalDate, WorkoutType, int);
```

Keep `existsByWorkoutType` — it is used by `WorkoutTypeService:34`.

**No additions.** The inherited `saveAndFlush` from `JpaRepository` is sufficient.

### 5.4 New component — `WorkoutLogInserter`

```java
package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkoutLogInserter {

    private final WorkoutLogRepository repository;

    public WorkoutLogInserter(WorkoutLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists in its own transaction so a unique-constraint violation cannot poison the
     * caller's. Throws DataIntegrityViolationException if the signature already exists —
     * the caller is responsible for catching it. Do not catch it here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(WorkoutLog workoutLog) {
        repository.saveAndFlush(workoutLog);
    }
}
```

Four details, all mandatory. Each one silently breaks the fix if changed:

- **It must be a separate bean, not a method on `SyncService`.** Spring's `@Transactional`
  is applied by a proxy. A self-invocation from `sync()` to a method on the same class
  bypasses the proxy entirely and the annotation does nothing at all — with no warning.
- **`saveAndFlush`, not `save`.** `save()` defers the INSERT to commit, which happens after
  this method returns. The statement must execute where we expect it.
- **The exception must propagate out of this method.** Catching it here leaves the
  transaction marked rollback-only, and Spring then throws `UnexpectedRollbackException` at
  commit. Letting it escape rolls this transaction back cleanly and hands the caller an
  ordinary exception.
- **`REQUIRES_NEW`, not the default `REQUIRED`.** Today `sync()` is not transactional, so
  the two behave identically. If anyone ever annotates `sync()`, `REQUIRED` would join the
  outer transaction, and one duplicate would mark the entire batch rollback-only and discard
  every legitimate workout at commit. `REQUIRES_NEW` suspends the outer transaction and
  keeps the failure isolated.

### 5.5 Service — `SyncService.sync()`

Replace the body of the processing loop. Required ordering change: the exercise type must be
parsed **before** the signature is built, so the in-request `seen` check moves after type
parsing.

```java
for (SyncRequest.ExerciseEntry entry : filtered) {
    int healthConnectType;
    try {
        healthConnectType = Integer.parseInt(entry.getType());
    } catch (NumberFormatException e) {
        log.warn("Skipping exercise with non-numeric type: {}", entry.getType());
        skipped++;
        continue;
    }

    Instant start;
    try {
        start = Instant.parse(entry.getStart_time());
    } catch (DateTimeParseException e) {
        log.warn("Skipping exercise with unparseable start_time: {}", entry.getStart_time());
        skipped++;
        continue;
    }

    String signature = start.toEpochMilli() + "|" + healthConnectType;

    if (!seen.add(signature)) {
        skipped++;
        continue;
    }

    Optional<ExerciseTypeMapping> mapping = mappingRepository.findById(healthConnectType);
    if (mapping.isEmpty()) {
        log.info("Unmapped Health Connect exercise type: {}. Add a mapping to auto-log this type.",
                healthConnectType);
        skipped++;
        continue;
    }

    WorkoutLog workoutLog = new WorkoutLog();
    workoutLog.setWorkoutType(mapping.get().getWorkoutType());
    workoutLog.setLogDate(start.atZone(appZone).toLocalDate());
    workoutLog.setDurationMinutes((int) Math.ceil(entry.getDuration_seconds() / 60.0));
    workoutLog.setSyncSignature(signature);

    try {
        workoutLogInserter.insert(workoutLog);
        created++;
    } catch (DataIntegrityViolationException e) {
        log.debug("Duplicate sync signature {}, skipping", signature);
        skipped++;
    }
}
```

`SyncService` gains a constructor-injected `WorkoutLogInserter`.

**`sync()` must not be annotated `@Transactional`.** Add a comment saying so. It is currently
non-transactional by omission rather than by intent, and the `REQUIRES_NEW` boundary in §5.4
exists precisely so that a future annotation cannot destroy a batch.

On the breadth of the catch: `DataIntegrityViolationException` is broader than "duplicate
signature", so in principle a different constraint failure would be miscounted as a skip. On
this path it is not reachable — `workout_type_id` comes from an `ExerciseTypeMapping` row
that was just loaded, so the foreign key cannot fail, and every other column is nullable. The
signature index is the only constraint that can realistically fire.

Consequences:

- `seen` is now keyed on the same normalized signature as the database constraint. It
  previously used the raw `start_time` string and omitted the type, so it could disagree
  with the persisted key in both directions.
- `Instant.parse` normalizes `...:32Z` and `...:32.000Z` to the same epoch value.
- `DateTimeParseException` is now caught per-entry. Previously it would propagate and fail
  the whole request.
- `parseUtcDate(...)` becomes unused and should be deleted.
- Failed inserts consume sequence values. The resulting gaps in `workout_logs.id` are
  harmless.

### 5.6 Timezone

Add to `application.properties`:

```properties
app.timezone=America/Sao_Paulo
```

Inject into `SyncService` as a `ZoneId` field (`appZone`, used in §5.5) and into
`WorkoutLogService`, replacing both bare `LocalDate.now()` calls:

- `WorkoutLogService:83` — `LocalDate.now()` in `calculateStreaks`
- `WorkoutLogService:115` — `LocalDate.now()` for `thisWeekSunday`

Use the zone ID, not a fixed `-03:00` offset. Brazil dropped DST in 2019, but the zone ID
carries the historical rules and any future change.

**Severity note:** this is latent, not active. Every workout in the sample data starts
between 12:13 and 19:15 local, well clear of the 21:00 local boundary where the UTC date
rolls over. It has probably never produced a wrong heatmap square. Fix it because it is one
line, not because it is urgent.

---

## 6. One-time data issue

Existing `workout_logs` rows have `sync_signature = NULL` and **cannot be backfilled** —
the table stores only `logDate`, not the start instant, so the signature is not
reconstructible from existing data.

Consequence: on the first sync after deployment, the workout carried over from the previous
sync is already in the database with a `NULL` signature, does not conflict, and will be
inserted a second time.

**Expected impact: exactly one duplicate row** (two or three if that sync also double-fires).
Delete it manually. Every row inserted from that point forward carries a signature, and the
constraint holds from then on.

Do not build a transition-period fallback for this. It is not worth the code.

---

## 7. Deployment procedure

Order is mandatory. Deploying the jar first will fail startup with a schema validation
error, on a host that already takes ~170 s to boot.

1. Resolve §10.1. Do not start the application locally with the §5.2 entity field in place
   until the local profile points at a non-production database.
2. Run the §5.1 migration against the production database (the one in
   `SPRING_DATASOURCE_URL`). If `ALTER TABLE` reports that `sync_signature` already exists,
   the column was created out-of-band by a local `ddl-auto=update` run — **the index is then
   almost certainly missing.** Skip the `ALTER` and run the `CREATE UNIQUE INDEX` alone.
3. Verify: `\d workout_logs` shows the column and `ux_workout_logs_sync_signature`.
4. Deploy the jar.
5. Confirm startup — no `SchemaManagementException`.
6. After the next scheduled sync, check for the single expected duplicate from §6 and
   delete it.

---

## 8. Verification

There is currently no test coverage on this path (`AtlasApplicationTests` only). The native
`ON CONFLICT` query cannot be meaningfully tested against H2; a real Postgres is required,
which means adding Testcontainers.

**§10.1 must be resolved before any of this can run.** Cases 2 and 3 insert real workout
rows and require cleanup afterwards; against the production database that corrupts real
history. A Neon dev branch is a hard prerequisite for verification, not a convenience.

**Decision required:** add Testcontainers, or verify manually against the dev branch. For a
single-user personal project, manual verification is defensible.

Cases to cover either way:

| # | Case | Expected |
|---|---|---|
| 1 | Same payload posted twice sequentially | 1 row |
| 2 | Same payload posted twice **concurrently** | 1 row — this is the actual bug |
| 3 | Two distinct workouts, same day, same type, same rounded duration | **2 rows** — regression guard for the old key |
| 4 | Same instant as `...:32Z` and `...:32.000Z` | 1 row |
| 5 | Cross-origin mirror pair (Xiaomi + Google `unknown`) | 1 row |
| 6 | Unmapped type / non-numeric type / malformed `start_time` | Skipped, batch continues, HTTP 200 |

Case 2 is the one that matters. Sequential testing will pass against the current buggy code
and prove nothing.

Real payloads for cases 2 and 5 are in the delivery logs from Aug 4 and Aug 3 respectively.

---

## 9. Rollback

Revert the jar. Leave the column and index in place — they are nullable and unused by the
previous code, so the old build runs against the new schema without modification. No
down-migration needed.

---

## 10. Risks and open items

### 10.1 BLOCKING — the local profile writes to production

`application-local.properties` sets `spring.jpa.hibernate.ddl-auto=update` against
`ep-purple-wave-acwdd93a.sa-east-1.aws.neon.tech/neondb`, **confirmed 2026-08-04 to be the
same database Render uses.** Local development currently reads, writes, and migrates
production.

Two consequences for this work:

- Starting the app locally after adding the §5.2 entity field will auto-create
  `sync_signature` in production **without** the unique index, because §5.2 deliberately
  omits `unique = true`. Production then boots cleanly under `validate`, and every
  subsequent sync fails with Postgres `42P10` — *"there is no unique or exclusion
  constraint matching the ON CONFLICT specification"* — returning 500 and halting all
  workout syncing. Loud rather than silent, but a broken deploy regardless.
- §8 verification cannot be performed. Cases 2 and 3 insert real rows into real workout
  history.

**Resolution (do this first):**

1. Create a Neon branch from `main` (e.g. `dev`). Branches are copy-on-write and instant.
2. Repoint `application-local.properties` at the branch connection string.
3. Set local `ddl-auto=validate`, matching production.

On step 3: `validate` costs a manual `ALTER TABLE` whenever an entity gains a field, and in
exchange local becomes a faithful rehearsal of the production deploy — a missing migration
fails on the development machine instead of on Render. Given that `update` produced this
situation, the convenience is not worth retaining.

**One-time audit while doing this:** `ddl-auto=update` never drops columns. If any entity
field has ever been renamed or removed, the production tables still carry the orphan.
Compare each entity's fields against `\d <table>` output.

### 10.2 Credentials in `application-local.properties`

That same file contains a plaintext Neon password, a Gemini API key, and the sync API key
(value redacted 2026-08-12; it was quoted in full here). The sync key is the only thing
protecting `POST /api/sync`. Separately from this work: rotate all three and move them to
environment variables, matching how `application.properties` already handles them.

Also note `SyncController:30` logs the sync API key in full at startup.

**Superseded 2026-08-12** by [`secrets-handling-spec.md`](secrets-handling-spec.md), which
found that the file is gitignored and never committed, that the live leak paths are the
build artifacts and the startup log line, and that this paragraph was itself one of them.

**Closed 2026-08-16.** The log line is deleted, the file is deleted, and a regression test
(`SecretsNotBundledTest`) fails if it ever returns to a build output. The Neon password died
with the database; the provider key is no longer an environment variable at all, having moved
into `app_settings` with to-do §1. Rotating the two surviving keys is the owner's, and is the
only part of this item still open.

### 10.3 Unverified inference — mostly resolved 2026-08-13

The delta hypothesis in §2 requires that the carried-over workout is re-sent because Health
Connect reports it as modified. This fits every observation but cannot be confirmed from
payloads alone, since the re-sent values are sometimes identical. It does not affect the
design — the constraint is correct regardless of why a record reappears.

**Update:** the sender's own documentation (see
[`webhook-sender-spec.md`](webhook-sender-spec.md) §1.2 — the app is
[open source](https://github.com/mcnaveen/health-connect-webhook)) states it sends a
**rolling 48-hour window** filtered to records new or updated since the last successful sync
watermark, *per record type*. That confirms the delta reading and gives the carry-over a
simpler explanation than modification: consecutive syncs less than 48 hours apart **overlap**,
so the newest workout falls inside both windows and the watermark is what keeps the rest out.

Likewise the double-fire: upstream documents **3 attempts with exponential backoff**, and this
install's cold starts take 144–173 s. A client-side timeout followed by a retry — while the
original request completes successfully anyway — fits "two deliveries 5.27 s apart, identical
content, both 200" better than the app firing twice by design. Still a hypothesis; confirming
it needs the app's timeout value.

### 10.4 Watch after deploy

If a delivery ever returns non-200, confirm the following sync includes the records the
failed one carried. The Jul 31 gap suggests the app advances its changes token only on
success, but that has not been directly verified. (Upstream states failed deliveries are
retried on the next successful trigger, which is consistent with this.)
