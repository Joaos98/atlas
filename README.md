# Atlas

Self-hosted fitness tracker for one person: workout logging, body metrics,
goals, weekly streaks, and AI insights. Spring Boot 4.1 + Java 21 backend with
SQLite, Vue 3 frontend served from the same origin.

**Atlas is not internet-facing.** It has no authentication and is designed to
run on your own hardware, behind your network or reverse proxy (Tailscale,
Caddy, Authelia — whatever you already use). Do not expose it publicly.

## Try the demo

A static demo with seeded data runs entirely in your browser, with no
backend of any kind. It's not hosted anywhere yet — for now, build it
locally:

```bash
cd ui
npm install
npm run build:demo && npm run preview
```

See [`ui/README.md`](ui/README.md#testing) for how the demo is verified
against this real backend rather than merely resembling it.

## Screenshots

> Coming soon — capture the app running and drop the images into
> `docs/screenshots/`, then link them here.

To capture: build and run the app (Quickstart below), then screenshot the
Dashboard, Workouts, Body Metrics, Goals, and Settings pages. Full-page
captures work best with a dark theme.

## Quickstart (Docker)

```bash
# From the repo root
echo "SYNC_API_KEY=$(openssl rand -hex 24)" > .env
docker compose up -d --build
# open http://localhost:8080
```

The image builds the frontend (`ui/`) and backend (`server/`) together. The
SQLite database lives at `/data/atlas.db` inside a named volume.

**Back up by copying the file** — the entire app state is one SQLite file:

```bash
docker compose exec atlas sh -c 'cat /data/atlas.db' > atlas-backup.db
```

To restore, replace the file and restart.

## Configuration

| Variable | Required | Default | Description |
|---|---|---|---|
| `SYNC_API_KEY` | Yes | — | API key for the Health Connect sync endpoint |
| `GEMINI_API_KEY` | No | — | Enables AI insight generation; without it insights show an unavailable message |
| `PORT` | No | `8080` | HTTP port |
| `ATLAS_DB_PATH` | No | `atlas.db` | Path to the SQLite database file |

## Local development

See [`server/README.md`](server/README.md) for running the backend, and
[`ui/README.md`](ui/README.md) for the frontend dev server and demo build.

## Architecture

```
┌─────────────┐      ┌──────────────────────────┐      ┌──────────┐
│  Vue 3 App  │─────▶│  Spring Boot Backend     │─────▶│  SQLite  │
│  (served by │      │  (same origin, /api)     │      │ atlas.db │
│  Spring)    │      │  Sync: X-API-Key         │      └──────────┘
└─────────────┘      └──────────────────────────┘
```

```
atlas/
├── Dockerfile        # multi-stage: node builds ui/ -> maven builds server/
├── compose.yaml       # one service, one named volume
├── server/            # Spring Boot backend (Java 21, SQLite)
└── ui/                 # Vue 3 frontend (self-hosted + static demo builds)
```

## API Endpoints

| Endpoint | Method | Auth | Description |
|---|---|---|---|
| `/api/workout-logs` | GET/POST | — | List / create workout logs |
| `/api/workout-logs/{id}` | DELETE/PUT | — | Delete / update a log |
| `/api/workout-logs/heatmap` | GET | — | Heatmap data for calendar |
| `/api/workout-logs/streaks` | GET | — | Current and longest streak |
| `/api/workout-types` | GET/POST | — | List / create workout types |
| `/api/workout-types/{id}` | DELETE | — | Delete a type |
| `/api/body-metrics` | GET/POST | — | List / create body measurements |
| `/api/body-metrics/{id}` | DELETE/PUT | — | Delete / update a measurement |
| `/api/goals` | GET/POST | — | List / create goals |
| `/api/goals/{id}/status` | PATCH | — | Update goal status |
| `/api/goals/{id}` | DELETE | — | Delete a goal |
| `/api/stats` | GET | — | Aggregated workout & body stats |
| `/api/insights` | GET | — | Latest AI-generated insight |
| `/api/insights/regenerate` | POST | — | Regenerate AI insight |
| `/api/settings` | GET/PUT | — | App-wide settings |
| `/api/sync` | POST | X-API-Key | Health Connect sync endpoint |
| `/api/sync/mappings` | GET/POST/DELETE | — | Exercise type mappings |

## Entities

- `workout_logs` — logged workouts linked to a workout type
- `workout_types` — custom workout categories with color hex
- `body_metrics` — weight, muscle mass, water, body fat measurements
- `goals` — goals with metric type, target value/date, and status
- `exercise_type_mapping` — maps Health Connect exercise types to workout types
- `app_settings` — single-row app configuration
