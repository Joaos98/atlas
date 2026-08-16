# Spec: Secrets Handling — Close the Leak Paths, Then Rotate

**Status:** Implemented 2026-08-16 — every code change in §3 has landed. **Rotation (§3.4) is
outstanding and is the owner's to do**; it needs credentials only he can issue.
**Date:** 2026-08-12
**Implements:** `atlas-generalization-todos.md` §7
**Supersedes:** [`webhook-sync-deduplication-spec.md`](webhook-sync-deduplication-spec.md) §10.1 (resolved) and §10.2 (partially resolved — see §1.1); `atlas-selfhost-plan.md` line 72's "remove from version control and gitignore it" (done)
**Component:** `server` — `SyncController`, `application-local.properties`, `pom.xml`, `.dockerignore`

---

## 1. Problem

The to-do records three claims. One is true, one is stale, and one is already fixed —
and the leak path that actually matters is not on the list.

### 1.1 What is still true, and what is not

**"Credentials in the repo" — not via the properties file.** `application-local.properties`
is ignored at [`server/.gitignore:6`](../server/.gitignore), and running
`git log --all -S"<secret>" --oneline` for each of the three values returns empty across
`master`, `selfhost`, and both `origin/*`. Nothing has been committed, so there is no
history to rewrite and no force-push to coordinate. The self-host plan's ask on line 72 was
carried out during the refactor; only its "rotate all three" half is outstanding.

**But the sync key is in the docs, and the docs are committed — see §1.3.** That is the
half of this premise that survives, by a different route than the to-do described.

**"`ddl-auto=update` against production Neon" — stale.** The file now reads
`spring.jpa.hibernate.ddl-auto=validate` (line 5). The dedup spec's §10.1 BLOCKING
condition is resolved. It does still point at the production `neondb` database rather
than a branch, which under `validate` is a read-shaped risk only, and dies at cutover.

