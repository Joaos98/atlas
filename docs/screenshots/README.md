# Screenshots

Drop screenshots here and link them from the repo README's Screenshots section.

## How to capture

1. Build and run the app (see README Quickstart):

   ```bash
   docker compose up -d --build
   # open http://localhost:8080
   ```

2. Capture the main pages. **Use regular viewport screenshots, not
   full-page** — the sidebar is `100vh` + sticky, so it renders correctly at
   one viewport height but gets cut off in taller full-page captures:
   - Dashboard (`/`) — headline stats, insight card, heatmap, weekly chart
   - Workouts (`/workouts`) — log form, heatmap, weekly chart, donut, history
   - Body Metrics (`/body-metrics`) — latest-measurement cards, 5 trend charts
   - Goals (`/goals`) — active goals with progress bars, ETA/pace, past goals
   - Settings (`/settings`) — target, workout types, sync mappings

3. Name files descriptively: `dashboard.png`, `workouts.png`,
   `body-metrics.png`, `goals.png`, `settings.png`.

Tips:

- A browser extension capture or DevTools device-toolbar screenshot works well.
- The demo build (`npm run build:demo` in the frontend repo) shows the same
  pages with seeded data if you'd rather not create real data.
