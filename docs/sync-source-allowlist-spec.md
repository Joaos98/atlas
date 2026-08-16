# Spec: Configurable Sync Sources with Quarantine

**Status:** Refined, ready for implementation
**Date:** 2026-08-12
**Implements:** `atlas-generalization-todos.md` §3
**Builds on:** [`webhook-sync-deduplication-spec.md`](webhook-sync-deduplication-spec.md) — supersedes its §4 filter decision and §5.2 constraint decision
**Component:** `server` — `SyncService`, `WorkoutLog`, new `SyncSource` / `QuarantinedEntry`; `ui` — `SettingsView`, dashboard

---

## 1. Problem

`SyncService` hardcodes the set of records it will accept:

```java
private static final String EXPECTED_ORIGIN = "com.xiaomi.wearable";
private static final String EXPECTED_METHOD = "automatically_recorded";
```

Everything else is filtered out before dedup runs, before any logging, and without
appearing in the response — a payload that is 100% Samsung Health returns
`{created: 0, skipped: 0}` and writes nothing to the log. A user with any device other
than the original author's gets an empty app and no explanation.

### 1.1 Why the filter exists

Not arbitrary. Per the dedup spec §2, Google-origin `automatically_recorded` entries are
phone activity-detection, and their timestamps **drift between syncs** — the same session
was re-sent as `22:15:36.897Z`/1744 s and then `22:15:41.624Z`/1814 s. The dedup signature
is `startEpochMillis|healthConnectType`, so a drifting record produces a new signature on
every sync and can never be deduplicated. Xiaomi records are whole-second and byte-identical
across re-sends.

The filter is therefore a **dedup-safety gate**, not a device preference. That distinction
drives every decision below.

### 1.2 What the filter is *not* needed for

Google-origin `unknown` entries are mirrors of a Xiaomi record — whole-second, paired,
identical field-for-field. They carry the *same signature* as their twin, so the unique
constraint already collapses them (dedup spec §8 case 5). The allow-list's irreplaceable
job is narrow: keep phone activity-detection out.

### 1.3 Prerequisite defect

**The unique constraint does not exist on a fresh install.** `WorkoutLog.syncSignature` is
declared without `unique = true` — deliberately, per dedup spec §5.2, because that spec
shipped under `ddl-auto=validate` where the annotation would have been inert and the
hand-run `CREATE UNIQUE INDEX` was authoritative. The self-hosting refactor moved the app to
SQLite with `ddl-auto=update`, which inverts that reasoning. The generated schema is:

```sql
CREATE TABLE workout_logs (id integer, duration_minutes integer, log_date date,
  sync_signature varchar(100), workout_type_id bigint, primary key (id))
```

No unique index. `WorkoutLogInserter`'s documented contract — "throws
`DataIntegrityViolationException` if the signature already exists" — cannot fire. Only the
in-request `seen` HashSet still works, which catches a double-fire only when both
copies land in the same POST. The webhook fires twice ~5 s apart as separate requests, so on
a fresh install every double-fire day inserts duplicates.

This must be fixed **first**. Relaxing the origin filter while the constraint is missing
would admit records that nothing deduplicates.

### 1.4 The constraint that shapes the solution

**The sender transmits a delta, not a time window** (dedup spec §2): each sync carries new
changes plus the previous sync's newest workout. An entry the backend refuses is, in
general, *never re-sent*. So "reject unknown sources and let the user enable them later"
means permanent data loss for everything received before the click — not deferred import.
Rejection must therefore be paired with quarantine.

---

## 2. Solution

1. Restore the unique index on `sync_signature` so `ddl-auto=update` creates it.
2. Replace the hardcoded constants with a database-backed allow-list keyed on
   `(data_origin, recording_method)`.
3. Entries from a source that is not allowed are **quarantined**, not dropped: stored raw,
   surfaced in the UI, and replayed through the normal insert path when the user enables the
   source.
4. Report rejections in the sync response and the logs.

Fresh installs ship with an **empty** allow-list. No vendor appears in the product defaults;
the app learns the user's sources from real payloads and asks.

---

## 3. Explicit non-changes

