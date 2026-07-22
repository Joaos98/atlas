# AIO Fitness — Project Context
 
## What this is
 
A personal fitness tracking web app for a **single user** (the owner). It logs workouts and body composition measurements, visualizes both over time, and surfaces simple rule-based insights connecting the two. It is not a multi-user product — there is no sign-up, no user management, and no public-facing features.
 
---
 
## Tech stack
 
### Backend
- **Java 21**, **Spring Boot 4.1**, **Maven**
- **Spring Data JPA** (Hibernate 7) for ORM
- **Spring Security** with HTTP Basic Auth — single hardcoded user stored in the `users` table with a BCrypt-hashed password
- **PostgreSQL** hosted on **Neon** (managed free tier)
- **Lombok** for reducing boilerplate on entity classes (`@Getter`, `@Setter`)
- REST API, runs on port **9090** locally
### Frontend
- **Vue 3** (Composition API, `<script setup>` syntax), **Vite**
- **Vue Router** for navigation
- **Pinia** for auth state management
- **Axios** for API calls, wrapped in `src/services/api.js` with a request interceptor that attaches Basic Auth credentials from the Pinia store
- **vue-chartjs** + **Chart.js** for line charts
- **lucide-vue-next** for icons
- Runs on port **5173** locally
### Deployment (planned, not done yet)
- Backend: Railway / Render / Fly.io (free tier)
- Frontend: Netlify or Vercel
- CORS is already configured on the backend via `@Value("${app.cors.allowed-origin}")` — update `application.properties` or set the env var when deploying
---
 
## Repository structure
 
Two separate repositories:
- `AIOFitness-backend` — Spring Boot project
- `AIOFitness-frontend` — Vue project
### Backend package structure (`com.joaosousa.aiofitness`)
```
entity/         — JPA entity classes (one per DB table)
repository/     — Spring Data JPA interfaces (one per entity)
service/        — Business logic
controller/     — REST controllers
dto/            — Data Transfer Objects for API responses
enums/          — MetricType, GoalStatus
config/         — SecurityConfig (Spring Security + CORS)
```
 
### Frontend src structure
```
views/          — One component per page (LoginView, DashboardView, WorkoutsView, BodyMetricsView, GoalsView)
components/     — Reusable pieces (NavBar, StatCard, WorkoutHeatmap, WorkoutForm, MetricChart, BodyMetricsForm, GoalForm, DatePicker, WeeklySessionsChart, WorkoutTypeDonut)
services/       — API wrappers (api.js, workoutService.js, bodyMetricsService.js, goalsService.js, statsService.js, settingsService.js, insightService.js)
stores/         — Pinia stores (auth.js — stores username/password in localStorage for Basic Auth)
styles/         — tokens.css (CSS variables for colors, fonts, spacing + global input/select styling), forms.css (shared form field/button/feedback classes)
router/         — index.js with route guard that redirects to /login if not authenticated
utils/          — date.js (UTC-safe local date helpers: toLocalDateStr, todayLocal, formatDateBr)
```
 
---
 
## Database schema
 
All PKs are auto-incrementing `bigint`. No `user_id` on domain tables — single-user by design.
 
### `users`
| Field | Type |
|---|---|
| id | bigint PK |
| username | string |
| password_hash | string |
 
### `workout_types`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| name | string | User-managed, not hardcoded |
| color_hex | string | Used for heatmap coloring |
 
Current workout type colors follow a fixed 5-color palette:
- Gym → `#4F8DFF` (blue)
- Crossfit → `#8B5CF6` (purple)
- 3rd type → `#2DD4BF` (teal)
- 4th type → `#F472B6` (pink)
- 5th type → `#FACC15` (amber)
### `workout_logs`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| workout_type_id | bigint FK | |
| log_date | date | Multiple logs per date allowed |
| duration_minutes | int | Required |
| calories | int | Optional, manual entry only |
 
### `body_metrics`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| measured_on | date | ~2-month cadence |
| weight_kg | decimal | Required |
| muscle_mass_kg | decimal | Required |
| water_liters | decimal | Required |
| body_fat_kg | decimal | Required |
| body_fat_pct | decimal | Required |
 
### `goals`
| Field | Type | Notes |
|---|---|---|
| id | bigint PK | |
| metric_type | enum | WEIGHT, MUSCLE_MASS, WATER, BODY_FAT_KG, BODY_FAT_PCT |
| target_value | decimal | |
| target_date | date | Nullable — if null, show trend-projected ETA |
| status | enum | ACTIVE, ACHIEVED, ABANDONED — never deleted, kept for history |
| created_at | timestamp | Set automatically when the goal is created |
| start_value | decimal | Snapshot of the metric value at goal creation time, used for progress bar |
 
