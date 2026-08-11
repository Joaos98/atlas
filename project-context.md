# Atlas — Project Context

## What this is

A personal fitness tracking web app for a **single user** (the owner). It logs
workouts and body composition measurements, visualizes both over time, computes
weekly streaks and goal progress, and surfaces AI insights connecting the two.
It is not a multi-user product — there is no sign-up, no user management, and
no public-facing features.

**Atlas is not internet-facing.** It has no authentication and is designed to
run on the owner's own hardware, behind their network or reverse proxy. Do not
expose it publicly.

Two builds ship from one codebase:

- **Self-hosted build** — Spring Boot serves the built Vue frontend from
  `static/` (same origin), SQLite in a file, one Docker container. This is the
  real app.
- **Demo build** — the same frontend against browser storage, seeded with
  realistic data, hosted statically. This is the portfolio artifact and the way
  anyone tries the app before self-hosting. AI insight *generation* is
  unavailable in the demo and says so — it requires the real backend with a
  Gemini API key.

---

## Tech stack

### Backend (`atlas-backend`)
- **Java 21**, **Spring Boot 4.1**, **Maven**
- **Spring Data JPA** (Hibernate 7) for ORM
- **SQLite** via `sqlite-jdbc` + the Hibernate community dialect — one file,
  `ddl-auto=update`, no auth, no CORS
- **Lombok** for boilerplate reduction on entities (`@Getter`, `@Setter`)
- **Google Gemini API** — AI insight generation (optional; without a key the
  insight falls back to an error message)
- REST API, serves the built frontend from `static/`

### Frontend (`atlas-frontend`)
- **Vue 3** (Composition API, `<script setup>`), **Vite**
- **Vue Router** for navigation
- **Pinia** for toast state
- **Axios** for API calls, wrapped in `src/services/api.js`
- **vue-chartjs** + **Chart.js** for charts
- **lucide-vue-next** for icons
- **Vitest** for tests

---

## Repository structure

Two separate repositories:
- `atlas-backend` — Spring Boot project
- `atlas-frontend` — Vue project

### Backend package structure (`com.joaosousa.atlas`)
```
config/         — TimeConfig (Clock bean), SpaForwardFilter (SPA fallback),
                  AppSettingsSeeder (idempotent settings row at startup)
controller/     — REST controllers
service/        — Business logic (WorkoutLogService, StatsService, GoalService,
                  InsightService, SyncService, ...)
entity/         — JPA entity classes (one per DB table)
repository/     — Spring Data JPA interfaces (one per entity)
dto/            — Data Transfer Objects for API responses
```

### Frontend src structure
```
views/          — One component per page (DashboardView, WorkoutsView,
                  BodyMetricsView, GoalsView, SettingsView)
components/     — Reusable pieces (NavBar, StatCard, WorkoutHeatmap,
                  WorkoutForm, MetricChart, BodyMetricsForm, GoalForm,
                  DatePicker, InsightCard, charts, ...)
services/       — API wrappers (api.js, workoutService.js, bodyMetricsService.js,
                  goalsService.js, statsService.js, settingsService.js,
                  insightService.js, syncService.js)
demo/           — Demo build only: demoApi.js (localStorage adapter),
                  derived.js (JS port of derived logic), seed.js,
                  demo-seed.json + expected-derived.json (generated fixtures),
                  fixture.test.js, demoApi.test.js, DemoBanner.vue,
                  InsightGateModal.vue
stores/         — Pinia stores (toast.js)
styles/         — tokens.css (design tokens), forms.css (shared form styles)
router/         — index.js route definitions (no auth guard)
utils/          — date.js (UTC-safe local date helpers)
```

---

## Database schema

All PKs are auto-incrementing `bigint`. No `user_id` on domain tables —
single-user by design. Stored in a single SQLite file (`atlas.db` by default).

### `app_settings`
Single row (id=1), seeded at startup by `AppSettingsSeeder`.
Contains `target_workouts_per_week` (default 3), used for streak calculation.
Week starts on Sunday (fixed constant, not stored).

### `workout_types`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| name | string | User-managed, not hardcoded |
| color_hex | string | Used for heatmap coloring |

### `workout_logs`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | IDENTITY |
| workout_type_id | bigint FK | |
| log_date | date | Multiple logs per date allowed |
| duration_minutes | int | Required |
| sync_signature | string | Dedup key for Health Connect sync |

### `body_metrics`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| measured_on | date | |
| weight_kg | decimal | |
| muscle_mass_kg | decimal | |
| water_liters | decimal | |
| body_fat_kg | decimal | |
| body_fat_pct | decimal | |
| insight_text | text | AI insight for this measurement |
| insight_generated_at | timestamp | |

