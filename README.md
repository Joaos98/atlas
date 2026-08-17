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
docker compose exec -T atlas sh -c 'cat /data/atlas.db' > atlas-backup.db
```

**The `-T` is required.** `docker compose exec` allocates a TTY by default, and a TTY rewrites
every `0x0A` byte in the stream — which silently corrupts a binary SQLite file. Without it the
command still appears to succeed and still writes a file.

Verify a backup before trusting it; anything other than `ok` means it is damaged:

```bash
sqlite3 atlas-backup.db "PRAGMA integrity_check;"
```

To restore, replace the file and restart.

## Configuration

| Variable | Required | Default | Description |
|---|---|---|---|
| `SYNC_API_KEY` | Yes | — | API key for the Health Connect sync endpoint |
| `PORT` | No | `8080` | HTTP port |
| `ATLAS_DB_PATH` | No | `atlas.db` | Path to the SQLite database file |

`SYNC_API_KEY` is the only secret in the environment. AI insights are configured in
the app under **Settings → Insights**, not here: provider URL, model and API key are
stored in the database, so switching provider needs no redeploy. Any OpenAI-compatible
endpoint works — OpenAI, Gemini, Groq, OpenRouter, Ollama, LM Studio — and without a
key configured, insights are simply off.

## Syncing workouts from your phone

Atlas can log workouts automatically from **Health Connect** (Android) or Apple
Health (iOS), so sessions recorded by a watch appear without typing anything.

### What sends the data

Atlas does not ship a phone app. It receives from **HC Webhook** — a third-party
app that reads Health Connect and posts it to a URL you choose:

- App and setup: <https://hcwebhook.com/> (Android syncs in the background; iOS
  goes through the Shortcuts app)
- Source, AGPL-3.0: <https://github.com/mcnaveen/health-connect-webhook>
- Payload reference: [`docs/webhook.md`](https://github.com/mcnaveen/health-connect-webhook/blob/main/docs/webhook.md)
  in that repository

**This is a dependency on software nobody here controls.** It could change its
payload, its price, or disappear. What bounds that risk is that `POST /api/sync`
is a published interface — see below — so anything that can send the right shape
works, including thirty lines of your own script.

### Pointing it at Atlas

In HC Webhook, add a webhook with:

| Setting | Value |
|---|---|
| URL | `http://<your-atlas-host>:8080/api/sync` |
| Method | `POST` |
| Custom header | `X-API-Key: <your SYNC_API_KEY>` |
| Schedule | Daily is plenty — it sends a 48-hour window each time |

There is no built-in signature or auth, which is why the key goes in a custom
header, and why Atlas should not be internet-facing.

### First run takes two steps

A new install trusts **no** device, so the first sync looks like it failed:

1. Send a sync. You get `200 OK` and **no workouts** — everything is held.
2. Open **Settings → Sync sources**, find your device, press **Enable**. Everything
   held is added immediately.

Nothing is lost in between: refused workouts are stored, not dropped, because the
sender transmits only what has changed and will not send them again. The dashboard
says how many are waiting.

This is deliberate rather than an oversight. A first payload usually contains both
watch-recorded sessions, whose timestamps are stable, and phone activity-detection
records, whose timestamps shift between syncs and therefore can never be
deduplicated. Enabling by default would quietly admit the second kind on the one
sync where you have no way to notice.

After that, unfamiliar activities need no setup at all — an exercise type Atlas has
not seen creates its own workout type from the Health Connect vocabulary. Map or
merge them later in Settings if you would rather group them.

### The `/api/sync` contract

Any sender that satisfies this works.

```
POST /api/sync
X-API-Key: <SYNC_API_KEY>
Content-Type: application/json
```

```json
{
  "exercise": [
    {
      "type": "79",
      "start_time": "2024-01-15T08:30:00.000Z",
      "duration_seconds": 1800,
      "metadata": {
        "data_origin": "com.example.wearable",
        "recording_method": "automatically_recorded"
      }
    }
  ]
}
```

| Field | Required | Notes |
|---|---|---|
| `type` | Yes | Health Connect exercise type as a **numeric** code, string or number |
| `start_time` | Yes | ISO-8601 UTC. Fractional seconds optional — both forms accepted |
| `duration_seconds` | Yes | Rounded up to whole minutes on storage |
| `metadata.data_origin` | Recommended | Identifies the source in Settings; absent becomes `(none)` |
| `metadata.recording_method` | Recommended | Part of the source identity, with `data_origin` |

Anything else in the payload is ignored, including fields HC Webhook sends that
Atlas does not model (`end_time`, `distance_meters`, `steps`, device details).

**The guarantee: re-send freely.** The endpoint deduplicates on start time plus
exercise type, so repeat deliveries are safe. A sender needs no delivery tracking,
no retry suppression, and no memory of what it sent — retry whenever unsure.

The response says what happened:

```json
{ "created": 3, "skipped": 1, "rejected": 0, "ignored": 0, "rejectedSources": [] }
```

| Field | Meaning |
|---|---|
| `created` | Logged |
| `skipped` | Already present, or unparseable |
| `rejected` | Source not enabled — held, recoverable in Settings |
| `ignored` | Activity mapped to nothing on purpose |

`rejected` above zero with `created` at zero is the first-run case: the sync
worked, the source just is not enabled yet.

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
| `/api/workout-types/{id}` | DELETE/PATCH | — | Delete / rename a type |
| `/api/workout-types/pending-review` | GET | — | Types sync created on its own |
| `/api/workout-types/{id}/merge-into/{targetId}` | POST | — | Merge one type into another |
| `/api/workout-types/{id}/dismiss-review` | POST | — | Stop announcing an auto-created type |
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
| `/api/sync/exercise-types` | GET | — | Health Connect exercise vocabulary |
| `/api/sync/sources` | GET | — | Devices seen, with held counts |
| `/api/sync/sources/{origin}/{method}` | PUT | — | Enable / disable a source; enabling replays |
| `/api/sync/sources/{origin}/{method}/quarantine` | DELETE | — | Discard held entries for a source |

## Entities

- `workout_logs` — logged workouts linked to a workout type
- `workout_types` — custom workout categories with color hex
- `body_metrics` — weight, muscle mass, water, body fat measurements
- `goals` — goals with metric type, target value/date, and status
- `exercise_type_mapping` — maps Health Connect exercise types to workout types;
  a row with no workout type means "never log this activity"
- `sync_sources` — devices seen, and whether they may log workouts
- `quarantined_entries` — workouts held from a source that is not enabled yet
- `app_settings` — single-row app configuration
