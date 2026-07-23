# aio-fitness Frontend

Personal single-user fitness tracking dashboard. Built with **Vue 3** (Composition API), **Vite**, **Pinia**, and **Vue Router**.

## Tech Stack

- **Vue 3** — Composition API with `<script setup>`
- **Vite 8** — dev server and build tool
- **Pinia** — state management
- **Vue Router 5** — client-side routing
- **Axios** — HTTP client with Basic Auth interceptor
- **Chart.js + vue-chartjs** — data visualization
- **Lucide** — icon library

## Features

- **Dashboard** — weekly progress stats, streaks, workout heatmap, body composition changes, AI coaching insights
- **Workouts** — log workouts, heatmap calendar, weekly chart, type distribution donut, log history
- **Body Metrics** — track weight, muscle mass, water, body fat with trend charts
- **Goals** — set goals with progress bars, ETA predictions, pace tracking
- **Settings** — weekly workout target, workout type management, Health Connect sync mappings

## Pages

| Route | Page |
|---|---|
| `/login` | Login with Basic Auth |
| `/` | Dashboard |
| `/workouts` | Workout tracking |
| `/body-metrics` | Body composition |
| `/goals` | Goal management |
| `/settings` | App settings |

## Project Structure

```
src/
├── main.js                  # App entry point
├── App.vue                  # Root component (navbar + router-view)
├── components/              # Reusable UI components
│   ├── NavBar.vue           # Sidebar navigation
│   ├── WorkoutForm.vue      # Workout log form
│   ├── BodyMetricsForm.vue  # Body measurement form
│   ├── GoalForm.vue         # Goal creation form
│   ├── StatCard.vue         # Dashboard stat cards
│   ├── InsightCard.vue      # AI insight display
│   ├── MetricChart.vue      # Line chart for metrics
│   ├── WorkoutHeatmap.vue   # Calendar heatmap
│   ├── WeeklyWorkoutsChart.vue # Weekly bar chart
│   ├── WorkoutTypeDonut.vue # Type distribution chart
│   ├── DayOfWeekChart.vue   # Day-of-week distribution
│   ├── DurationHistogram.vue # Duration distribution
│   ├── MetricSparkline.vue  # Mini sparkline
│   ├── LatestMeasurementStats.vue # Latest body stats
│   ├── DatePicker.vue       # Date picker component
│   ├── SkeletonLoader.vue   # Loading skeleton
│   ├── EmptyState.vue       # Empty state placeholder
│   └── ToastContainer.vue   # Toast notifications
├── views/                   # Page components
│   ├── LoginView.vue
│   ├── DashboardView.vue
│   ├── WorkoutsView.vue
│   ├── BodyMetricsView.vue
│   ├── GoalsView.vue
│   └── SettingsView.vue
├── router/
│   └── index.js             # Route definitions + auth guard
├── stores/
│   ├── auth.js              # Auth state (username/password in localStorage)
│   └── toast.js             # Toast notification state
├── services/
│   ├── api.js               # Axios instance with Basic Auth
│   ├── workoutService.js
│   ├── bodyMetricsService.js
│   ├── goalsService.js
│   ├── statsService.js
│   ├── insightService.js
│   ├── settingsService.js
│   └── syncService.js
├── styles/
│   ├── tokens.css           # CSS custom properties (design tokens)
│   └── forms.css            # Shared form styles
├── utils/
│   └── date.js              # UTC-safe date helpers
└── assets/                  # Static assets
```

## Getting Started

### Prerequisites

- Node.js ^22.18.0 \|\| >=24.12.0

### Setup

```sh
npm install
npm run dev
```

The dev server runs on `http://localhost:5173`.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `VITE_API_URL` | Yes | `http://localhost:9090/api` | Backend API base URL |

Create a `.env` file in the project root:

```
VITE_API_URL=http://localhost:9090/api
```

For production, set `VITE_API_URL` to your deployed backend URL.

## Scripts

| Script | Description |
|---|---|
| `npm run dev` | Start dev server with hot-reload |
| `npm run build` | Build for production into `dist/` |
| `npm run preview` | Preview the production build locally |
| `npm run lint` | Run all linters (oxlint + eslint) |
| `npm run format` | Format code with Prettier |

## Deployment

Build and deploy to any static hosting:

```sh
npm run build
```

The `dist/` folder contains the production build. Set `VITE_API_URL` to your backend URL during build.

### Vercel (recommended)

- Framework preset: **Vite**
- Build command: `npm run build`
- Output directory: `dist`
- Environment variable: `VITE_API_URL` set to your Railway backend URL