Considered and deliberately rejected. Do not "improve" these during implementation.

| Decision | Rationale |
|---|---|
| **Signature stays `startEpochMillis \| healthConnectType`** | A tolerance window (rounding start to N minutes) would make drifting sources dedup-able, but re-introduces the exact defect dedup spec §1 removed: a lossy key that silently discards a genuine second workout of the same type on the same day. §8 case 3 is the regression guard for this. We also have zero retained payloads to tune a window against. |
| **No per-source drift detection, no auto-disable** | Detecting drift requires at least two syncs, so the junk rows arrive before the app could react — and it would then be overriding an explicit user decision. A generic warning at enable time is the whole mitigation. |
| **No seeded known-bad deny-list** | The exact Google origin package string appears nowhere in the repo, the dedup spec, or any retained payload. A deny-list would ship empty and would never cover an unseen vendor. Reject-unknown-by-default achieves the same protection without needing the string. |
| **Allow-list is not a properties setting** | It must be editable from the UI in one click, and observations must persist across syncs to be discoverable at all. Both require the database. |
| **The sync response is not the discovery mechanism** | `POST /api/sync` is called by the phone; nobody reads its JSON. Response fields are for curl-debugging. The UI is where discovery happens. |

---

## 4. Changes — schema

`ddl-auto=update` creates all of this from the entities. No hand-run SQL, unlike dedup spec
§5.1 — that is the difference the self-host refactor made.

### 4.1 `workout_logs` — restore the unique index

```java
@Table(name = "workout_logs", uniqueConstraints = @UniqueConstraint(
        name = "ux_workout_logs_sync_signature", columnNames = "sync_signature"))
```

`@Table(uniqueConstraints = ...)`, not `@Column(unique = true)`, so the index carries the
same name Neon already uses and the two schemas are directly comparable.

Existing rows with `sync_signature = NULL` are unaffected: SQLite, like Postgres, treats
NULLs as distinct in a unique index, so manually created workouts never collide.

**Cutover interaction:** additive and convergent. Neon already has
`ux_workout_logs_sync_signature` from dedup spec §5.1, so this makes the two schemas agree
rather than diverge — the Neon→SQLite migration stays a straight copy. Because production
has carried the index since 2026-08-04, no duplicate signatures can exist to break the copy.

### 4.2 New table — `sync_sources`

| Column | Type | Notes |
|---|---|---|
| `data_origin` | varchar | PK, part 1 |
| `recording_method` | varchar | PK, part 2 |
| `allowed` | boolean | |
| `first_seen` | timestamp | |
| `last_seen` | timestamp | updated every sync the source appears in |

Composite PK on `(data_origin, recording_method)`. The pair is the key, not the origin alone:
the same vendor can emit both a stable and a drifting stream — Google's `unknown` mirrors are
dedup-safe while its `automatically_recorded` entries are not.

Entry counts are **not** stored here; they are derived from `quarantined_entries` so there is
one source of truth.

### 4.3 New table — `quarantined_entries`

Raw, as received, so replay can run the identical code path.

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | PK |
| `data_origin` | varchar | |
| `recording_method` | varchar | |
| `type` | varchar | raw, unparsed — mirrors `SyncRequest.ExerciseEntry.type` |
| `start_time` | varchar | raw ISO string, unparsed |
| `duration_seconds` | int | |
| `received_at` | timestamp | |
| `reason` | varchar | `SOURCE_NOT_ALLOWED` today |

Unique index on `(data_origin, recording_method, type, start_time)` so the webhook's
double-fire does not quarantine the same entry twice.

Note this cannot help a drifting source: its re-sends carry different timestamps by
definition, so each one is genuinely a new row. The quarantine count for such a source will
climb every sync — which is, usefully, exactly what a drifting source looks like from the UI.

**`reason` currently has one value.** It is kept as a column rather than being implied, so the
table is self-describing and a future rejection cause needs no schema change. An earlier draft
also defined `UNMAPPED_TYPE` for replayed entries with no exercise-type mapping; that state no
longer exists — see §5.3 and
[`exercise-type-vocabulary-spec.md`](exercise-type-vocabulary-spec.md) §9.

