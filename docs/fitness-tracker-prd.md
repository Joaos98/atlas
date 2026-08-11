# Fitness Tracker — Product Requirements Document

## 1. Overview

A personal fitness tracking web app for a single user. It logs workouts and
body composition measurements, visualizes both over time, and surfaces
simple, rule-based insights connecting the two. Deployed to the cloud (free
tier) so it's reachable from a phone, but never intended for other users.

## 2. Goals & non-goals

**Goals**
- Make it effortless to log a workout or a body-metric reading.
- Make consistency visible (heatmap, streaks) as a motivator.
- Make progress visible (metric trend graphs, goal progress).
- Give lightweight, honest context when a measurement is better or worse
  than expected, without pretending to predict the future.

**Non-goals**
- Not a multi-user product. No public sign-up, no user management beyond a
  single account.
- Not a substitute for a nutritionist/trainer — no diet logging, no
  training-plan generation.
- No attempt to *predict* future measurements from workout data (see
  §9, out of scope). The data is too sparse and too confounded for that to
  be honest.

## 3. Users

One user: the app owner. No roles, no permissions model.

## 4. Tech stack & architecture

- **Backend:** Java, Spring Boot, REST API. Spring Security for auth.
- **Frontend:** Vue.js, deployed as a separate SPA (not served by Spring
  Boot). Chosen partly as a learning exercise.
- **Database:** PostgreSQL, managed free tier (e.g. Neon, Supabase, or
  Railway) — chosen over SQLite for first-class Hibernate/Spring Data
  support and to avoid persistent-volume complications on ephemeral cloud
  filesystems.
- **Deployment shape:** frontend and backend deployed independently (e.g.
  Vue on Netlify/Vercel, Spring Boot on Railway/Render/Fly.io). Backend
  must configure CORS to allow the frontend's origin.
- **Auth:** Since the app will be reachable at a public URL (even if
  unlisted), it needs real authentication, not security-through-obscurity.
  Spring Security with a single user account (hashed password) is
  sufficient — no OAuth, no social login, no multi-account support needed.

## 5. Data model

All primary keys are plain auto-incrementing `bigint`. No `user_id` foreign
keys on domain tables — this is intentionally single-user; `users` exists
only for login.

### `users`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| username | string | |
| password_hash | string | |

### `workout_types`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| name | string | e.g. "Gym", "Crossfit", "Running" — user-managed, not hardcoded |
| color_hex | string | used for heatmap cell coloring |

### `workout_logs`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| workout_type_id | bigint FK → workout_types | |
| log_date | date | **multiple logs per date are allowed** (e.g. AM + PM session) |
| duration_minutes | int | required |
| calories | int | optional, manual entry only (see §8.6) |

### `body_metrics`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| measured_on | date | |
| weight_kg | decimal | **required** |
| muscle_mass_kg | decimal | **required** |
| water_liters | decimal | **required** |
| body_fat_kg | decimal | **required** |
| body_fat_pct | decimal | **required** |

