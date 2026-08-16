# Atlas — Monorepo Consolidation Plan

**Status: COMPLETE.** Written 2026-08-11; executed and verified the same day.
Sits between the completed self-hosting work
([atlas-selfhost-plan.md](atlas-selfhost-plan.md)) and the
[generalization to-dos](atlas-generalization-todos.md), and before the cutover
described in the self-host plan §8.

> **Naming note, 2026-08-15.** This document refers throughout to `atlas-backend`
> and `atlas-frontend`, which were the repository names at the time. Since then:
> `atlas-backend` was **renamed to `atlas`** (the "backend" half stopped being
> true once the frontend was grafted in), its `selfhost` branch was merged into
> `master` and deleted, and the `atlas-frontend` repository was deleted outright.
> The narrative below is left as written, as a record of what was decided when.

> **Status:** all of §5 shipped on `atlas-backend`'s `selfhost` branch, unpushed.
> `master` in both repos is untouched; the hosted Render/Vercel deployment still
> serves the pre-refactor app. §5.7's clean-clone acceptance test passed after one
> real bug it caught (below). `atlas-frontend`'s `selfhost` safety branch has been
> deleted — its content is preserved in `atlas-backend`'s history via the subtree
> graft, confirmed by the acceptance test.
>
> **What the acceptance test actually caught:** the first clean-clone run of
> `SeedFixtureTest` failed with "Run the seed generator first" — not because the
> seed path fix (§5.5) was wrong, but because the edit to
> `AbstractSqliteIntegrationTest.java`, along with edits to `project-context.md`
> and `server/README.md`, had been made in the working tree but never `git add`ed
> before the step-4 commit. The commit silently didn't contain what its own
> message described. A second clean clone after a fix-up commit passed cleanly:
> 7 backend tests (SQLite integration, `FreshInstallTest`, `SpaServingTest`,
> `SeedFixtureTest`), 15 frontend tests, a demo build with no `axios` in the
> bundle, and a full Docker Compose cycle — build, run, create a workout log,
> confirm `AppSettingsSeeder` seeded `app_settings`, confirm the SQLite
> `BETWEEN`-on-ISO-string heatmap query and the workout-type join both return
> correct data, restart the container, confirm both survive in the named volume.
> This is exactly the failure mode §1 predicted for the split-repo build, just
> one seam over: a change that's right in isolation but silently incomplete
> because nothing forced it to be checked as a whole. One clean-clone run is
> what forced it.
>
> One unrelated finding during verification, not a defect in this plan: the
> local machine's port 8080 is already bound by qBittorrent's WebUI, which
> `compose.yaml`'s existing `${ATLAS_PORT:-8080}` override handles without any
> change needed — worth knowing if `docker compose up` on this machine ever
> looks like it started but doesn't answer on 8080.
>
> Not done here, deliberately out of scope per §4: pushing the branch (§6 step
> 7, gated on Render's auto-deploy being turned off first), the GitHub rename
> to `atlas`, demo hosting (§7), and CI (§7).

## 1. Goal

Atlas is two repositories — `atlas-backend` (`aio-fitness/`) and `atlas-frontend`
(`aio-fitness-frontend/aio-fitness-frontend/`) — that the self-hosting refactor
turned into a single deployable artifact. Consolidate them into one repository,
matching the layout Prometheus already uses.

The motivation is not tidiness. It is three concrete problems, in descending order
of how much they cost:

**The self-hosted build cannot be reproduced from a clone.** [dockerfile](aio-fitness/dockerfile)
lives in the backend repo but its build stages `COPY` from
`aio-fitness-frontend/aio-fitness-frontend/`, and [docker-compose.yml](docker-compose.yml)
sets `context: .` at the workspace root while being tracked by neither repo. The
build works only because the two clones happen to sit side by side in this
directory. Someone following the README — clone `atlas-backend`, `docker compose up` —
gets no compose file and a dockerfile whose paths do not exist. Self-host plan §4.4
is marked delivered; strictly, it is not. This plan is what makes it true.

**The seed contract crosses the repo boundary.** `atlas.seed.dir` defaults to
`../aio-fitness-frontend/aio-fitness-frontend/src/demo`
([AbstractSqliteIntegrationTest.java:34](aio-fitness/src/test/java/com/joaosousa/atlas/AbstractSqliteIntegrationTest.java#L34)),
so `SeedGenerator` writes `demo-seed.json` and `expected-derived.json` into a
*different working copy*, where the JS fixture test consumes them. Self-host plan
§4.5 exists specifically to stop the Java and JS implementations diverging silently —
a risk the plan knowingly took on against Prometheus's ADR-0007. Split across two
repos, the change that regenerates the fixture cannot be atomic and no single CI
run can enforce it. The mitigation is weaker than it was designed to be.

**Some files have no home at all.** `docker-compose.yml`, this plan, the self-host
plan, the generalization to-dos and the compose `.env` are tracked by neither
repository. They exist only on this machine.

## 2. Why now, rather than after the to-dos

The generalization to-dos are mostly cross-cutting: units (#5) is frontend display
plus backend storage, the exercise-type vocabulary (#2) is backend entities plus
seed generation plus frontend rendering, provider-agnostic insights (#1) is a
backend interface plus a settings UI. Each of those, done today, is a pair of
commits in two repos that cannot be reviewed or reverted together. Done after this
plan, each is one commit.

Item #2 in particular *requires regenerating the seed* against the newly seeded
vocabulary. That drives the exact backend-generates / frontend-consumes handoff
described above. Doing it while the repos are split is the worst case of the
coupling problem, on the one seam the whole demo architecture rests on.

And the build should be verifiable before feature work is stacked on it.

## 3. Decisions

**`atlas-backend` is the surviving repository.** The frontend is grafted into it,
not the other way around and not into a new repo. It keeps the existing GitHub URL —
which the demo banner links to via `VITE_SELF_HOST_URL` — along with the history
and any issues. Rename it to `atlas` on GitHub at cutover; GitHub redirects the old
URL, so the banner keeps working either way.

**Frontend history is preserved, via `git subtree add`.** Not a fresh copy of the
files. The frontend's commits graft in under the `ui/` prefix. Consequence, accepted:
`git log ui/` shows a path change partway back through history, and `--follow` is
needed to trace individual files across the move.

**Directories are `server/` and `ui/`.** Matches Prometheus. Retires the `aio-fitness`
name, which predates the project being called Atlas, and kills the doubled
`aio-fitness-frontend/aio-fitness-frontend/` nesting. The Java package is already
`com.joaosousa.atlas` and does not move.

**All of it happens on the `selfhost` branch.** `master` in both repos stays exactly
as deployed. Render and Vercel keep serving the hosted app that is still in daily
use, and nothing here can disturb that.

**`atlas-frontend` stays alive until cutover.** Its `master` is what Vercel serves
today. It gets archived when the hosted instance is decommissioned, not before. Its
local `selfhost` branch is kept as a safety copy until §5.5 passes.

## 4. Non-goals

- **Unifying the build tooling.** No npm workspace driving Maven, no Turborepo, no
  root `package.json`. Two toolchains stay two toolchains; the Dockerfile is the
  only thing that needs to know about both.
- **Any behaviour change.** This is a move plus path corrections. If a test's
  expected output changes, something has gone wrong.
- **Any generalization to-do.** Tempting to fix things in passing while touching
  every file. Don't — a pure-move commit is reviewable and a mixed one is not.
- **Deleting or archiving `atlas-frontend`.** That is cutover work.
- **CI.** Worth having, and easier once this lands, but out of scope here (§7).

## 5. Workstreams

### 5.1 Target layout

```
atlas/
  Dockerfile          # moved from aio-fitness/dockerfile, paths corrected
  compose.yaml        # moved from the workspace root, now tracked
  .dockerignore       # merged: server and ui build artifacts
  README.md           # quickstart, not-internet-facing constraint, backup story
  server/             # the Spring Boot app (was aio-fitness/)
  ui/                 # the Vue app (was aio-fitness-frontend/aio-fitness-frontend/)
  docs/               # plans, screenshots, project context
```

### 5.2 Move the backend into `server/`

`git mv` everything except `.git` into `server/`. Commit on its own — a pure rename
commit that Git records as such, keeping the diff reviewable.

Watch for: `.mvn/` and the Maven wrappers (`mvnw`, `mvnw.cmd`) move with the app,
since Maven is invoked from `server/`; `atlas.db` and `target/` are gitignored and
must stay untracked through the move; `atlas.iml` and `.idea/` are IDE noise that
should be gitignored rather than relocated.

### 5.3 Graft the frontend into `ui/`

Add the local frontend clone as a remote and `git subtree add --prefix=ui` from its
`selfhost` branch — the branch, not `master`, because `selfhost` carries the demo
build and the auth removal.

Watch for: `dist/`, `node_modules/`, `.eslintcache` and the stray `preview.log` /
`preview.err.log` must not come across; confirm they are ignored before the subtree
add, not after.

### 5.4 Root build files

- **Dockerfile** to the root. The frontend stage's `COPY` paths become `ui/`, the
  backend stage's become `server/`. The `COPY --from=frontend /frontend/dist
  ./src/main/resources/static` line is unchanged — it targets a path inside the
  build stage, not the repo.
- **compose.yaml** to the root, tracked. `context: .` is finally correct rather than
  accidentally correct. Keep the env surface exactly as it is: `SYNC_API_KEY`,
  `GEMINI_API_KEY`, `ATLAS_DB_PATH`, `PORT`, the named volume.
  **Changed 2026-08-16, after this plan completed:** generalization to-do §1 removed
  `GEMINI_API_KEY` — the insight provider is configured in `app_settings` via the UI.
- **`.gitignore` / `.gitattributes` / `.dockerignore`**: both repos have all three.
  Merge to root where the rule is global (build output, IDE files, line endings) and
  leave path-specific rules in `server/` and `ui/`. Nested ignore files are fine and
  clearer than one root file that has to know both trees.
- **`vercel.json`** stays in `ui/`. It configures a Vercel project whose root
  directory becomes `ui/` if the demo is hosted there (§7).

### 5.5 Fix the cross-repo seams

These are the changes that are not pure moves, and each one is a place where the
split repo left something pointing outside itself:

- **`atlas.seed.dir`** default becomes `../ui/src/demo` — relative to `server/`,
  where Maven runs. One line, in
  [AbstractSqliteIntegrationTest.java:34](aio-fitness/src/test/java/com/joaosousa/atlas/AbstractSqliteIntegrationTest.java#L34).
  It is already a `@Value` with an overridable default, so nothing else changes.
- **Seed regeneration command** in the backend README and `SeedGenerator`'s javadoc —
  the documented `mvnw` invocation now runs from `server/`.
- **README paths and split.** One root README is the self-hosting quickstart and the
  portfolio front door. Whatever is genuinely toolchain-specific goes to
  `server/README.md` and `ui/README.md`.
- **`VITE_SELF_HOST_URL`** in `.env.demo` — still correct today, and still correct
  after a GitHub rename thanks to redirects. Update it when the rename happens, not
  now.
- **Anything referencing the old sibling layout** in `project-context.md` and the
  docs.

### 5.6 Docs consolidation

`docs/` takes this plan, the self-host plan, the generalization to-dos, the webhook
sync spec, the PRD and the existing screenshots directory. `project-context.md`
currently lives in the frontend and describes the whole system; it belongs at the
root or in `docs/`. Prometheus uses a root `CONTEXT.md` — worth matching, but a
naming question rather than a structural one (§7).

Note that [homelab-setup-plan.md](homelab-setup-plan.md) is workspace-level, not
Atlas-level. It should not move into the Atlas repo.

### 5.7 Verification — the acceptance test

The whole point of this plan is a build that works from a clone, so the check is
exactly that, and nothing weaker:

1. `git clone` the branch into an empty directory somewhere with **no sibling Atlas
   directories present** — this is the condition today's build silently depends on.
2. `docker compose up --build` from that clone, with a fresh `.env`.
3. Load the app, exercise a workout log, the dashboard streak and the heatmap.
4. Restart the container; confirm data survives in the volume.
5. `./mvnw test` from `server/` — the SQLite integration tests, `FreshInstallTest`,
   `SpaServingTest` and `SeedFixtureTest` must pass, the last of which proves the
   relocated seed path resolves.
6. `npm run test` and `npm run build:demo` from `ui/`; confirm the demo bundle still
   has no axios and no network calls.

Only after all six: delete the frontend repo's local `selfhost` branch safety copy.

## 6. Sequencing

| Step | Work | Why here |
|---|---|---|
| 1 | Confirm ignore rules cover `dist/`, `node_modules/`, `target/`, `atlas.db`, logs, IDE files | Cheapest before the move; a mistake here means rewriting the branch. |
| 2 | `git mv` backend into `server/` (§5.2) | Pure rename, own commit. |
| 3 | `git subtree add --prefix=ui` (§5.3) | Grafts history; own commit. |
| 4 | Root Dockerfile, compose.yaml, ignore files (§5.4) | The build is broken between steps 2 and 4 — expected, and why 2–4 land together. |
| 5 | Seam fixes: seed dir, READMEs, docs (§5.5, §5.6) | Small, and the seed path must be right before step 6 can pass. |
| 6 | Clean-clone verification (§5.7) | The acceptance test. |
| 7 | Push the branch | Only after Render auto-deploy is off. |

Steps 2–6 are one sitting; the tree does not build in the middle of them.

## 7. Open decisions

- **Demo hosting.** Nothing hosts the demo today. **Decided: a Vercel project with
  Root Directory set to `ui/`.** Prometheus's own `vercel.json` is a two-line root
  config (`buildCommand: npm run build:demo`, `outputDirectory: dist-demo`) — that
  works there only because Prometheus is a single npm project at the repo root.
  Atlas isn't: `ui/` is the only npm project, sitting next to a Java `server/`, so
  the split has to be declared somewhere. Root Directory is a dashboard setting
  (Project Settings → General → Root Directory = `ui/`), not something
  `vercel.json` can express, so it is configured once when the Vercel project is
  created rather than committed. With it set, Vercel treats `ui/` as the project
  root: `ui/vercel.json` (already in place, §5.4) supplies the demo rewrite rule,
  and the project's Build Command becomes `npm run build:demo` with output
  `ui/dist`. The alternative — a root-level `vercel.json` with a `cd ui &&` build
  command and `outputDirectory: ui/dist` — was considered and rejected: it works
  without touching the dashboard, but has Vercel `cd` out of its own detected
  project root, which is more fragile than the setting the UI exists for. Create
  the project at cutover, not now — but note the current Vercel project is
  attached to `atlas-frontend` and dies when that repo is archived.
- **Rename the GitHub repo to `atlas` now or at cutover?** Cutover is the safer
  moment: Render is watching `atlas-backend` until then, and one fewer moving part
  during the migration is worth more than an accurate repo name for a few weeks.
- **`project-context.md` or `CONTEXT.md`?** Prometheus uses the latter at the root.
- **CI.** A GitHub Actions workflow running `mvnw test` and `npm test` on push is
  the thing that converts "the fixture contract exists" into "the fixture contract
  is enforced". It is the natural follow-up to this plan and arguably the point of
  it. Deliberately not in scope here; schedule it immediately after.

## 8. Risk and rollback

Low risk, because nothing is published. Both `selfhost` branches are local and
unpushed, `master` is untouched in both repos, and the hosted app that is still in
daily use is driven entirely by `master`. If the graft goes wrong the recovery is
`git branch -D selfhost` in the backend clone and start again — the frontend's own
`selfhost` branch still holds the work until §5.7 passes.

The one thing that is awkward to undo is the subtree graft *after* it is pushed,
which is the argument for doing the whole of §5 before the push rather than pushing
the current two branches first.

The real risk is subtler: a pure-move refactor invites opportunistic fixes, and this
one touches every file in both projects. A mixed commit here is unreviewable and, if
something breaks, indistinguishable from a bad move. Keep §4's "no behaviour change"
literal — the to-dos are next, and they are the place for all of it.
