# Atlas — Self-Hosting & Demo Plan

**Status: COMPLETE.** Written 2026-07-30 as a planned refactor; all six phases shipped between 2026-08-08 and 2026-08-11. Each workstream below is annotated with what actually happened, including every deviation from the original text. The plan text is preserved as written — strikethrough/markers distinguish "as planned" from "as built".

## 1. Goal

Atlas today is a hosted app: Spring Boot on Render, Postgres on Neon, Vue on Vercel, single user behind HTTP Basic auth. Free-tier cold starts make the hosted instance unpleasant enough that it isn't worth keeping.

The end state has **no hosted instance**. Instead:

- **A self-hosted build** — one container, SQLite in a file, no login, run on your own hardware with your real data.
- **A static demo build** — the same frontend against browser storage, seeded with sample data, hosted free with zero cold start. This is the portfolio artifact and the way anyone tries the app before deciding to self-host.

This mirrors the deployment model in [prometheus-system-plan.md](../../Dev/Prometheus/prometheus-system-plan.md) §5, with one deliberate difference: Atlas keeps a real backend for the self-hosted build rather than moving its domain into the browser.

> **Status:** delivered. Self-hosted build runs as one Docker container (multi-stage: node → maven → JRE); demo build is a static folder. The hosted instance is still running until the cutover (§8).

## 2. Decisions already made

**The backend stays Java.** No rewrite to TypeScript. Spring Boot + Java 21 is the most distinctive thing in the portfolio, and rebuilding 1,100 lines of working services to make the demo marginally easier is a bad trade. The demo instead reimplements only the derived logic it needs, in JS, inside the frontend.

This is a deliberate divergence from Prometheus, and worth being honest about: Prometheus's [ADR-0007](../../Dev/Prometheus/docs/adr/0007-client-side-domain.md) rejects exactly this architecture, on the grounds that computing the domain server-side while the demo computes it in the browser means "two implementations of the same rules, guaranteed to diverge, and the demo would stop being the app and become something that resembles it." That reasoning is correct. Atlas accepts the trade anyway, for reasons Prometheus didn't have: the Java implementation already exists and works, and it carries portfolio value that a Node CRUD layer would not. The divergence risk is real, and §4.5 is the mitigation — without those tests, this decision is a bad one.

> **Status:** held. The mitigation (§4.5 fixture contract) shipped and does its job — a JS test replays the committed seed against the recorded Java responses.

**SQLite everywhere.** Single user, one writer, a few thousand rows for a lifetime of workouts — Postgres is over-provisioned. SQLite means one container, one file, and `cp atlas.db` as the entire backup story. With no hosted instance there is no second dialect to support.

> **Status:** delivered (see §4.1).

**No user auth.** For self-hosted apps, auth belongs to the reverse proxy or the network (Tailscale, Caddy, Authelia), not the application. Same call Prometheus made. The `X-API-Key` on the Health Connect sync endpoint stays, because Atlas — unlike Prometheus — has a phone pushing data in.

> **Status:** delivered (see §4.3). **Correction to the plan:** the `X-API-Key` check never rode on Spring Security — it was already a plain `@RequestHeader` comparison inside `SyncController`. Dropping the security starter removed nothing that protected `/api/sync`; no filter migration was needed. This was recorded during review so nobody "fixes" it into a filter later.

**Atlas is not internet-facing.** This is a stated design constraint, not an omission. It goes in the README as a decision.

> **Status:** in the backend README quickstart.

**The demo is entirely static.** No serverless functions, no API keys, no backend of any kind — so nothing about it can go down. AI insight *generation* is therefore unavailable in the demo and says so (§4.7), which makes it the one feature that requires self-hosting. That's an asset rather than a gap: the demo exists to make people want to run the real thing.

> **Status:** delivered. Verified the demo bundle contains no axios and no network calls.

## 3. Explicit non-goals

- Rewriting the backend in TypeScript or any other language.
- Multi-user, accounts, or permissions.
- Making the app safe to expose publicly. Anyone who wants that puts a proxy in front of it.
- Keeping any deployed instance running.
- Making the demo a path into real use. It is a showcase: it ships seeded and resets to seeded, with no way to start from an empty state. Anyone who wants to use Atlas for real self-hosts it, where a fresh install is empty by definition. This also means no export/import is needed to bridge the two (§6).

> **Status:** all held. "No hosted instance running" is still pending only because the cutover waits for the home server (§8).