### 4.4 Sentinel for missing metadata

`metadata == null`, or a null `data_origin` / `recording_method`, normalizes to the literal
`(none)`.

**It must not be `unknown`** — that is a real observed value on Google's mirror records
(dedup spec §2). Using it as a sentinel would merge a real source with the malformed case.

Normalized entries flow through the ordinary path: recorded in `sync_sources`, rejected by
default, enablable if some future sender legitimately omits metadata. One code path, fully
diagnosable.

---

## 5. Changes — backend behaviour

### 5.1 `SyncService.sync()`

Delete `EXPECTED_ORIGIN` / `EXPECTED_METHOD` and the pre-filter stream. Per entry:

1. Normalize `(data_origin, recording_method)` per §4.4.
2. Upsert the `sync_sources` row: insert with `allowed = false` and `first_seen = now` if
   absent; update `last_seen` either way.
3. If the source is not allowed → insert into `quarantined_entries` with
   `reason = SOURCE_NOT_ALLOWED`, increment `rejected`, `continue`.
4. Otherwise proceed through the existing pipeline **unchanged** — type parse, instant
   parse, signature, `seen` check, mapping lookup, `workoutLogInserter.insert` with the
   `DataIntegrityViolationException` catch (dedup spec §5.5).

`sync()` must remain non-transactional — the comment saying so at
`SyncService.java:48-51` stays, and the reasoning in dedup spec §5.4 is unchanged by this
work. The two new writes (source upsert, quarantine insert) follow the same rule as the
workout insert: each in its own short transaction, so neither can poison the batch.

### 5.2 `SyncResponse`

Gains `rejected` (int) and `rejectedSources`: a list of `{origin, method, count}`.

Additive; nothing breaks. The frontend never calls `POST /api/sync` (verified — `syncService.js`
only touches `/sync/mappings`), and the demo API does not implement the endpoint.

One INFO line per sync summarizing rejections by source, so `docker logs` answers "why zero
workouts" without opening the UI.

Counting stays separated: `rejected` (source not allowed) is distinct from `skipped`
(duplicate, unmapped type, malformed). Three different causes, three different fixes.

### 5.3 Enabling a source — replay

Setting `allowed = true` replays that source's quarantined rows through the same per-entry
pipeline as §5.1 step 4. Replay is idempotent: the signature constraint absorbs anything
already inserted, so a partial failure can simply be retried.

Row disposition after replay:

- **Inserted, or rejected as a duplicate signature** → delete the quarantine row.
- **Malformed** (non-numeric type, unparseable `start_time`) → delete, with a WARN log. It
  can never be replayed usefully.

There is no third case. An entry whose exercise type has no mapping is not a failure: this work
ships together with [`exercise-type-vocabulary-spec.md`](exercise-type-vocabulary-spec.md), so
replay auto-creates the type and inserts the workout like any other. An earlier draft parked
such entries as `UNMAPPED_TYPE` pending that work; shipping the two together removes the need
for the bridge.

Disabling a source (`allowed = true → false`) stops future logging and does **not** delete
existing `workout_logs`. Symmetric and non-destructive.

### 5.4 API