### `app_settings`
Single row (id=1). Contains `target_workouts_per_week` (default 4), used for streak calculation. Week starts on Sunday (fixed constant, not stored).
 
---
 
## API endpoints
 
### Auth
- `GET /api/auth/me` — validates credentials, returns `{ username }`. Used by the login page to test credentials before storing them.
### Workout types
- `GET /api/workout-types`
- `POST /api/workout-types`
- `DELETE /api/workout-types/{id}` — returns 204 on success, 409 Conflict if the type has existing workout logs
### Workout logs
- `GET /api/workout-logs`
- `POST /api/workout-logs`
- `PUT /api/workout-logs/{id}`
- `DELETE /api/workout-logs/{id}`
- `GET /api/workout-logs/heatmap?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD` — returns aggregated daily data for the heatmap (see response shape below)
- `GET /api/workout-logs/streaks` — returns `{ currentStreak, longestStreak }` in weeks
### Body metrics
- `GET /api/body-metrics` — returns plain entries (insight per measurement removed)
- `POST /api/body-metrics`
- `PUT /api/body-metrics/{id}`
- `DELETE /api/body-metrics/{id}`
### Insights
- `GET /api/insights` — returns the latest AI-generated insight text, timestamp, and fallback flag
- `POST /api/insights/regenerate` — regenerates and persists a new insight for the latest measurement
### Goals
- `GET /api/goals` — returns enriched data with `currentValue`, `progressPercent`, `eta` (for undated goals), `paceStatus` (for dated goals)
- `POST /api/goals` — captures `createdAt` and `startValue` automatically from the latest measurement
- `PATCH /api/goals/{id}/status?status=ACHIEVED|ABANDONED`
### Stats
- `GET /api/stats?year=YYYY&month=M` — both params optional, defaults to current month/year. Returns workout stats, body composition changes since first measurement, and streak data.
### Settings
- `GET /api/settings`
- `PUT /api/settings`
### Key response shapes
 
**Heatmap (`GET /api/workout-logs/heatmap`)**
```json
[
  {
    "date": "2026-07-14",
    "workouts": [
      { "type": "Gym", "colorHex": "#4F8DFF", "durationMinutes": 45, "calories": 320 },
      { "type": "Crossfit", "colorHex": "#8B5CF6", "durationMinutes": 35, "calories": null }
    ]
  }
]
```
Days with no workouts are omitted. Multiple logs of the same type on the same day each get their own entry (no merging).
 
**Body metrics (`GET /api/body-metrics`)**
```json
{
  "id": 1,
  "measuredOn": "2026-07-01",
  "weightKg": 78.2,
  "muscleMassKg": 34.1,
  "waterLiters": 41.6,
  "bodyFatKg": 14.8,
  "bodyFatPct": 18.9,
  "insightText": "...",
  "insightGeneratedAt": "2026-07-01T14:30:00"
}
```
The first entry always has `null` for both insight fields (no prior baseline when it was created).
 
---
 
## Features implemented
 
### Backend (complete)
- CRUD for all entities
- Heatmap aggregation with per-day workout grouping
- Weekly streak calculation (distinct workout days per week, Sunday–Saturday)
- Insight engine: rule-based 2×2 matrix (workout frequency vs metric direction), computed on the fly, separate insights for muscle mass and body fat
- Stats endpoint: workout stats (this month + this year), body composition change since first measurement, streaks
- Spring Security with BCrypt, single user account
- CORS configured via property
### Frontend (mostly complete, some pages need polish)
- Login page with credential validation against `/api/auth/me`
- Route guard redirecting unauthenticated users to `/login`
- Sticky sidebar nav with Lucide icons
- Dashboard: streak cards, this-week sessions vs target, 6-month heatmap, this-month & this-year stats, body composition change cards, Insights card (AI-generated analysis, regenerates with each new measurement)
- Workouts page: log form, workout type management (add/delete types with color picker), weekly sessions bar chart with target line, type-breakdown donut, log history table with per-row delete, 6-month heatmap (fills full content width like on Dashboard)
- Body Metrics page: log form, latest-measurement cards with Δ vs previous, 5 line charts (one per metric) with optional active-goal target lines, history table with edit/delete
- Goals page: create goal form, active goals with current→target display + progress bar + remaining-distance text + ETA (for undated goals) + pace check (for dated goals), past goals with status tags
### Design system
- Dark theme: `--bg: #12141A`, `--surface: #1B1E27`, `--border: #2A2E3A`
- Accents: `--blue: #4F8DFF`, `--green: #3DD68C`, `--purple: #8B5CF6`, `--orange: #FB923C`
- Fonts: Space Grotesk (headers), Inter (body), JetBrains Mono (all numeric data, `.data-value` class)
- Color semantics: green = positive/improving, orange = flag/declining, purple = streaks/goals, blue = workouts/neutral data
---
 