## 4. Workstreams

### 4.1 Postgres → SQLite

- **Spike first.** Add `sqlite-jdbc` and `hibernate-community-dialects`, boot the app, hit `/api/stats` and `/api/workout-logs/heatmap`. The SQLite dialect is community-supported, not core, and this runs on Spring Boot 4.1 / Hibernate 7. If it doesn't work cleanly, that invalidates this whole workstream — so find out in the first hour, before anything else is built on it.
- **Fix `WorkoutLog`'s ID strategy.** Uses a bare `@GeneratedValue`, i.e. `AUTO`, which Hibernate maps to a sequence. SQLite has no sequences. Every other entity uses `IDENTITY`; make this one match. Worth fixing regardless — it currently creates a stray sequence in Postgres.
- **Verify date handling.** SQLite has no date type. The repository is built on `BETWEEN :startDate AND :endDate`, which will rely on ISO strings sorting lexicographically. It should work; it's the most likely place for a silent wrong-results bug, so it needs tests (§4.5) rather than eyeballing.
- **Good news:** no native queries anywhere in the codebase, all JPQL. The query layer should port without changes.
- **Decide schema management.** `ddl-auto=update` is defensible for a single-user file database and is the cheap answer to today's blocker (prod runs `validate` with no migration files anywhere, so a fresh install has nothing to create tables from). Flyway is the disciplined answer. Pick one when starting.
- **Migrate personal data out of Neon** once, then let the Neon project go.

