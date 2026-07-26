# Atlas Backend

REST API backend for a personal single-user fitness tracking application. Built with Spring Boot 4.1.0 and Java 21.

## Tech Stack

- **Spring Boot 4.1.0** — WebMVC, Data JPA, Security, Validation
- **Java 21**
- **PostgreSQL** — production database
- **Maven** — build tool (wrapper included)
- **Google Gemini API** — AI-powered fitness coaching insights

## Architecture

```
┌─────────────┐     ┌──────────────────────┐
│  Vue 3 App  │────▶│  Spring Boot Backend │────▶ PostgreSQL
└─────────────┘     │  (port 8080)         │
                    ├──────────────────────┤
                    │  Auth: HTTP Basic    │
                    │  CORS: frontend URL  │
                    │  Sync: X-API-Key     │
                    └──────────────────────┘
```

## API Endpoints

| Endpoint | Method | Auth | Description |
|---|---|---|---|
| `/api/auth/me` | GET | Basic | Returns authenticated username |
| `/api/workout-logs` | GET/POST | Basic | List / create workout logs |
| `/api/workout-logs/{id}` | DELETE/PUT | Basic | Delete / update a log |
| `/api/workout-logs/heatmap` | GET | Basic | Heatmap data for calendar |
| `/api/workout-logs/streaks` | GET | Basic | Current and longest streak |
| `/api/workout-types` | GET/POST | Basic | List / create workout types |
| `/api/workout-types/{id}` | DELETE | Basic | Delete a type |
| `/api/body-metrics` | GET/POST | Basic | List / create body measurements |
| `/api/body-metrics/{id}` | DELETE/PUT | Basic | Delete / update a measurement |
| `/api/goals` | GET/POST | Basic | List / create goals |
| `/api/goals/{id}/status` | PATCH | Basic | Update goal status |
| `/api/goals/{id}` | DELETE | Basic | Delete a goal |
| `/api/stats` | GET | Basic | Aggregated workout & body stats |
| `/api/insights` | GET | Basic | Latest AI-generated insight |
| `/api/insights/regenerate` | POST | Basic | Regenerate AI insight |
| `/api/settings` | GET/PUT | Basic | App-wide settings |
| `/api/sync` | POST | X-API-Key | Health Connect sync endpoint |
| `/api/sync/mappings` | GET/POST/DELETE | Basic | Exercise type mappings |

## Entities

- `users` — single user with bcrypt password hash
- `workout_logs` — logged workouts linked to a workout type
- `workout_types` — custom workout categories with color hex
- `body_metrics` — weight, muscle mass, water, body fat measurements
- `goals` — goals with metric type, target value/date, and status
- `exercise_type_mapping` — maps Health Connect exercise types to workout types
- `app_settings` — single-row app configuration

## Getting Started

### Prerequisites

- Java 21+
- PostgreSQL instance

### Local Development

```bash
# Set local profile (uses application-local.properties)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Or build and run:

```bash
./mvnw clean package -DskipTests
java -jar target/atlas-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

### Configuration

**Production** (via environment variables):

| Variable | Required | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | JDBC URL for PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Yes | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Database password |
| `PORT` | No | Server port (default 8080) |
| `APP_CORS_ALLOWED_ORIGIN` | Yes | Frontend URL for CORS |
| `GEMINI_API_KEY` | No | Google Gemini API key |
| `SYNC_API_KEY` | Yes | API key for sync endpoint |

**Local** (`application-local.properties`) — overrides for development.

## Deployment

Build the JAR and run with environment variables:

```bash
./mvnw clean package -DskipTests
java -jar target/atlas-0.0.1-SNAPSHOT.jar
```

The app binds to `0.0.0.0:${PORT}` and uses `validate` DDL mode in production — tables must already exist.