## What still needs to be done
 
### UI polish (in progress)
- [x] Workouts page: spacing/layout pass, add icons to form labels
- [x] Body Metrics page: spacing/layout pass, add icons to form labels, chart section could use a two-column grid layout so not all five charts stack vertically
- [x] Goals page: spacing/layout pass
- [x] General: form inputs are mostly browser-default styled, could use improvement, specially date picker
- [x] Stats: think of more stats and graphs that could be useful to have on each page (first batch done: weekly sessions + target, type donut on Workouts; latest measurement + goal lines on Body Metrics; remaining text on Goals; this-week card + this-year row on Dashboard; backend batch done: goal `created_at` + `start_value`, progress bar, ETA projection for undated goals, pace check for dated goals)
- [x] Interactive polish: hover effects on all buttons and links; page-level loading animation (pulsing text) when waiting for backend data
### Management features (in progress)
- [x] **Manage workout logs** — inline edit (date, type, duration, calories) + delete with confirmation
- [x] **Manage body metrics entries** — inline edit (all fields) + delete with confirmation
- [x] **Graceful handling of workout type deletion** — `DELETE /api/workout-types/{id}` endpoint added; returns 204 on success, 409 Conflict if the type has existing logs (handled gracefully on the frontend)
### AI-powered insights (not started)
- [x] Improve the rule-based insight engine (the 2×2 matrix in `BodyMetricsService`). Replaced with AI-generated insights via Gemini Flash — a single natural-language analysis on the Dashboard, regenerated after each measurement. The insight text is stored on the `body_metrics` row. Falls back to a templated summary if the AI API is unavailable. Requires `GEMINI_API_KEY` env var.
- [x] Dropped per-entry insight badges from the history table (redundant with the dashboard analysis)
### Deployment (not started)
- [ ] Deploy backend to Railway / Render / Fly.io free tier
- [ ] Deploy frontend to Netlify or Vercel
- [ ] Set environment variables on both platforms:
  - Backend: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_CORS_ALLOWED_ORIGIN`
  - Frontend: `VITE_API_URL`
- [ ] Update `app.cors.allowed-origin` to the deployed frontend URL
---
 
## Known quirks / decisions to be aware of
 
- **Date handling:** always be aware of how to work with dates so that they stay the correct day after the browser's UTC parsing shifts dates by the user's UTC offset (UTC-3 in the user's case), causing off-by-one day bugs in the heatmap. Use the helpers in `src/utils/date.js`. All date inputs use the custom `DatePicker` component (displays `dd/mm/yyyy`, emits `yyyy-MM-dd`) — native date inputs can't be reformatted and are no longer used.
- **Basic Auth in localStorage:** credentials are stored in `localStorage` for simplicity — acceptable for a single-user personal app behind HTTPS, but not a general best practice
- **`app_settings` seeding:** the `app_settings` table requires a manual `INSERT` after first run since Hibernate creates the table but doesn't seed it: `INSERT INTO app_settings (id, target_workouts_per_week) VALUES (1, 4);`
- **Insight suppression:** any analytics or insight that requires a baseline (streaks need data, insights need 2+ measurements) are suppressed rather than shown with misleading partial data
- **Body metrics all required:** all five metric fields on `body_metrics` are required — this was a deliberate decision to avoid null-handling complexity in the insight and goal logic. The assumption is that the user's bioimpedance scale reports all five values in a single reading.
- **Goal `created_at` / `start_value` columns:** these were added to the `goals` schema after initial creation. If Hibernate's ddl-auto doesn't add them automatically, run: `ALTER TABLE goals ADD COLUMN created_at TIMESTAMP; ALTER TABLE goals ADD COLUMN start_value DOUBLE PRECISION;`. Existing goals will have NULL for both and the progress features will gracefully hide (no crash).
- **Insight columns on `body_metrics`:** two new columns (`insight_text TEXT`, `insight_generated_at TIMESTAMP`) were added. Run: `ALTER TABLE body_metrics ADD COLUMN insight_text TEXT; ALTER TABLE body_metrics ADD COLUMN insight_generated_at TIMESTAMP;`. The insight is generated when a new measurement is saved (POST), not hydrated from existing rows.
- **Gemini API key:** set `GEMINI_API_KEY` as an environment variable. If missing, the insight falls back to a templated summary (auto-generated, labeled as fallback).
