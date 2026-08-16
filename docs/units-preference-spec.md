# Spec: Unit System Preference — Canonical Metric, Converted at Display

**Status:** Implemented and verified 2026-08-16. All seven verifications in §7 pass, checked in
a running app rather than only in tests — including §7.4's round-trip, which is the one that
fails silently. See §10 for what implementation found that this spec did not.
**Date:** 2026-08-13
**Implements:** `atlas-generalization-todos.md` §5
**Consumer:** [`insight-provider-spec.md`](insight-provider-spec.md) §7 — the AI prompt must follow the preference
**Component:** `server` — `AppSettings`, `InsightService`; `ui` — five views/components, `MetricChart`, demo build

---

## 1. Problem

Every unit in the app is metric and fixed at compile time. `body_metrics` stores
`weight_kg`, `muscle_mass_kg`, `water_liters`, `body_fat_kg`; the frontend hardcodes the
labels in at least five places (`BodyMetricsView` metric config and table cells,
`LatestMeasurementStats` deltas, `DashboardView` body-composition tiles, `GoalsView`'s
`UNITS` map, `GoalForm`'s dropdown); and goal `target_value` carries no unit at all — its
meaning is implied by the metric type.

For a portfolio project seen by people in the US, body composition is unreadable.

### 1.1 The scope is smaller than it looks

Workouts have **no distance field** — `WorkoutLog` is `workoutType`, `logDate`,
`durationMinutes`, `syncSignature`, and `SyncRequest` carries `duration_seconds`. Time is
unit-neutral. So this item is **body composition only**: five metrics on one entity, plus
goals over those same metrics. No pace, no distance, no elevation.

Qualifier added 2026-08-13: the *sender* can emit `distance_meters`, `steps`,
`avg_cadence_spm` and `stride_length_m` (see [`webhook-sender-spec.md`](webhook-sender-spec.md)
§1.4). Atlas models none of them and drops them silently, so the scope claim holds today — but
it is a property of Atlas's schema, not of the data available. If distance is ever modelled,
it arrives with a unit question attached.

### 1.2 Body composition is hand-entered, not synced

Worth stating because it is easy to assume otherwise in an app built around a Health Connect
webhook: `SyncService` never touches `BodyMetrics`, and `SyncRequest` carries only `exercise`
entries. Every value in `body_metrics` arrives through `POST` / `PUT /api/body-metrics` —
the form. The only other writer is `InsightController`, storing insight text back onto the
row.

Consequences: there is no ingest conversion boundary, no upstream unit to inherit, and the
user is *already* free to type pounds into a field labelled kg — which is what makes the
"relabel only" option in §2.3 coherent enough to need rejecting on the record.

(For completeness, had body data ever flowed: Health Connect records store typed quantities —
a `Mass` with `inKilograms` / `inPounds` accessors — not a unit. The phone's display
preference is a display concern, and a sender app chooses an accessor. "It arrives metric"
would be an assumption about the sender, not a property of Health Connect.)

### 1.3 The unit is in the vocabulary, not just the labels

The harder half. `MetricType` is `WEIGHT, MUSCLE_MASS, WATER, BODY_FAT_KG, BODY_FAT_PCT` —
and `BODY_FAT_KG` is persisted as a **string** in `goals.metric_type` (`@Enumerated(EnumType.STRING)`),
surfaced in the API, matched in the frontend's `FIELD_BY_METRIC` / `UNITS` maps, and
switched on in `InsightService.metricUnit(...)` and `goalLabel(...)`. Under an imperial
preference the identifier itself becomes false.

### 1.4 Body water is not a mass

`water_liters` is a volume; the other three are masses. There is no sensible imperial volume
for it — nobody wants to read "10.9 gal of body water". Smart scales in imperial markets
report body water as **pounds** (1 L of water ≈ 1 kg ≈ 2.205 lb) or as a percentage. So
`WATER` does not convert L → some-imperial-volume; it converts L → lb, which means the
conversion table is not one-metric-per-row. Called out because it is the detail that turns
"multiply by 2.205" into a per-metric mapping.

---

## 2. Solution

**Store canonical metric. Convert at the display boundary. One preference in
`app_settings`.**

`unit_system` column, `METRIC` | `IMPERIAL`, default `METRIC`, seeded by the existing
`AppSettingsService.ensureSeeded()` path.

### 2.1 Why canonical metric rather than storing the user's unit

- Every existing row is metric, so canonical-metric is a no-op migration; storing the user's
  unit means either rewriting `body_metrics` and `goals` on every preference change — which
  leaves mixed units behind if it fails halfway — or tagging each row with the unit it was
  entered in and branching on it at every read.
- Comparisons, deltas, and rate-per-month arithmetic stay in one unit.
  `InsightService.appendTrend(...)` computes rates across the *entire* series, and
  `GoalService` compares a target against a current value; both would need per-row
  normalisation under mixed storage, which is the same conversion work plus a branch.

**Not a reason, despite appearances:** ingest. Body composition does **not** arrive from
Health Connect — see §1.4. It is typed in by hand, so there is no ingest boundary to convert
at and no upstream unit to inherit.

The cost, stated honestly: canonical storage is what *creates* the round-trip drift problem
in §5. Store-as-entered would make that class of bug impossible. It is the strongest argument
against this choice, and it loses only because the drift has a cheap, testable fix (compare
the displayed string) while mixed-unit history does not have a cheap fix for the arithmetic.

### 2.2 Why not rename `BODY_FAT_KG`

Tempting for correctness — `BODY_FAT_MASS` is the honest name. Rejected on cost: the value
is persisted as a string in `goals.metric_type`, and the app has **no migration tooling**
(`ddl-auto=update` plus an `ApplicationRunner` seeder — no Flyway, no Liquibase). Renaming
means hand-writing a one-shot data fixup and keeping it forever for a cosmetic gain.

Instead: treat `MetricType` values as **opaque identifiers**, not descriptions. `BODY_FAT_KG`
means "body fat as a mass"; the unit token in the name is legacy. Every user-visible label
already comes from a lookup (`goalLabel`, `UNITS`, the `GoalForm` option text), so the
identifier never reaches the screen. Add a comment on the enum saying exactly this — the
next reader will otherwise assume it is a bug.

### 2.3 The option that converts nothing

Because values are hand-entered (§1.2), a third design exists that this spec initially
missed: **make the preference a label, not a conversion.** The user types whatever their
scale shows, and `unit_system` only decides whether the app prints "kg" or "lb" beside it.
No conversion code, no round-trip drift, no §5 at all — perhaps 30 lines total.

For a single user who never switches systems, it is genuinely sufficient, and it is worth
being honest that most of this spec's complexity buys correctness that one user may never
observe.

Rejected for three reasons:

- **History becomes meaningless on a switch.** Toggle the label and every past measurement
  silently changes meaning. `appendTrend` would report a 100 lb/month gain when nothing
  happened. Nothing in the data marks where the switch occurred, so it is unrecoverable.
- **The numbers stop being data.** `82.3` means whatever the person typing believed. Goals,
  deltas, and the insight prompt all inherit that ambiguity, and a value with no unit is not
  a measurement.
- **The item's purpose is generalisation.** To-do §5 exists to make Atlas work for a user who
  is not the author. "Type whatever you like and we will label it" is the same
  reverse-engineer-the-author's-setup posture that §2 and §3 were written to remove.

Noted rather than omitted, because it is the cheapest thing that could work and a reader
will otherwise wonder why it was not chosen.

---

## 3. Conversion table

| MetricType | Stored | Metric display | Imperial display | Factor |
|---|---|---|---|---|
| `WEIGHT` | kg | kg | lb | × 2.20462 |
| `MUSCLE_MASS` | kg | kg | lb | × 2.20462 |
| `BODY_FAT_KG` | kg | kg | lb | × 2.20462 |
| `WATER` | L | L | lb | × 2.20462 (see §1.4) |
| `BODY_FAT_PCT` | % | % | % | none |

One factor, four metrics, one passthrough. `WATER`'s row is the one that needs the comment.

---

## 4. Where conversion lives — two sites, deliberately

**The API stays canonical metric.** No endpoint changes shape, no DTO gains a unit field.
The preference is exposed through `GET /api/settings` (already served, now with
`unitSystem`), and consumers convert.

That gives exactly two conversion sites:

1. **Frontend, at render.** A `useUnits()` composable exposing `format(value, metricType)`
   and `label(metricType)`, reading `unitSystem` from the settings store. Every hardcoded
   `' kg'` / `' L'` / `${...} L` becomes a call. Sites: `BodyMetricsView` (metric config,
   table cells, `MetricChart` `unit` props, goal target lines), `LatestMeasurementStats`
   (values and `deltaText`), `DashboardView` (body-composition tiles), `GoalsView` (`UNITS`
   map), `GoalForm` (option labels).

2. **Backend, in the insight prompt.** `InsightService.buildPrompt(...)` writes units into
   text a human reads — `- Weight: 82.3 kg`, and goal targets via `metricUnit(...)`. If it
   stays metric while the UI shows pounds, the card contradicts itself. `InsightService`
   reads `unitSystem` from `AppSettingsService` (which it already injects, for
   `targetWorkoutsPerWeek`) and converts there.

Anything else stays metric, including `BodyCompositionStatsDto`'s `weightChangeKg` /
`muscleMassChangeKg` field names — they describe the canonical unit and remain accurate.

### 4.1 Input conversion

`BodyMetricsForm` and `GoalForm` collect values in the **displayed** unit and must convert
to canonical before POST/PUT. This is the mirror of §4 and the easiest half to forget, since
nothing visibly breaks until someone in imperial mode saves 181 and the chart moves to 400.

---

## 5. Round-trip precision — the trap

Display rounds to 1 dp. `82.3 kg` → `181.4 lb`; converting `181.4` back gives `82.28 kg`.
The edit forms in `BodyMetricsView` (inline row editing) and `BodyMetricsForm` prefill from
current values, so **opening a row and saving it unchanged silently mutates the data**, and
repeating it walks the value.

Rule: **if the rounded displayed value is unchanged, submit the original canonical value
untouched.** Convert on input only when the field was actually edited. Concretely, keep the
canonical value alongside the form field and compare the displayed string, not the number.

This is worth a test (§7.4) because it fails silently and only in imperial mode.

---

## 6. Frontend surface

### 6.1 Settings

A unit-system toggle (Metric / Imperial) in `SettingsView`, alongside the insight settings
from [`insight-provider-spec.md`](insight-provider-spec.md) §9.1. Changing it re-renders;
it never writes to `body_metrics` or `goals`.

### 6.2 Charts

`MetricChart` takes a `unit` prop and plots raw values. Pass the converted series and the
resolved label; the goal-target line must be converted with it, or the target renders at the
wrong height. Same for `activeGoalTarget(...)`.

### 6.3 Demo build

`demo/derived.js`, `demoApi.js` and `seed.js` carry `waterLiters` / `weightKg` through
unchanged, so the demo inherits the composable for free — provided the demo's settings
object includes `unitSystem`. `demo-seed.json` stays metric; it is canonical data like any
other.

---

## 7. Verification

1. Metric mode renders exactly as today — same numbers, same labels. This is the regression
   that matters; the default must be indistinguishable from the current app.
2. Imperial mode: `82.3 kg` → `181.4 lb` on dashboard tiles, metrics table, sparkline
   subtitles and chart axis, with no stray `kg` anywhere. Grep the built output for `kg` as
   a backstop.
3. `WATER` renders as `lb` in imperial, `L` in metric — the row most likely to be missed.
4. **Round-trip:** in imperial, open a measurement, save without editing, assert the stored
   canonical value is byte-identical (§5).
5. Goal entered as `180 lb` stores `81.65 kg` and redisplays as `180.0 lb`; the same goal
   viewed in metric reads `81.7 kg`.
6. Insight prompt in imperial contains `lb` and no `kg` — assert on the built prompt string,
   not the model output.
7. Switching the preference changes no row in `body_metrics` or `goals`.

---

## 8. Sequencing

Behind the cutover freeze with everything else.

Order against the other specs: this one **depends on nothing**, but
[`insight-provider-spec.md`](insight-provider-spec.md) §7 is its consumer, and both add
columns to `app_settings` and fields to the same settings DTO and `SettingsView`. Land the
insight spec first — it introduces `AppSettingsDto` and the write-only key handling — then
add `unitSystem` to the structures it created. Doing it the other way means building the DTO
twice.

---

## 10. What implementation found — added 2026-08-16

**There was no settings store.** §4 says `useUnits()` reads `unitSystem` "from the settings
store"; `stores/` held `toast.js` and a dead Vite scaffold `counter.js`. Four components each
fetched `/api/settings` for themselves — `DashboardView`, `WorkoutsView`, `SettingsView`,
`WeeklyWorkoutsChart` — so one page issued the request up to three times and nothing shared
state. That is fatal to §6.1's "changing it re-renders": a toggle in Settings would have left
every other view stale until reload. A Pinia settings store now owns it, the four call sites are
folded in, and `targetPerWeek` became a computed in three of them instead of a ref that never
updated. `counter.js` deleted.

**The prompt's numbers were locale-dependent.** Not a units bug, but §7.6 surfaced it: every
`String.format("%.1f", …)` in `buildPrompt` used the JVM default locale, so on this pt-BR machine
the prompt read `- Weight: 82,3 kg`. The same app produced different prompt text depending on
where it ran, and a comma decimal is ambiguous to a model that may read it as a thousands
separator. All eight call sites now pin `Locale.ROOT`.

**`format(null)` rendered `0.0`.** Caught by the composable's own test: `Number(null)` is `0`,
which is finite, so a null slipped past the guard. A goal with no baseline would have displayed a
confident `0.0 lb`.

**Two labels had units baked into them**, which only reads correctly in metric:
`BodyMetricsView`'s "Body fat kg" delta card and `GoalForm`'s "Body fat (kg)" option, plus
`InsightService.goalLabel`'s matching string. All now say "mass".

**Spacing and casing were inconsistent before this touched them** — `LatestMeasurementStats`
rendered `79.1 Kg` beside a `-0.7 kg` subtitle in the same card, and `GoalsView` wrote `80%`
where the tiles wrote `21.1 %`. Normalising means §7.1's "metric renders exactly as today" is
satisfied in substance but not literally: four tiles changed `Kg` to `kg` and percentages gained
a space. Agreed with the owner before implementing.

---

## 9. Out of scope

- Renaming `MetricType.BODY_FAT_KG` — §2.2.
- Per-metric unit overrides (weight in lb, water in L). One preference, applied uniformly.
- Height, distance, pace, temperature — no such fields exist (§1.1).
- Stones, or lb+oz composite display.
- Localised number formatting (decimal commas, thousands separators). Related but a separate
  concern, and not what this to-do is about.