**"`SyncController` logs the key in full at INFO" — true.**
[`SyncController.java:30`](../server/src/main/java/com/joaosousa/atlas/controller/SyncController.java#L30):

```java
log.info("Sync API key loaded: [{}] (length={})", syncApiKey, syncApiKey != null ? syncApiKey.length() : 0);
```

This runs on every startup, in every environment, including the Render deployment serving
the app today.

### 1.2 The leak path the to-do missed — the build artifact

Gitignoring the file keeps it out of the repository. It does not keep it out of anything
Maven or Docker builds, because both read the working tree, not the index:

- `mvn package` copies `src/main/resources/**` wholesale. Verified on the current tree:
  `target/classes/application-local.properties` exists, and
  `unzip -l target/atlas-0.0.1-SNAPSHOT.jar` lists
  `BOOT-INF/classes/application-local.properties` (496 bytes).
- The [`Dockerfile`](../Dockerfile) does `COPY server/src ./src`, and
  [`.dockerignore`](../.dockerignore) excludes only `.git/`, `.idea/`, `*.iml`,
  `server/target/`, `ui/node_modules/`, `ui/dist/`. The file is copied in, then baked
  into `app.jar`, which is copied into the final runtime image.

So the Neon password, the Gemini key and the sync key are inside every `atlas:local`
image and every locally built jar. Spring does not *load* the file unless profile `local`
is active — this is disclosure, not misconfiguration — but it is disclosure that travels
with the artifact, which is exactly what would go wrong the first time an image is pushed
to a registry or a jar is handed to someone. A secondary hazard follows from the same
fact: a container started with `SPRING_PROFILES_ACTIVE=local` would silently point itself
at production Neon.

### 1.3 The sync key is quoted verbatim in the documentation

A sweep for the three literal values across the whole tree found the sync key in prose, in
two files that describe the problem:

| File | Line | Git status |
|---|---|---|
| `docs/atlas-generalization-todos.md` | 192 (the §7 history block) | **tracked**, currently modified |
| `docs/webhook-sync-deduplication-spec.md` | 543 (§10.2) | untracked, clearly destined to be committed |

Neither has entered history yet — which is why the `-S` search above is clean, and why this
is still cheap to fix. But `atlas-generalization-todos.md` is a tracked file with staged-in
changes, so **the next commit touching it writes the live sync key into git permanently**,
at which point the finding stops being a working-tree problem and becomes a history-rewrite
problem. The dedup spec is untracked today only because it has not been committed yet.

The Neon password and Gemini key do not appear in any document; only the sync key was
quoted. This does not change the ordering in §2 — it adds a step ahead of everything, since
it is the only item with a deadline attached to it.

**Fix:** replace the literal in both files with a redaction (`aio-960b…`, or just "the sync
API key") before the next commit. No history work required if done first. Note that this
spec deliberately does not quote the value either.

### 1.4 Exposure inventory

| Secret | Where it sits | Who can see it today | Dies at cutover? |
|---|---|---|---|
| Neon DB password | working tree, local jar, local image, Render env | local disk; Render dashboard access | **Yes** — Neon is decommissioned |
| Gemini API key | working tree, local jar, local image, Render env | local disk; Render dashboard access | No — carried into self-hosted config |
| Sync API key | working tree, local jar, local image, Render env, `../.env`, **application logs**, the phone webhook app | everything above **plus anyone with Render log access or a log drain** | No — the phone app authenticates with it |

### 1.5 Only one of these is actively leaking

The working-tree file and the build artifacts are local. The log line is not: it writes
the sync key — the only thing protecting `POST /api/sync` — into Render's retained log
stream on every restart of the app that is still in daily use. That asymmetry sets the
ordering below.

---

## 2. Solution

Close the paths that keep re-exposing the secrets, *then* rotate. Rotating first would
mint new secrets into the same three holes — a jar that still bundles them, an image that
still bundles them, and a log line that still prints one of them.

Five changes, in order: scrub the docs; delete the log line; stop shipping the file; rotate;
delete the file. The first is done (§3.0). The next two are self-contained and testable. The
fourth has a coordination cost. The fifth is cutover work.

---

## 3. Changes

### 3.0 Scrub the sync key from the docs — DONE 2026-08-12

Both occurrences from §1.3 now read "value redacted 2026-08-12; it was quoted in full here"
in place of the literal, and the dedup spec's §10.2 carries a pointer to this document.
Done ahead of the rest because it was the only item with a deadline — the next commit
touching `atlas-generalization-todos.md` would have made it permanent. This is documentation
only; it ships no code and does not touch the cutover freeze.

### 3.1 Delete the startup log line — DONE 2026-08-16

The freeze this was held behind (§5) expired with the cutover. Line and now-unused `Logger`
import both removed. §4's verification 4 was run and confirms the premise: with `SYNC_API_KEY`
unset the context fails at startup with `Could not resolve placeholder 'SYNC_API_KEY' in value
"${SYNC_API_KEY}" <-- "${app.sync.api-key}"`, so the line's diagnostic value really was zero.

Remove [`SyncController.java:30`](../server/src/main/java/com/joaosousa/atlas/controller/SyncController.java#L30)
outright. Do not replace it with a masked or length-only variant: `app.sync.api-key` is
`${SYNC_API_KEY}` with **no default** ([`application.properties:13`](../server/src/main/resources/application.properties#L13)),
so a missing key already fails the context at startup with a clear message. The line's
diagnostic value is zero and its `Logger` import becomes unused.

### 3.2 Constant-time comparison — TAKEN 2026-08-16

Swapped for `MessageDigest.isEqual(...)` on exactly the reasoning below: the file was already
open. The null check moved into the guard, since `isEqual` cannot take a null argument the way
`equals` could — a missing header now short-circuits instead of throwing.



`syncApiKey.equals(apiKey)` (line 36) is not constant-time. On a LAN-only, single-user app
this is theoretical, and the honest reason to change it is that
`MessageDigest.isEqual(a.getBytes(UTF_8), b.getBytes(UTF_8))` is a one-line swap in a file
already being edited, not that the timing channel is exploitable here. Take it or leave it;
it is not a blocker.

### 3.3 Stop shipping `application-local.properties` in build outputs — SUPERSEDED by §3.5

Not implemented, deliberately. §3.5 ran in the same pass and deleted the file outright, so
both edits below would configure exclusions for a path that no longer exists — dead config
that reads as though a secrets file is still expected. The durable guard is §4's regression
test instead, which fails if the file returns to the build output by any route, including
ones these two edits would not have covered. Verified after deletion: a fresh `mvn package`
jar contains only `BOOT-INF/classes/application.properties`.

Kept as written for history; reinstate both if a local profile is ever reintroduced.

Both layers, because they fail independently:

**`.dockerignore`** — add `server/src/main/resources/application-local.properties`. Fixes
the image.

**`pom.xml`** — exclude it from resource filtering:

```xml
<resources>
  <resource>
    <directory>src/main/resources</directory>
    <excludes>
      <exclude>application-local.properties</exclude>
    </excludes>
  </resource>
</resources>
```

Fixes any local `mvn package`, including one whose jar is copied somewhere by hand. Note
this makes the local profile un-runnable from a *packaged* jar; it stays available from the
IDE and `mvn spring-boot:run`, which is the only way it is used. If that turns out to matter,
the alternative is to move the file out of `src/main/resources` entirely to a gitignored
`config/` directory next to the project, which Spring picks up ahead of the classpath — a
cleaner end state that §3.5 makes moot anyway.

### 3.4 Rotate — per secret, with the sequencing each one needs

**OUTSTANDING as of 2026-08-16 — the owner's to do.** Its precondition is met: §3.1 has landed
and §3.3's intent is satisfied by §3.5, so the holes are closed and rotating no longer mints
new secrets into them. Restating the three against post-cutover reality:

| Secret | Now | Action |
|---|---|---|
| Neon password | Database deleted 2026-08-15 | **Nothing to do** — the recommendation below ("skip, let cutover retire it") is what happened |
| LLM provider key | Still live, was in the deleted file, quota/billing exposure | **Revoke and reissue.** No coordination — see the note below on where the new one goes |
| Sync API key | Still live, guards `POST /api/sync`, shared with the phone app | **Rotate**, following the four-step order below |

Two things the original text could not know:

- **The sync key's blocker is gone.** §5 held its rotation until the Render deployment was
  decommissioned, so a new key would not be written straight back into retained logs. Render
  was deleted 2026-08-15 and the log line no longer exists. It is now unblocked — but the
  phone-app coordination cost below is unchanged, and the delta warning still applies: a sync
  rejected with `401` is never re-sent, so a fumbled rotation loses that day's workouts.
- **Where the new LLM key goes depends on §3.5's dependency note.** Once to-do §1 lands, the
  key belongs in `app_settings` via the Settings UI, not an env var. Reissuing *after* §1 saves
  entering it twice; reissuing *now* is the safer order if the old key's exposure is a concern,
  at the cost of one re-entry. **Revoking the old key is the part that should not wait** — it
  is independent of where the replacement lives.

Rotate only after §3.1 and §3.3 have landed.

- **Neon password.** Rotate now only if the Render deployment's credentials are considered
  suspect; otherwise skip. The database is decommissioned at cutover and the password has
  never left local disk and the Render env. Rotating costs a Render env-var update plus a
  working-tree edit, and buys a few weeks of hygiene on a resource that is being deleted.
  **Recommendation: skip, and let cutover retire it.**
- **Gemini key.** Rotate. It survives the cutover, it carries real quota/billing exposure,
  and rotation is a one-sided change — issue a new key, update the Render env var and the
  local file, revoke the old one. No coordination with anything.
- **Sync API key.** Rotate, but understand the cost: it is a **shared secret with the phone
  webhook app**, which lives outside this repository (to-do §6). Rotating breaks sync until
  the phone side is updated, and the phone side is edited by hand today. Sequence it as:
  generate the new key → update the phone app → update the Render env var → confirm the next
  webhook delivery returns non-`401`. Do not rotate this one in the middle of a day where a
  missed sync matters, and note that the phone sender transmits a delta — a sync rejected
  with `401` is not re-sent, so a fumbled rotation loses that day's workouts outright. The
  quarantine mechanism from [`sync-source-allowlist-spec.md`](sync-source-allowlist-spec.md)
  does not help here; rejection happens in the controller, before any of it runs.

The README already documents the intended generation path — `openssl rand -hex 24`
([`README.md:39`](../README.md#L39)) — so the new key's shape is settled.

### 3.5 Delete `application-local.properties` at cutover — DONE 2026-08-16

Deleted. By then the file held nothing live but the two keys: the Neon datasource pointed at a
database deleted on 2026-08-15, and `app.cors.allowed-origin` was already dead config — the
self-host work removed CORS entirely along with `APP_CORS_ALLOWED_ORIGIN`
([`atlas-selfhost-plan.md`](atlas-selfhost-plan.md) §86), and nothing in the tree reads that
property. Port 9090 was the only other setting, which is not worth a file.

Notes on the two loose ends:

- **The `server/.gitignore` entry was dropped as instructed**, and the rest of that file went
  with it. Audited on the owner's call to prioritise tidiness: the root `.gitignore` already
  matches `target/`, `HELP.md`, `*.db*` and the IDE patterns **at any depth**, so `server/`'s
  copies were redundant, and the STS/NetBeans/Gradle blocks were Initializr boilerplate for
  tooling this project does not use. Only two entries were not covered by root —
  `.mvn/wrapper/maven-wrapper.jar` and `src/main/resources/static/` (the built frontend Docker
  copies in) — and the file now holds just those. `git check-ignore -v` confirms every
  previously-ignored path still resolves, now against the root file. The intended consequence:
  a future `application-local.properties` is no longer ignored, so it shows up in
  `git status` instead of sitting invisible.
- **The `D:/Downloads/aiofitness/.env` loose end below was already resolved.** That directory
  now holds no files at all; the stale duplicate `docker-compose.yml` is gone too. Nothing to
  consolidate.

The file was copied to a scratch location before deletion, since it held the only local copy
of two live keys and was never in git. That copy is temporary — §3.4 supersedes it.

Once Neon is gone, the file has nothing left to hold that the environment does not already
supply. The self-hosted profile takes everything from env vars, documented in
[`README.md:59`](../README.md#L59). Delete it, and drop the `server/.gitignore` entry with it.

Two loose ends belong to this step, both currently outside the repository:

- `D:/Downloads/aiofitness/.env` holds `SYNC_API_KEY` in plaintext and sits **beside** the
  repo, not inside it, alongside a stale duplicate `docker-compose.yml` that references a
  lowercase `aio-fitness/dockerfile` path. The in-repo [`compose.yaml`](../compose.yaml)
  expects its `.env` at the repo root, per the README. Consolidate onto the in-repo pair and
  delete the parent-directory copies.
- **Dependency on to-do §1 — resolved 2026-08-13.**
  [`insight-provider-spec.md`](insight-provider-spec.md) §3 puts the insight provider key in
  `app_settings`, not the environment, and deletes `app.insight.api-key` /
  `app.insight.model` / `GEMINI_API_KEY` outright. So **`SYNC_API_KEY` is the only
  environment secret that survives**, and the Gemini key's rotation in §3.4 above becomes a
  one-time re-entry through the Settings UI after cutover rather than an env-var edit.
  The two specs share the `application.properties` edit; do them in one pass to avoid
  touching that file twice.

---

## 4. Verification

1. `unzip -l server/target/*.jar | grep application-local` → no match.
2. `docker build . && docker run --rm --entrypoint sh atlas:local -c 'unzip -l app.jar | grep -c application-local'` → `0`.
3. `grep -rn "Sync API key loaded" server/src` → no match.
4. Start the app with `SYNC_API_KEY` unset → context fails at startup with a missing-property
   error (confirms §3.1's premise that the log line was redundant).
5. Regression test, cheap and worth having permanently:

   ```java
   @Test
   void localPropertiesAreNotOnTheClasspath() {
       assertNull(getClass().getResource("/application-local.properties"));
   }
   ```

   This fails today against `target/classes`, which is the point — it pins §3.3 so a future
   resource-config change cannot quietly re-bundle the file.
6. Post-rotation: `POST /api/sync` with the old key → `401`; with the new key → `200`.

---

## 5. Sequencing against the cutover freeze

**Spent 2026-08-16.** Atlas has run on the home server since 2026-08-15, so the freeze this
section reasons about no longer binds anything, and §3.1's held exception was released with
the rest. The accepted residual risk below expired the way it was predicted to: the Render
deployment that was re-writing the sync key into its own log retention was deleted, taking the
retained logs with it. The section is kept because its §3.4 consequence still stands — the
sync key's rotation was deliberately sequenced after Render's decommissioning, and that is now
satisfied rather than pending.

Per the standing rule, no generalization to-do ships before Atlas runs on the home server.
Everything in §3.3–§3.5 is genuinely inert until then: the artifacts are local, and nothing
in them is reachable by anyone who is not already on the machine.

§3.1 was raised as a possible exception: the log line writes the live sync key into Render's
retained logs on every restart of an app in daily use, and the fix is a one-line deletion
with no schema, no migration and no interaction with the cutover.

**Decided 2026-08-12 — hold it.** §3.1 waits for the cutover with everything else; the
freeze applies without exception. The residual risk is accepted knowingly: until cutover,
every restart of the Render deployment re-writes the sync key into its log retention, and
anyone with Render dashboard or log-drain access can read it. That risk ends when the
deployment is decommissioned. It also means §3.4's rotation of the sync key should happen
*after* the Render deployment is gone, not before — rotating while the log line is still
live would just write the new key into the same place.

Only §3.0 (documentation redaction, already done) sits outside the freeze.

---

## 6. Out of scope

- Authentication for the app as a whole — rejected in `atlas-selfhost-plan.md` §3. The sync
  key is not a login; it is a single shared secret on one endpoint, and stays that way.
- Open-sourcing the phone webhook app (to-do §6), beyond noting that it holds the sync key.
- Where the LLM provider key ultimately lives (to-do §1) — see §3.5's dependency note.
- Secret management tooling (vaults, sops, encrypted env files). A single-user LAN app with
  two secrets does not earn it.
