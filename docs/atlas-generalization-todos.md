# Atlas — Generalization To-Dos

To-dos to be refined and implemented **after** the self-hosting transition
(`atlas-selfhost-plan.md`, now complete — see the plan's §8 "Still pending"
for cutover status), before Atlas counts as finished. Today the app is
built specifically around one user's setup; each item below is something that
must be generalized or made self-serve for Atlas to work as a standalone
portfolio project.

---

## 1. LLM-agnostic insights with user-supplied API keys

**Status:** to be refined.

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

**Status:** to be refined.

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

**Status:** to be added — found while reviewing the dedup work.

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

**Status:** to be added.

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

## 6. The Health Connect webhook app is not in the repo

**Status:** to be added.

The sync pipeline's phone side — the Android app that fires the daily webhook —
lives outside this repository. The backend spec (`webhook-sync-deduplication-spec.md`)
documents its behaviour from delivery logs (including that it double-fires most
days, which the backend now dedups), but nobody else can replicate the pipeline
without the sender app, and its double-fire bug is undocumented as a bug.

**To refine:** open-source the webhook app into this (or a sibling) repository
with its own README; decide whether fixing its double-fire is in scope or it
stays a documented quirk that the backend is authoritative over.

---

## Out of scope (already decided elsewhere)

- Export/import of data — rejected in `atlas-selfhost-plan.md` §6.
- Demo build, seed generator, demo adapter — that is the self-host plan's work,
  not this list.
- Auth, multi-user, public internet exposure — rejected in the self-host plan §3.