> **Status: delivered**, with details:
> - Spike passed in the first hour: the community `SQLiteDialect` works on Spring Boot 4.1 / Hibernate 7.4.1. It must be set explicitly (`spring.jpa.database-platform`) — auto-detection from JDBC metadata fails.
> - `WorkoutLog` fixed to `IDENTITY`. The spike surfaced the exact predicted failure (AUTO → table generator → separate-connection isolated work → `SQLITE_BUSY`). The stray Postgres sequence is gone.
> - `ddl-auto=update` chosen (§6). Hikari pool capped at 1 (SQLite is single-writer).
> - `BETWEEN` on ISO dates verified by tests and the fixture contract.
> - **Out-of-plan addition:** `AppSettingsSeeder` (from `atlas-generalization-todos.md`) — `app_settings` was never seeded by any code path and `ddl-auto=update` creates an empty table, so streaks/settings would 500 on a fresh install. Now seeded idempotently at startup. (The plan's §5 ordering put this fix later; it was required by Phase 1, so it shipped there.)

### 4.2 Secrets cleanup — do this first, independent of everything else

`application-local.properties` contains live credentials in plaintext: the Neon database password, the Gemini API key, and the sync API key. Rotate all three, remove the file from version control, and gitignore it. This is unrelated to the refactor and shouldn't wait for it.

> **Status: partially delivered, by decision.** The file is **not tracked** and was already covered by `.gitignore` — confirmed, nothing to remove. **Rotation was declined** by the owner (the keys stay live; the Neon password is needed for the cutover). The plan's "rotate all three" is therefore unresolved and deferred indefinitely. The Gemini key is still used to regenerate the demo seed's insight; the sync key is set via `.env` for docker compose.

### 4.3 Remove auth

Delete:

- Backend: `SecurityConfig`, `User`, `UserRepository`, `UserService`, `AuthController`, the `users` table, and `APP_CORS_ALLOWED_ORIGIN`.
- Frontend: `LoginView`, `stores/auth.js`, the router guard, and the Basic Auth interceptor in `services/api.js`.

Keep the `X-API-Key` check on `/api/sync`. **Note:** if `spring-boot-starter-security` is dropped entirely, that check needs to move to a plain servlet filter or interceptor — it currently rides on Spring Security's config.

Side effects, all good: first-run user creation stops being a self-hosting blocker, the demo no longer needs a login bypass, CORS disappears if the frontend is served from the same origin, and the password stops being stored in `localStorage` in plaintext.

> **Status: delivered.** All listed deletions done; `spring-boot-starter-security` + `-security-test` removed; tests dropped `@WithMockUser`. The `users` table is gone (no entity, nothing recreates it). See §2 correction: the X-API-Key migration note was moot — the check was always a plain controller header check.

### 4.4 Packaging for self-hosting

- Serve the built frontend from Spring Boot's `static/` so the API is same-origin and relative. This kills CORS and removes the `VITE_API_URL` build-time-baking problem, which today would force self-hosters to rebuild the frontend.
- One container plus a volume for the `.db` file. `docker-compose.yml` with a single service.
- Environment surface shrinks to roughly: `SYNC_API_KEY`, `GEMINI_API_KEY` (optional), `PORT`, database file path.
- README: quickstart, the not-internet-facing constraint, and "back up by copying the file."

> **Status: delivered.** Multi-stage `dockerfile` (node builds frontend → maven copies `dist/` into `static/` → JRE runtime). `docker-compose.yml` at the workspace root with a named volume, `ATLAS_DB_PATH=/data/atlas.db`. Env surface exactly as planned. SPA history-mode fallback implemented as a servlet filter (`SpaForwardFilter`) — the initial controller-pattern attempt had a bug (regex only guarded the first path segment, so asset requests got forwarded to `index.html` → MIME errors in the browser); the filter forwards only GET, non-`/api`, extension-free paths. Covered by `SpaServingTest`. Backup = copy the file, verified across a container restart.

### 4.5 The seed generator — one artifact, three jobs

Today `AtlasApplicationTests` is only `contextLoads()`. Nothing asserts any behaviour.

Rather than hand-writing a fixture, adopt the approach from Prometheus's ADR-0009 and **generate the seed by driving the Java backend**, since the Java is Atlas's real domain. One generator emits two files:

- **`demo-seed.json`** — the rows, produced by driving the real services the way the UI does: create workout types, log a year of workouts, add body metrics, create goals. This is what the demo loads into browser storage (§4.6).
- **`expected-derived.json`** — Java's computed streaks, stats, heatmap and goal progress for that exact dataset.

The demo ships the first. A JS test loads the first, runs the ported logic, and asserts it equals the second.

**Drive it through MockMvc against the real controllers**, not the service layer directly. Then `expected-derived.json` holds the actual HTTP response bodies for `GET /api/stats`, `/workout-logs/streaks`, `/workout-logs/heatmap` and `/goals` — which is precisely what the demo adapter must reproduce. The fixture becomes a recorded API contract rather than an internal snapshot, and it documents the API as a side effect.

Commit both files into the frontend repo rather than generating them in CI. The demo build then needs only Node, the seed is reproducible, and a changed seed arrives as a reviewable diff instead of appearing silently. Regenerate deliberately with a Maven command.

This collapses what were two separate workstreams. It also buys ADR-0009's real property: the seed cannot rot silently against the schema, because it is generated from the live entities — and when the Java logic changes, both files change and the JS test fails loudly rather than the demo quietly showing different numbers.

**Two hard prerequisites:**

1. **Time must be injectable.** `calculateStreaks()`, `computeEta()` and `computePace()` all call `LocalDate.now()` directly. Expected outputs computed against a moving clock change daily and the test flakes within a day. Inject a `Clock` into those services first — small, but blocking.
2. **The seed's dates must be relative.** A seed generated with 2026 dates looks abandoned a year later: last workout months ago, streak zero, dashboard bare. The generator emits offsets from a reference date; the demo materializes them against today at load time. Use the same reference date in the fixture test.

   **Shift by whole weeks — multiples of 7 — never by an arbitrary number of days.** `calculateStreaks()` buckets workouts by week via `previousOrSame(SUNDAY)` and requires ≥ target workouts per week. Shift every date by four days and the day-of-week alignment moves: workouts regroup across week boundaries and the computed streak changes, so a seeded twelve-week streak becomes some other number depending on which day the visitor opens the demo. Anchor materialization to the most recent Sunday and shift in 7-day multiples; week bucketing is then identical to what Java computed, which is what keeps `expected-derived.json` valid at runtime and not merely in the test. The day-of-week distribution chart depends on this too — otherwise it rotates.

**Honest limit:** this proves the two implementations agree *on one dataset*, not universally. Prometheus has one engine, so agreement is structural and there is nothing to prove. Atlas gets fixture-based agreement, which is weaker. Adequate here, but not parity — see §2.

Coverage priority, by intricacy: `calculateStreaks()`, goal progress/ETA/pace, monthly/yearly aggregates, heatmap grouping.

**This has to happen before §4.6.** Porting first and testing afterwards means having nothing trustworthy to compare against — and here it also means having no seed to ship.

> **Status: delivered**, with details and deviations:
> - `Clock` injected into `WorkoutLogService`, `GoalService`, **and `StatsController`** (which had its own raw `LocalDate.now()` for the year/month defaults — the plan didn't list it, but it would flake the fixture the same way).
> - Generator (`SeedGenerator`, `@Tag("seed-generator")`, excluded from default test runs) drives MockMvc; fixture test (`SeedFixtureTest`) replays the committed seed and asserts equality. Both files committed to the frontend repo at `src/demo/`. Regenerate deliberately: `mvnw test -Dtest=SeedGenerator -Dsurefire.excludedGroups=`.
> - Reference date `2026-08-02` (a Sunday); all dates stored as day offsets; demo materializes against the visitor's most recent Sunday in whole-week shifts.
> - **Deviation — goals are seeded via the repository, not the API.** The plan says "drive it through MockMvc against the real controllers." Goal `createdAt`/`startValue` are server-stamped (`now`, latest metric), so creating goals through the API makes every goal "created today" and ETA/pace are dead — the plan's own §4.6 requires "Pace and ETA are both computed, so both need a goal that exercises them." Goals are therefore inserted via repository with backdated values; the `GET /api/goals` body is still recorded and asserted. Trade-off: the goal POST path is never exercised by the fixture — acceptable, and recorded in `SeedReplayer`.
> - **Deviation — the Gemini insight call retries.** The plan (§4.7) says "call Gemini for real, **once**". The free tier rate-limits heavily (429/503), so `maybeFreezeInsight` retries up to 10× with 15s backoff, logging each attempt. Deviation acknowledged in the generator's javadoc.
> - Seed was regenerated with the real Gemini key; the demo carries genuine frozen model output ("Consistent recomposition, fat ticking down"), not a hand-written imitation.
> - **Seed metric cadence changed after review:** body metrics are logged ~monthly (every 4 weeks, 20 rows), not weekly — weekly sampling made the metric charts noisy. All offsets remain whole-week multiples, so the runtime fixture property is preserved.

### 4.6 The demo build

The frontend already has a clean seam: eight service files, 121 lines total, all funnelling through `services/api.js`. The demo swaps that module at build time via a Vite alias, so both builds ship from one codebase.

- **Demo data adapter** implementing the ~18-endpoint surface against browser storage. `localStorage` is likely sufficient — the whole dataset is tens of kilobytes against a 5 MB budget — and avoids IndexedDB's async plumbing. Decide when starting.
- **Port the derived logic to JS**: streaks, heatmap grouping, stats aggregates, goal progress/ETA/pace. Roughly 400–500 lines. Diff every one against the §4.5 fixture; the numbers must match exactly.
- **Visitor isolation is free.** Browser storage is per-visitor: every new visitor gets the pristine seed and nobody can affect anyone else. No scheduled resets or session namespacing to build.
- **Seed data comes from the §4.5 generator**, loaded into browser storage on first run with its relative dates materialized against today. Version it, so improving the seed later pushes new data to returning visitors instead of leaving them on a stale copy.
- **Seed quality matters more than it looks.** This is the only data anyone will ever see the app render. Sparse or random data will make correct charts and streak logic look broken. What the generator should produce:
  - A year or more of workouts with realistic gaps — including a broken streak and a current live one, since streaks are the dashboard's headline number.
  - Enough workout-type variety that the distribution donut and day-of-week chart say something, with one clearly favoured type.
  - Body metrics that trend somewhere interesting — a plateau or a reversal, not a clean line — so the AI insight has something worth saying.
  - Goals in mixed states: one on track, one behind pace, one achieved. Pace and ETA are both computed, so both need a goal that exercises them.
  - Durations spread widely enough for the histogram to have shape.
- **"Reset demo data"** in settings. Nice to have, not critical: it only matters for a returning visitor seeing their own earlier edits, since visitors are already isolated from each other.
- **A demo banner** — "demo, data stays in your browser" — linking to the self-hosting instructions.

> **Status: delivered.** Vite alias (`@/services/api` → `demoApi.js`) active only in `--mode demo`; both builds verified (demo bundle has no axios; self-host bundle has no demo code). Ported logic in `src/demo/derived.js` passes the fixture contract byte-for-byte (same doubles, same week bucketing). localStorage seeded with `seedVersion` versioning. Banner links to the backend repo README. "Reset demo data" in Settings via dynamic import (kept out of the self-host bundle).
> - **Post-review change:** clicking *regenerate insight* in the demo no longer replaces the seeded insight or fires a success toast — it opens a warning modal (`InsightGateModal`) explaining generation needs self-hosting. The adapter's regenerate endpoint now rejects 403 rather than faking a response.

### 4.7 AI insights in the demo — read-only, no proxy

**Decided: no serverless function, no Gemini key anywhere near the demo.** The insight card renders normally from seeded text; only the *regenerate* action is gated, with a message saying insight generation requires self-hosting with your own Gemini API key.

This keeps the demo **entirely static** — no backend of any kind, so nothing can go down and there is no live API key to rate-limit, meter or have abused from a public page. It also turns the one gated feature into a concrete reason to self-host, which is what the demo is for.

The consequence: **seeded insight text is now the only way anyone sees this feature**, so it carries more weight than the rest of the seed. Have the generator call Gemini for real, once, with your own key while generating (§4.5), and write the output onto the historical measurements via the existing `insight_text` and `insight_generated_at` columns. The demo then shows genuine model output, frozen — not a hand-written imitation of it. Honest, because the UI states plainly that regeneration is unavailable.

Gate the button, don't hide it: a visitor should see that the feature exists.

> **Status: delivered** (see §4.5 deviation for the retry; §4.6 for the modal). Seeded text is genuine Gemini output. Button visible, gated.

## 5. Sequencing

| Phase | Work | Status |
|---|---|---|
| 0 | Rotate secrets (§4.2); spike the SQLite dialect (§4.1) | ✅ Done. Secrets: gitignore-only (no rotation, by decision). Spike: passed. |
| 1 | SQLite migration, fix the ID strategy | ✅ Done (+ `AppSettingsSeeder`). |
| 2 | Remove auth (§4.3) | ✅ Done. |
| 3 | Inject `Clock`; seed generator and characterization tests (§4.5) | ✅ Done (+ `StatsController` clock; goals via repository). |
| 4 | Single-container packaging (§4.4) | ✅ Done (+ SPA fallback filter). |
| 5 | Demo adapter, JS port, demo build (§4.6) | ✅ Done. |
| 6 | Demo polish, README and portfolio framing | ✅ Done (banner, gating modal, READMEs, `project-context.md`, screenshots pending). |

Phases 1–4 leave a working self-hosted app even if 5–6 stall. That's the natural stopping point if the work gets interrupted.

> As it happened, everything through 6 shipped. The "working app even if interrupted" property was validated at the Phase 4 checkpoint (self-hosted build ran standalone before any demo work).

## 6. Open decisions

To resolve when starting, not now:

- `ddl-auto=update` or Flyway? → **`ddl-auto=update`** (chosen).
- `localStorage` or IndexedDB for the demo? → **`localStorage`** (chosen).
- JSON export/import? → **Skipped** (SQLite file is the backup; demo is showcase-only). Not built.

## 7. Main risk

The SQLite dialect being awkward on Spring Boot 4.1 / Hibernate 7. Everything else here is deletion, packaging, or a contained frontend port. Phase 0's spike exists specifically to surface this before any effort is committed on top of it.

> **Risk retired.** The dialect works; the one real surprise (AUTO → table generator → `SQLITE_BUSY`) was the already-known ID-strategy bug, fixed as planned. The actual post-plan risks that surfaced were: the SPA fallback asset-forwarding bug (browser-only, caught via a dedicated test) and free-tier Gemini rate limiting (handled by the retry loop).

## 8. Still pending

- **Cutover** — migrate personal data out of Neon into SQLite, then decommission the hosted instance. **Blocked by decision:** the owner's home server isn't ready; the hosted version stays in use until it is. Migration mechanism planned: a `tools/` script (Python, `pg8000` + built-in `sqlite3`, preserves explicit IDs, skips the `users` table) run once at cutover. The Neon password is still required for this.
- **Generalization to-dos** (`atlas-generalization-todos.md`) — not started, need the planning pass described by the owner (fit existing data, `/sync` for other devices, provider-agnostic insights, units, webhook app open-sourcing). Note: item #4 (app_settings bootstrap) is already done via `AppSettingsSeeder`.
- **Screenshots** — capture instructions ready in `docs/screenshots/`; images to be added.
- **Commits** — done, superseded by `atlas-monorepo-plan.md`. Both repos were committed, then consolidated into one (`atlas-backend`, `server/` + `ui/`), verified via a clean-clone acceptance test, and pushed to the `selfhost` branch. CI (`mvnw test`, `npm test` + both builds, `docker build`) runs on every push. `master` is untouched in both repos pending cutover.
