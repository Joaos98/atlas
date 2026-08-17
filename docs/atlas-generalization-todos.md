# Atlas — Generalization To-Dos

**All seven are complete as of 2026-08-16.** This document is now a record of what was done
and why, not a work list. Each item keeps its original finding, the refinement that followed,
and a note on what implementation discovered that the refinement had not — that last part is
the most useful thing here, because in every case it was found by running the app rather than
by reading the spec again.

What changed, in one line each:

| | | |
|---|---|---|
| §1 | Insight provider | Any OpenAI-compatible endpoint, configured in the UI, key never served over HTTP |
| §2 | Exercise vocabulary | Unmapped Health Connect codes create their own type instead of being dropped |
| §3 | Sync sources | Any device is accepted; unknown ones are held and recoverable, not silently filtered |
| §4 | First-run bootstrap | `app_settings` seeds itself, and now backfills columns added later |
| §5 | Units | Metric/imperial at the display boundary, canonical metric in the database |
| §6 | Webhook sender | Documented and third-party; `POST /api/sync` published as a tested contract |
| §7 | Secrets | Leak paths closed; rotation considered and declined as an accepted risk |

Four defects were found along the way that pre-dated all of this: a missing unique index that
had silently disabled cross-sync dedup, a duplicate-catch that never matched what SQLite throws,
a settings seeder that skipped every install holding real data, and a demo that broke its own
dashboard as soon as a workout was logged.

The original framing, kept because it is what the items were written against:

To-dos to be refined and implemented **after** the self-hosting transition
(`atlas-selfhost-plan.md`, now complete — see the plan's §8 "Still pending"
for cutover status), before Atlas counts as finished. Today the app is
built specifically around one user's setup; each item below is something that
must be generalized or made self-serve for Atlas to work as a standalone
portfolio project.

---

## 1. LLM-agnostic insights with user-supplied API keys

**Status: IMPLEMENTED 2026-08-16 — see [`insight-provider-spec.md`](insight-provider-spec.md),
whose §13 records what the refinement got wrong.**

Insights now run against any OpenAI-compatible endpoint, configured in the UI under
**Settings → Insights** and stored in `app_settings`. `GEMINI_API_KEY` is gone from
`compose.yaml`, `application.properties` and the README, so **`SYNC_API_KEY` is the only
environment secret** — the condition §7 was waiting on. The key is write-only over HTTP:
`GET /api/settings` returns a configured flag and last-4, asserted against the raw response
body so a future entity field cannot reintroduce the leak.

Three things worth carrying forward:

- **Seeding a new column is not automatic.** `ddl-auto=update` adds columns as NULL to rows
  that already exist, and the seeder used to skip any install that had a settings row — so the
  defaults would have reached only fresh databases. `ensureSeeded()` now backfills. §5 adds
  `unit_system` to the same table and gets this for free.
- **§8's defect had two more sites than the spec found**, including one where merely *creating
  a measurement* stored an error string as its insight. Found by running the app; the test
  suite was green throughout. The store-only-on-success rule is now in one function.
- **Failure states are a field, not a message.** The frontend used to detect them by
  string-matching the error prose, which reworded messages would have broken silently.

- **The success path was the only one with no coverage.** Every test written for the failure
  states exercised a failure; nothing had ever sent a request and read a reply. A stub HTTP
  server now asserts both directions of the wire format.

**All ten verifications pass.** The tenth — a real generation against a live provider — was run
by hand through the Settings UI, since the sandbox blocks an agent from submitting an API key.
It also confirmed `gemini-3.5-flash` is valid on the OpenAI-compatible endpoint, so the seeded
default is correct as shipped.

The refinement rejected this item's central assumption. There is **no provider interface**:
one OpenAI-compatible client with three DB-backed settings (`baseUrl`, `apiKey`, `model`)
covers OpenAI, Gemini, Groq, OpenRouter, Ollama and LM Studio, so `baseUrl` is the provider
selector and switching providers is a settings change, not a code change. The key lives in
`app_settings`, is write-only over HTTP (GET returns a configured flag and last-4), and
`GEMINI_API_KEY` disappears — leaving `SYNC_API_KEY` as the only environment secret, which
is what §7's spec was waiting on.