All five metric fields are required on every entry — this assumes the
bioimpedance scale used (at the user's nutritionist) reports all of them in
a single reading. This avoids null-handling in the goals/insight logic
below. If that assumption turns out to be wrong in practice, this table is
the first place to revisit.

### `goals`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| metric_type | string enum | `WEIGHT`, `MUSCLE_MASS`, `WATER`, `BODY_FAT_KG`, `BODY_FAT_PCT` |
| target_value | decimal | |
| target_date | date, nullable | if set, show progress against a deadline; if null, show a trend-projected ETA instead |
| status | string enum | `ACTIVE`, `ACHIEVED`, `ABANDONED` — old goals are kept, not overwritten |

### `app_settings`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | single row |
| target_workouts_per_week | int | used for streak calculation, default 4 |

Note: week start (Sunday) is a fixed application constant, not a stored
setting.

## 6. Features

### 6.1 Workout logging
Log a date, a workout type (from the user-managed `workout_types` list),
duration in minutes, and optionally calories burned. Multiple entries per
day are allowed and expected.

### 6.2 Workout heatmap
A GitHub-style calendar grid, one cell per day.
- A day with one workout type is filled with that type's color.
- A rest day is empty/neutral.
- A day with multiple *distinct* workout types splits the cell into equal
  vertical segments, one per distinct type (doing "Gym" twice in one day is
  still one Gym-colored segment, not two — the split reflects variety of
  training that day, not raw log count).
  > **Assumption to confirm:** this is the current design. If instead every
  > individual log entry should get its own segment (so two Gym sessions in
  > a day would show as two Gym segments), flag it — it's a small change to
  > the aggregation query, not the schema.
- Hovering a cell shows that day's detail: workout type(s), duration,
  calories. Given the low data volume (a few hundred rows/year), the
  frontend should load the full visible date range in one request and do
  the hover lookup client-side — no per-hover API call needed.

### 6.3 Weekly streaks
- A week (Sunday–Saturday) "counts" toward a streak if the number of
  **distinct workout dates** in that week meets or exceeds
  `app_settings.target_workouts_per_week`. Three workouts on the same day
  still only count as one qualifying day.
- Streak = current consecutive run of qualifying weeks; also track longest
  streak ever, for the stats dashboard.
- No separate "streak goal" feature — decided against, since the streak
  count itself already conveys the same information a target would, and an
  arbitrary week-count target doesn't add real value here.

### 6.4 Body metrics logging & progress graphs
Log a date and all five required metrics (§5). Show a line/point chart per
metric over time. Given the ~2-month measurement cadence (~6 points/year),
charts should clearly mark actual measurement dates as discrete points
rather than implying a smooth, continuous trend between distant
measurements.

### 6.5 Insight engine (workout ↔ metric cross-reference)
For each pair of consecutive measurements, compare:
1. **Workout frequency** in that period vs. the user's trailing personal
   average (average workouts/period across all prior periods). Use a
   ±15–20% neutral band around the average so small fluctuations don't
   flip the classification — below the band = "below average", within or
   above = "at/above average".
2. **Metric direction** — did the metric decline, or hold/improve?

These combine into one of four messages:

| | Metric declined | Metric held/improved |
|---|---|---|
| **Workouts below average** | *Expected dip* — matches reduced activity | *Nice surprise* — better than expected |
| **Workouts at/above average** | *Worth investigating* — flag to check diet, sleep, etc. | *On track* — consistency paying off |

- Computed on the fly from `workout_logs` + `body_metrics` whenever the
  metrics page is viewed — **not persisted**, no dedicated table.
- Suppressed whenever there isn't enough data for a meaningful comparison:
  specifically, the first measurement ever (no prior baseline) shows no
  insight message.
- No prediction of future measurement values (see §9).

### 6.6 Goals (body metrics only)
Set a target value for any of the five metrics, with an optional target
date.
- If a target date is set: show progress as current value vs. target,
  against that deadline.
- If no target date: compute a simple linear-trend projection from recent
  measurements to estimate when the target will be reached. Given the
  sparse data (~6 points/year), a simple trend line is the right level of
  sophistication — no more complex modeling is justified.
- `status` lets old goals be marked achieved/abandoned rather than deleted,
  preserving history.

### 6.7 Stats dashboard
- Current streak, longest streak (per §6.3's definition).
- Workout type breakdown (count by type).
- Weekly/monthly workout frequency over time.
All of this reuses the same aggregation queries as the heatmap and streak
features.

### 6.8 Authentication
Single hardcoded user account via Spring Security (username + hashed
password). No registration flow, no password reset flow, no multi-user
support.

## 7. Key API response shapes

Most endpoints are plain CRUD over the tables in §5 and can be designed
naturally during implementation. Two responses are less obvious and worth
specifying up front:

**Heatmap range query** — given a date range, return one entry per day with
workouts nested:
```json
[
  {
    "date": "2026-07-14",
    "workouts": [
      { "type": "Gym", "colorHex": "#378ADD", "durationMinutes": 45, "calories": 320 },
      { "type": "Crossfit", "colorHex": "#EF9F27", "durationMinutes": 35, "calories": null }
    ]
  }
]
```
Days with no workouts can simply be omitted (frontend treats missing dates
as rest days) rather than returned as empty entries.

**Body metric entry with insight** — when returning a measurement (or list
of measurements), include the computed insight alongside it:
```json
{
  "measuredOn": "2026-07-01",
  "weightKg": 78.2,
  "muscleMassKg": 34.1,
  "waterLiters": 41.6,
  "bodyFatKg": 14.8,
  "bodyFatPct": 18.9,
  "insight": {
    "workoutCountThisPeriod": 32,
    "personalAverage": 38,
    "frequencyStatus": "below_average",
    "metricChangeDirection": "declined",
    "messageType": "expected_dip"
  }
}
```
`insight` is `null` when there's no prior measurement to compare against.

## 8. Non-functional requirements

- **Scale:** trivially small (hundreds of workout rows/year, ~6 body-metric
  rows/year). No performance or indexing concerns beyond basic good
  practice.
- **Units:** metric throughout (kg, L, %).
- **Accessibility from phone:** the Vue frontend should be usable on a
  mobile browser; no native app is planned.
- **Cost:** must run on free tiers (hosting + managed Postgres).

## 9. Explicitly out of scope

Decided against during planning, kept here so they aren't silently
reconsidered later:
- **Predicting future measurements** from workout data — data is too sparse
  (~6 points/year) and too confounded by untracked factors (diet, sleep,
  stress) for a model to be honest.
- **Workout streak goals** — redundant with simply displaying the current
  streak.
- **Session notes / context tags** on workouts.
- **Data export** (CSV/JSON).
- **Progress photos.**
- **Automatic calorie sync** from a smartband/wearable API — calories are
  manual-entry only, and treated as a rough/secondary signal, not wired
  into the insight engine (§6.5), since wrist-based calorie estimates are
  known to be noisy, especially for non-steady-state training.

## 10. Open items to confirm before/while building

- Whether the heatmap split-cell logic should be by distinct workout type
  per day (current assumption, §6.2) or by raw log count.
- Whether the nutritionist's scale reliably reports all five body metrics
  every visit (assumed yes, §5).
- Whether `body_metrics.measured_on` should be unique (currently
  unconstrained, same as `workout_logs.log_date` — multiple entries per day
  allowed by default, though unlikely to occur in practice).