### `goals`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| metric_type | enum | WEIGHT, MUSCLE_MASS, WATER, BODY_FAT_KG, BODY_FAT_PCT |
| target_value | decimal | |
| target_date | date | Nullable — if null, show trend-projected ETA |
| status | enum | ACTIVE, ACHIEVED, ABANDONED |
| created_at | timestamp | Set automatically when the goal is created |
| start_value | decimal | Snapshot of the metric value at goal creation time |

### `exercise_type_mapping`
Maps Health Connect exercise type codes to `workout_types` so auto-synced
workouts are logged correctly.

---

## API endpoints

### Workout types
- `GET /api/workout-types`
- `POST /api/workout-types`
- `DELETE /api/workout-types/{id}` — 409 Conflict if the type has logs

### Workout logs
- `GET /api/workout-logs` (paginated, sorted by date desc)
- `POST /api/workout-logs`
- `PUT /api/workout-logs/{id}`
- `DELETE /api/workout-logs/{id}`
- `GET /api/workout-logs/heatmap?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD` — aggregated daily data
- `GET /api/workout-logs/streaks` — `{ currentStreak, longestStreak }` in weeks

### Body metrics
- `GET /api/body-metrics`
- `POST /api/body-metrics` — triggers AI insight generation for the new measurement
- `PUT /api/body-metrics/{id}`
- `DELETE /api/body-metrics/{id}`

### Insights
- `GET /api/insights` — latest AI-generated insight for the latest measurement
- `POST /api/insights/regenerate` — regenerates and persists

### Goals
- `GET /api/goals` — enriched: `currentValue`, `progressPercent`, `eta`
  (undated goals), `paceStatus` (dated goals)
- `POST /api/goals` — captures `createdAt` and `startValue` automatically
- `PATCH /api/goals/{id}/status?status=ACHIEVED|ABANDONED`

### Stats
- `GET /api/stats?year=YYYY&month=M` — both params optional, defaults to the
  clock's current year/month. Returns workout stats, body composition change
  since the first measurement, and streak data.

### Settings
- `GET /api/settings`
- `PUT /api/settings`

### Sync
- `POST /api/sync` — Health Connect webhook (X-API-Key required)
- `GET/POST/DELETE /api/sync/mappings`

---

## Time handling

All date-dependent logic (streaks, stats defaults, goal ETA/pace) goes through
an injected `java.time.Clock` bean — production uses the system clock, tests
pin it to a fixed reference date. This is what makes the seed generator and
fixture tests deterministic.

## The seed generator / fixture contract

The demo is verified against the real backend, not just "similar to" it:

- `SeedGenerator` (backend test, tagged `seed-generator`) drives the real API
  via MockMvc against a fixed clock: creates types, logs ~19 months of
  workouts, ~monthly body metrics, and goals. It emits two files into the
  frontend repo:
  - `demo-seed.json` — all rows with **day offsets** from a reference Sunday
  - `expected-derived.json` — the actual HTTP responses for stats, streaks,
    heatmap, and goals
- Regenerate deliberately: `mvnw test -Dtest=SeedGenerator -Dsurefire.excludedGroups=`
  (set `GEMINI_API_KEY` to freeze a real insight onto the latest measurement;
  the free tier rate-limits, so it retries with backoff)
- The demo materializes the seed's offsets against the visitor's most recent
  Sunday (whole-week shifts, so week bucketing matches what Java computed)
- `fixture.test.js` (frontend) loads the seed, runs the ported JS logic, and
  asserts it equals the recorded Java responses — if either drifts, the test
  fails loudly

## Known quirks / decisions to be aware of

- **Date handling:** the frontend works with `yyyy-MM-dd` strings and
  UTC-safe helpers (`src/utils/date.js`) to avoid browser UTC-parsing
  off-by-one shifts. Use those helpers; avoid `toISOString()`.
- **`app_settings` seeding:** `AppSettingsSeeder` inserts the single row
  (id=1, target=3) idempotently at startup — no manual SQL needed.
- **Insight suppression:** analytics that require a baseline (streaks need
  data, insights need a measurement) are suppressed rather than shown with
  misleading partial data.
- **Body metrics all required:** all five metric fields are required — a
  deliberate decision to avoid null-handling complexity in insight and goal
  logic. The bioimpedance scale reports all five in one reading.
- **Gemini API key:** set `GEMINI_API_KEY`. If missing, insight generation
  returns an error message (the UI surfaces it as unavailable). In the demo,
  the regenerate action is gated with a warning modal.
- **SQLite is single-writer:** Hikari pool capped at 1 connection.
- **Backup = copy the file:** `cp atlas.db` (or `docker compose exec atlas
  sh -c 'cat /data/atlas.db' > backup.db`) is the entire backup story.

## Deployment

See the backend README for the one-container Docker quickstart. The demo build
is a static folder — deploy `dist/` anywhere.