Alongside the existing `/api/sync/mappings`:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/sync/sources` | list sources with quarantined counts and first/last seen |
| `PUT` | `/api/sync/sources/{origin}/{method}` | set `allowed`; enabling triggers §5.3 replay and returns what it inserted |
| `DELETE` | `/api/sync/sources/{origin}/{method}/quarantine` | dismiss quarantined entries for a source |

Origin strings are package names and appear in the path; they must be URL-encoded.

### 5.5 Seeding and migration

- **Fresh install:** `sync_sources` is empty. Nothing seeded. `AppSettingsSeeder` is
  untouched.
- **This install, at cutover:** the Neon→SQLite migration script inserts
  `('com.xiaomi.wearable', 'automatically_recorded', allowed = true)` so syncing continues
  uninterrupted. The single-user assumption lives in the migration, where it is true, rather
  than in the product defaults.

---

## 6. Changes — frontend

### 6.1 Settings

A fourth section in `SettingsView.vue`, after "Health Connect mappings": **Sync sources**.
Per row — origin, recording method, first seen, last seen, quarantined count, and
Enable / Disable / Dismiss actions.

Enabling shows one generic caveat, the same text for every source:

> Atlas deduplicates workouts by their exact start time. A source that revises timestamps
> between syncs will create duplicate entries.

No per-source drift prediction; see §3.

### 6.2 Dashboard notice

While quarantined entries exist, the dashboard shows a dismissible line: *"Atlas received N
workouts from a source that isn't enabled"*, linking to the settings section. It disappears
once the quarantine is empty.

This is the point of the whole to-do: the user it serves is staring at an empty dashboard and
has no reason to suspect a settings page.

### 6.3 Demo build

`demoApi.js` / `seed.js` serve one allowed source and one quarantined source with a few
entries, so a portfolio visitor can click Enable and watch the backfill land. Covered in
`demoApi.test.js` alongside the existing mappings coverage.

---

## 7. Verification

On the existing SQLite integration harness (`AbstractSqliteIntegrationTest`).

| # | Case | Expected |
|---|---|---|
| 1 | Fresh install schema | `ux_workout_logs_sync_signature` exists — regression guard for §1.3 |
| 2 | Same payload posted twice **sequentially** | 1 row (impossible today on a fresh install) |
| 3 | Entry from an unknown source | 0 logs, 1 quarantined, `rejected = 1`, source recorded `allowed = false` |
| 4 | Enable that source | quarantined entries become logs, quarantine emptied |
| 5 | Enable, then replay again | no additional rows — idempotent |
| 6 | `metadata` absent | source recorded as `(none)`/`(none)`, quarantined, not dropped |
| 7 | Two distinct workouts, same day, same type, same rounded duration | **2 rows** — dedup spec §8 case 3 still holds |
| 8 | Double-fire of the same rejected entry | 1 quarantine row, not 2 |

**Not covered: the concurrent double-fire** (dedup spec §8 case 2, the original bug).
`spring.datasource.hikari.maximum-pool-size=1` serializes SQLite writes, so the race cannot
be reproduced on this harness. Protection is structural — `REQUIRES_NEW` plus the database
constraint — and Testcontainers-Postgres was rejected as a CI dependency for a database being
decommissioned at cutover. State this in a comment on the test class so the gap is visible
rather than merely absent.

---

## 8. Sequencing

**Nothing here ships before the cutover.** The owner is still using the hosted app daily and
will not push generalization work until Atlas runs on his own hardware (stated 2026-08-12).
This spec ships as one bundle with
[`exercise-type-vocabulary-spec.md`](exercise-type-vocabulary-spec.md).

Implementation order within the bundle:

1. §4.1 unique index + case 1 test. Self-contained, and fixes a live defect on its own —
   without it, everything below admits records that nothing deduplicates.
2. §4.2–§4.4 schema and §5.1–§5.2 service behaviour.
3. §5.3 replay + §5.4 API. Replay depends on the vocabulary spec's auto-create.
4. §6 frontend and demo.
5. §5.5 migration line — applied as part of the cutover itself.

Note on §4.1 for the cutover: the index is schema-*convergent*. Neon has carried
`ux_workout_logs_sync_signature` since 2026-08-04, so adding it to the entity makes the two
schemas agree and the Neon→SQLite copy stays a straight copy. §4.2/§4.3 add new tables, which
the copy simply creates empty.

---

## 9. Out of scope

Surfaced while refining this to-do; tracked separately in
`atlas-generalization-todos.md` §7.

~~Dedup spec §10.2 is still live on disk: `application-local.properties` contains a plaintext
Neon password, a Gemini API key and the sync API key, and `SyncController.java:29` logs the
sync key in full at startup.~~

**Resolved 2026-08-16** by [`secrets-handling-spec.md`](secrets-handling-spec.md). The log line
is deleted and `application-local.properties` is gone, so no code change here depends on it any
more. What is left of that item is rotation, which is the owner's to do and blocks nothing in
this spec: the Neon password died with the database, and the provider key stopped being an
environment variable when to-do §1 moved it into `app_settings`.