Two findings the to-do did not anticipate:

- **`AppSettingsController` serializes the entity directly on GET.** Adding a key column
  would leak it over an unauthenticated LAN endpoint with no further mistake required. A DTO
  is a prerequisite, not a nicety.
- **Failed generations overwrite good insights.** `regenerate()` persists error text into
  `body_metrics.insight_text` and re-serves it as an insight. Rare today; routine once a
  mistyped key returns 401 on first run. Fixed in the same pass.

The prompt-copy bullet closed with no action — the copy is generic, and the only real
coupling is units, deferred to §5.

The original finding, kept for history:

Insight generation is wired to Gemini only: `InsightService` builds a
Gemini-shaped request (`callGemini` / `doGeminiCall`, [InsightService.java](aio-fitness/src/main/java/com/joaosousa/atlas/service/InsightService.java#L262)),
and the model is a property with a Gemini default
(`app.insight.model`). A self-hosted user must be able to:

- bring their own API key for the provider of their choice, and
- store that key without putting it in an environment variable at deploy time
  (the self-hosted build has no auth and is not internet-facing, so a key stored
  in the database or a local config file is acceptable here — decide when refining).

**To refine:**

- Define the provider interface (one method: `generate(prompt, model) → text`)
  with a Gemini implementation first, then OpenAI-compatible (Anthropic/SAMM/etc.)
- Where the key lives: DB-backed setting vs. file/env — pick one.
- Prompt copy is presumably written in one voice and may reference the original
  user's framing ("your training", personal-trainer tone). Sweep it for
  user-specific assumptions while generalizing.
- Whether the key ever reaches the frontend, or stays server-side only
  (server-side only; the frontend currently just displays the result).

## 2. Health Connect exercise types by default — no manual type conversion

**Status: DONE 2026-08-16 — see [`exercise-type-vocabulary-spec.md`](exercise-type-vocabulary-spec.md),
whose §9 records what the refinement did not anticipate. Shipped as one bundle with §3.**

An unmapped Health Connect code now creates its own type from a 61-entry catalog and logs the
workout; "unmapped" has stopped being a reason to skip anything. An explicit mapping always
wins, so hand-made labels survive untouched, and a mapping to *nothing* means "never log this",
which replaces the capability that not-mapping used to provide. Near-duplicates are repaired by
merge, and auto-created types announce themselves on the dashboard rather than appearing
silently.

**The owner chose fine-grained types over preserving the `Cardio` grouping.** Splitting the
existing history turned out to be impossible: the HC code survives inside `sync_signature`, but
only 6 of 442 rows have one and none of them are cardio. `Cardio` keeps its 436 rows as a
historical bucket with no mapping; fine-grained types apply from the upgrade forward. `Gym` was
renamed to the catalog's `Strength training`; `0 → Crossfit` stays, because the watch chooses
the code and Health Connect has no CrossFit constant.

The refinement rejected seeding the HC vocabulary as the canonical `WorkoutType` set (an
~70-item dropdown, plus the reconciliation risk this to-do worried about) in favour of
**auto-creating** a type the first time an unmapped HC code arrives, named from a static
catalog. An explicit mapping row always wins, so existing installs need **no migration** —
the orphaning risk below does not arise. A null mapping means "ignore this activity",
replacing the capability that not-mapping provides today, and types can now be merged.

The real risk it surfaced is different from the one recorded here: this install has
*grouping* types (`Cardio`), and auto-create fragments them one activity at a time. Hence
the new-type notice and the §8 upgrade checklist in that spec.

The original finding, kept for history:

Today the user creates `WorkoutType` rows by hand, and Health Connect's numeric
exercise types are mapped to them via `exercise_type_mapping`; anything unmapped
is silently skipped ("Unmapped Health Connect exercise type..."). This is
entirely backwards for a new user: they must reverse-engineer their own mapping
table before the sync does anything useful.

**To refine:**

- Default to Health Connect's `ExerciseSessionRecord` exercise type vocabulary
  (`EXERCISE_TYPE_*` constants) as the canonical `WorkoutType` set, seeded at
  first run, so sync works out of the box.
- Keep the mapping table for re-labelling (e.g. type → "Gym"), but make it an
  override with a sane default rather than a hard prerequisite.
- Existing installs: migrate current `workout_types` rows into the new model
  without orphaning `workout_logs.workout_type_id`.
- The seed generator (self-host plan §4.5) must then generate against the
  seeded vocabulary, not a hand-made one.

## 3. Configurable device origin / recording-method filter

**Status: DONE 2026-08-16 — see [`sync-source-allowlist-spec.md`](sync-source-allowlist-spec.md),
whose §10 records what the refinement did not anticipate. Shipped as one bundle with §2.**

The hardcoded `com.xiaomi.wearable` filter is gone. Sources are discovered from real payloads
and recorded before they are judged, so an unrecognised device is visible in Settings instead of
producing a silent `{created: 0}`. Entries from a source that is not enabled are **quarantined,
not dropped** — the sender transmits a delta, so anything refused would otherwise be lost for
good — and enabling a source replays them through the identical code path a live sync takes.

**Three defects were hiding behind each other, all pre-dating this work:**

- **The unique index on `sync_signature` did not exist**, so cross-sync dedup was dead. The
  spec's fix — a JPA `uniqueConstraints` annotation — does not work on SQLite either: Hibernate
  emits `ALTER TABLE ADD CONSTRAINT`, SQLite rejects it, and `ddl-auto=update` swallows the
  failure. It now runs as explicit `CREATE UNIQUE INDEX`.
- **The duplicate catch had never matched.** SQLite surfaces the violation as
  `JpaSystemException`, not `DataIntegrityViolationException`, so with the index working a
  duplicate would have failed the whole sync rather than being skipped.
- **This install had three copies of one workout** from cutover night, cleaned up by hand.

Catching a constraint violation inside its own transaction turns out not to work at all — the
context is already rollback-only — which is precisely why `WorkoutLogInserter` had always been a
separate bean. Worth remembering before "simplifying" it.

The refinement settled it as a *dedup-safety gate*, not a device preference: a DB-backed
allow-list keyed on `(data_origin, recording_method)`, empty on fresh installs, with
rejected entries **quarantined** rather than dropped and replayed when the user enables the
source. Quarantine is not optional — the sender transmits a delta, so a rejected entry is
never re-sent and plain rejection would lose data permanently.

It also surfaced a prerequisite defect: **`workout_logs.sync_signature` has no unique index
on a fresh install**, so cross-sync dedup is silently dead there. The dedup spec's §5.2
"do not add `unique = true`" was correct under `ddl-auto=validate` and expired when
self-hosting moved the app to `ddl-auto=update`. That fix goes first, and is
cutover-convergent — Neon already has the index.

The original finding, kept for history:

`SyncService` hardcodes `EXPECTED_ORIGIN = "com.xiaomi.wearable"` and
`EXPECTED_METHOD = "automatically_recorded"` — every other device origin is
filtered out before dedup even runs. A user with a Samsung Watch, a Garmin or
Google Fit integration gets zero workouts and no explanation.

The filter exists for a real reason (see `webhook-sync-deduplication-spec.md`
§2): Google-origin activity-detection records drift between syncs, so an
instant-based signature cannot dedup them, and Xiaomi records are stable. The
default can stay Xiaomi-safe, but it must become:

- configurable (property/setting listing accepted origins and methods), and
- diagnosable — the "everything filtered out" case should say so in the sync
  response or logs, not produce a silent 200 with zero created.

**To refine:** default behavior for unknown origins — accept and rely on the
signature constraint, or reject like today; and whether the origin/method
allow-list belongs in `app_settings` or properties.

## 4. First-run bootstrap — `app_settings` is never seeded

**Status: DONE — implemented during self-host Phase 1.**

`AppSettingsSeeder` (backend) now inserts the single `app_settings` row
(id=1, `target_workouts_per_week` = 3) idempotently at startup, so
`calculateStreaks()` and settings updates work on a fresh install. A
`FreshInstallTest` covers the path. The original finding, kept for history:

No code path ever inserts the single `app_settings` row: `AppSettingsService`
and `WorkoutLogService.calculateStreaks()` both do
`appSettingsRepository.findById(1L).orElseThrow()`, and no `data.sql`,
`CommandLineRunner` or service creates it. The row exists in production only
because it was inserted manually. A fresh self-hosted install with `ddl-auto=update`
will create the empty table, and `/workout-logs/streaks` (and settings updates)
will 500.

**To refine:** sweep the rest of the codebase for other manually-created
one-off rows (`findById(1L)`-style assumptions) that a fresh install would
miss. (Check `users` — the auth removal deleted it; confirmed nothing else
depends on a pre-existing row.)

## 5. Units are hardcoded to metric

**Status: DONE 2026-08-16 — see [`units-preference-spec.md`](units-preference-spec.md),
whose §10 records what the refinement did not anticipate.**

A Metric/Imperial toggle under **Settings → Units**, applied at display time only: the database
stays canonical metric, so switching back and forth never touches a row. All seven of the spec's
verifications pass, checked in a running app — including the round-trip, which is the one that
would corrupt data silently.

Three things worth carrying forward:

- **The spec assumed a settings store that did not exist.** Four components each fetched
  `/api/settings` independently, which made the toggle un-reactive by construction. One Pinia
  store now owns settings; a dead `counter.js` scaffold went with it.
- **The insight prompt's numbers were locale-dependent** — `String.format` with the JVM default
  rendered `82,3` on a pt-BR machine, so prompt text varied by where the server ran. Pinned to
  `Locale.ROOT`. Unrelated to units; found by asserting on prompt text for the first time.
- **`BODY_FAT_KG` stays as-is**, treated as an opaque identifier with a comment on the enum
  saying so. Renaming it needs a hand-written data fixup the app has no tooling for.

Settled as: **canonical metric in the database, converted at the display boundary**, driven
by one `unit_system` column on `app_settings`. The API keeps its current shape — no DTO
gains a unit field — so there are exactly two conversion sites: a `useUnits()` composable in
the frontend, and `InsightService.buildPrompt(...)`, which writes units into text a human
reads and would otherwise contradict the UI.

Three things the refinement found:

- **The scope is smaller than it looks.** Workouts have no distance field — only
  `durationMinutes`, which is unit-neutral. This is body composition only.
- **Body composition is hand-entered, not synced.** `SyncService` never touches
  `BodyMetrics`; `SyncRequest` carries only `exercise` entries. So there is no ingest
  conversion boundary and no upstream unit to inherit — the values are whatever someone
  typed into the form.
- **The unit is in the vocabulary.** `MetricType.BODY_FAT_KG` is persisted as a string in
  `goals.metric_type`. Renaming it needs a hand-written data fixup because the app has no
  migration tooling, so the spec keeps the name and treats it as an opaque identifier.
- **Body water is a volume, not a mass.** It converts L → lb, not to any imperial volume, so
  the conversion table is per-metric rather than one global factor.

The trap the spec spends most effort on: display rounds to 1 dp, so an unedited save in
imperial mode silently walks the stored value (82.3 kg → 181.4 lb → 82.28 kg). Forms must
submit the original canonical value when the displayed value is unchanged.

The original finding, kept for history:

Every unit string in the frontend is metric and hardcoded — `kg` across
BodyMetricsView, DashboardView, GoalsView, GoalForm and LatestMeasurementStats
(e.g. `unit: ' kg'`, `WEIGHT: ' kg'`), and the backend stores weight/muscle/bone
values in `kg` fields (`weight_kg`, `muscle_mass_kg`, ...) with no unit concept.
The unit is also baked into goal `target_value` semantics.

For a portfolio app seen by users in any country, body composition needs at
least: a units preference (metric/imperial) applied at display time, and goal
targets either entered in the chosen unit or labelled unambiguously. Storage
decision: keep canonical metric in the DB and convert at the boundary (cleaner),
or store the user's unit (simpler). Decide when refining.

**To refine:** where conversion lives (frontend vs. backend), and whether the
AI insight prompt needs units spelled out either way.

**Consumer to account for:** `InsightService` builds the AI prompt with metric labels baked
in (`- Weight: 82.3 kg`, `- Body water: 41.2 L`, goal targets via `metricUnit(...)`). That is
correct today because the units are stated explicitly, and
[`insight-provider-spec.md`](insight-provider-spec.md) §7 deliberately left it alone rather
than guess this item's storage decision. Once a display preference exists, the prompt must
follow it — otherwise the card shows pounds while the insight text says kilos.

## 6. The Health Connect webhook app is not in the repo

**Status: DONE 2026-08-16 — see [`webhook-sender-spec.md`](webhook-sender-spec.md), whose §9
records what implementation added. This was the last of the seven.**

The README now has a "Syncing workouts from your phone" section: what HC Webhook is and that it
is third-party, how to point it at Atlas, the two-step first run, and `POST /api/sync` published
as a contract anyone can satisfy — so the dependency is bounded rather than absolute.

**The contract is tested.** `SyncContractTest` pins every claim the README makes in writing, on
the principle that an interface strangers are invited to build against is code with a
documentation front end, not prose. It caught nothing broken, but it did verify one claim
written on assumption: `type` really is accepted as a JSON number as well as a string.

Every example payload is invented. Real deliveries encode the owner's device, workout times and
daily schedule — collectively, when the house is empty.

**This item's premise was wrong.** The sender is not the author's app to open-source: it is
**HC Webhook** (<https://hcwebhook.com/>), a third-party product on the Play Store and App
Store — and it is **already open source**, at
<https://github.com/mcnaveen/health-connect-webhook> under AGPL-3.0 with a commercial-use
addendum. The to-do's proposal is already done, by someone else. Link it; forking it into this
repo would inherit AGPL obligations and go stale. Its double-fire is likewise not Atlas's to
fix, so the backend is authoritative permanently.

What replaces it: document the sender in the README **and** treat `POST /api/sync` as a
published interface, so HC Webhook is an example that satisfies the contract rather than a
hard dependency. The contract is descriptive — it states what the endpoint accepts and
guarantees, headlined by idempotency ("re-send freely, repeat deliveries are safe"). A strict
schema was rejected because the only known working sender emits two timestamp formats inside
one payload and would fail its own validation.

Upstream publishes a field-level payload reference (`docs/webhook.md`), so Atlas links it
rather than restating it and documents only what upstream cannot know — plus **four
divergences between those docs and real traffic**, including that the docs omit the
`data_origin` / `recording_method` metadata the entire allow-list design depends on.

Two findings that reach other specs:

- **The 48-hour rolling window with a per-type watermark** is documented upstream, which
  confirms the dedup spec's delta inference and explains the carried-over workout as window
  overlap. Upstream's 3-attempt exponential backoff plus this install's 144–173 s cold starts
  also gives the double-fire a likelier cause than a defective app: a client timeout retrying
  a request that succeeds anyway.
- **`type` is numeric — verified 2026-08-13** against a real delivery from sender version
  `1.9.14` (`"type":"79"`, `"type":"0"`). Upstream's "string form" wording was ambiguous and
  the breaking reading would have silently skipped every entry; it does not apply. §2 is
  unblocked. That delivery also confirmed the two-timestamp-format variance and the presence
  of the `metadata` fields upstream's field table omits.

Scope note: the README carries what makes the pipeline reproducible; the full treatment goes
on the planned Saturn documentation site. **§6 closes on the README section** — Atlas is not
gated on a cross-project site. The spec's §5 records what that page must cover so the
material is not reverse-engineered twice.

The original finding, kept for history:

The sync pipeline's phone side — the Android app that fires the daily webhook —
lives outside this repository. The backend spec (`webhook-sync-deduplication-spec.md`)
documents its behaviour from delivery logs (including that it double-fires most
days, which the backend now dedups), but nobody else can replicate the pipeline
without the sender app, and its double-fire bug is undocumented as a bug.

**To refine:** open-source the webhook app into this (or a sibling) repository
with its own README; decide whether fixing its double-fire is in scope or it
stays a documented quirk that the backend is authoritative over.

## 7. Secrets on disk, and a key logged at startup

**Status: DONE 2026-08-16 — see [`secrets-handling-spec.md`](secrets-handling-spec.md).**

What shipped: the startup log line is gone (with its `Logger` import), the key comparison is
now `MessageDigest.isEqual`, and `application-local.properties` is deleted. By deletion time
the file held nothing live but the two keys — its Neon datasource pointed at a database
deleted the day before, and its CORS property had been dead since the self-host work removed
CORS. A permanent regression test (`SecretsNotBundledTest`) fails if the file ever returns to
the build output, which is what the spec's `pom.xml`/`.dockerignore` exclusions were for; those
were skipped as dead config once the file itself was gone.

`server/.gitignore` was trimmed to the two entries the root `.gitignore` does not already
match at any depth. A future `application-local.properties` is deliberately no longer ignored,
so it would surface in `git status` rather than sit invisible.

All four of the spec's automated verifications pass, including the one that mattered most for
justifying the deletion: with `SYNC_API_KEY` unset the app fails at startup naming the missing
property, so the deleted log line's diagnostic value really was zero.

**Rotation was considered and declined** — see the spec's §3.4, where the residual exposure is
written down as an accepted risk. The leak paths are shut either way; what is declined is
re-issuing the two keys that passed through them.

The refinement found two of this item's three premises no longer hold, and the leak path
that does matter was not among them. `application-local.properties` is **not** in the
repository — it is gitignored at `server/.gitignore:6` and `git log --all -S` for each of
the three secrets comes back empty on every branch, so there is no history to rewrite.
`ddl-auto` is back to `validate`, resolving the dedup spec's §10.1 BLOCKING condition.

What is real: the file is bundled into **build artifacts** — `mvn package` copies it into
`BOOT-INF/classes/` (verified in the current jar) and the Dockerfile's `COPY server/src`
carries it into every `atlas:local` image, because `.dockerignore` does not exclude it.
Gitignore never protected the artifact. And `SyncController.java:30` still logs the sync
key in full at INFO, which on the live Render deployment writes the only credential
guarding `POST /api/sync` into retained logs on every restart — the one exposure that is
active rather than local.

A third path turned up on a full-tree sweep: the sync key was quoted verbatim in **this
file** (§7's history block) and in the dedup spec's §10.2 — the first of which is tracked,
so the next commit would have written the key into history permanently. Both were redacted
2026-08-12, before any commit, so no history rewrite is needed.

The spec orders the work as: scrub the docs (done), delete the log line, stop shipping the
file, *then* rotate
(rotating first would mint new secrets into the same holes), and delete the file at
cutover. Rotation of the sync key is coupled to the phone app (§6) and to the sender's
delta behaviour — a fumbled rotation loses a day of workouts, not just a request.

The original finding, kept for history:

`server/src/main/resources/application-local.properties` contains, in plaintext: the Neon
database password, a Gemini API key, and the sync API key (redacted 2026-08-12; it was
quoted in full here) — which
is the only thing protecting `POST /api/sync`. Separately, `SyncController.java:29` logs
that key in full, at INFO, on every startup.

That file also still points `spring.jpa.hibernate.ddl-auto=update` at the production Neon
database, the condition §10.1 of the dedup spec called BLOCKING; it was resolved at the time
by repointing at a dev branch, and has since drifted back.

**To refine:** rotate all three secrets, move them to environment variables the way
`application.properties` already does, remove the startup log line, and decide whether
`application-local.properties` should exist in the repository at all now that the
self-hosted profile takes its configuration from the environment.

---

## Out of scope (already decided elsewhere)

- Export/import of data — rejected in `atlas-selfhost-plan.md` §6.
- Demo build, seed generator, demo adapter — that is the self-host plan's work,
  not this list.
- Auth, multi-user, public internet exposure — rejected in the self-host plan §3.
