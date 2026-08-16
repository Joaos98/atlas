# Spec: Provider-Agnostic Insights via an OpenAI-Compatible Client

**Status:** Implemented and verified 2026-08-16. All ten verifications in §10 pass — including 9,
confirmed by the owner by hand against a real provider through the Settings UI, since the
sandbox will not let an agent submit an API key. That run also settles a question this spec left
open: `gemini-3.5-flash` **is** a valid model ID on Gemini's OpenAI-compatible endpoint, so
§3.1's seeded default needs no correction. See §13 for what implementation found that this spec
did not.
**Date:** 2026-08-13
**Implements:** `atlas-generalization-todos.md` §1
**Unblocks:** [`secrets-handling-spec.md`](secrets-handling-spec.md) §3.5 — the insight key stops being an environment variable
**Depends on:** to-do §5 (units) for prompt unit handling only — see §7
**Component:** `server` — `InsightService`, `InsightController`, `AppSettings`, `AppSettingsController`, `AppSettingsService`; `ui` — `SettingsView`, `InsightCard`, demo build

---

## 1. Problem

Insight generation is welded to Gemini. `InsightService` builds a Gemini-shaped request —
`systemInstruction` / `contents` / `generationConfig` — and posts it to
`https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}`.
The key arrives as `@Value("${app.insight.api-key:}")`, an environment variable fixed at
deploy time.

A self-hosted user therefore cannot use insights at all without a Google API key, and cannot
supply even that without editing a `.env` file and restarting the container. For the app's
single most visible feature, that is not self-serve.

---

## 2. Solution

**One OpenAI-compatible HTTP client, configured by three database-backed settings.**

`baseUrl`, `apiKey`, `model`. No provider interface, no per-provider implementations. The
chat-completions wire format is spoken by OpenAI, Gemini (via its OpenAI-compatible
endpoint), Groq, OpenRouter, Ollama and LM Studio, so `baseUrl` *is* the provider selector
and switching providers is a settings change rather than a code change.

### 2.1 Why not a provider interface

The to-do assumed `InsightProvider` with an implementation per vendor. Rejected: it is a
plugin architecture for an app with one user, and the abstraction earns nothing while every
target speaks one format. The single genuinely different wire format — Anthropic's native
API — is reachable through OpenAI-compatible proxies. If a second format ever becomes
necessary, extracting an interface is a contained refactor, because every call site already
lives in one service.

The cost accepted: no Gemini-native features (`systemInstruction` as a distinct field,
Google safety settings). For one text-in/text-out call with a system prompt, the compat
layer is sufficient.

### 2.2 Why this decision constrains later ones

Two options elsewhere in this spec were rejected *because* they would reintroduce
per-provider branching immediately after choosing not to have any — structured JSON output
(§6) and provider-specific error taxonomies (§5). Support for both diverges across
OpenAI-compatible endpoints. Choosing the single client means choosing the lowest common
denominator deliberately, not accidentally.

---

## 3. Settings — storage and exposure

### 3.1 Schema

Three columns on `app_settings`, which today holds exactly one (`target_workouts_per_week`):

| Column | Type | Seeded value |
|---|---|---|
| `insight_base_url` | text | `https://generativelanguage.googleapis.com/v1beta/openai` |
| `insight_api_key` | text | empty |
| `insight_model` | text | `gemini-3.5-flash` |

Seeded by extending `AppSettingsService.ensureSeeded()`, already invoked by
`AppSettingsSeeder` at startup. Existing installs get the new columns via `ddl-auto=update`
with the same defaults; only the key is ever blank.

Pre-seeding Gemini rather than leaving all three blank is deliberate: an empty `baseUrl`
gives a new user no clue what a valid value looks like, which is to-do §2's
reverse-engineering problem in a different costume. Pre-seeded, the common path is one
field, every other provider is one edit, and the URL documents its own format.

### 3.2 The key must never be readable over HTTP

`AppSettingsController` currently serializes the **entity** on both `GET` and `PUT`:

```java
@GetMapping  public AppSettings get()                        { return appSettingsService.get(); }
@PutMapping  public AppSettings update(@RequestBody AppSettings s) { return appSettingsService.update(s); }
```

Adding an `insightApiKey` field to `AppSettings` therefore leaks it over `GET /api/settings`
**the moment the column exists**, with no further mistake required. The app has no auth by
design (self-host plan §3), so that endpoint is readable by anything on the LAN.

Introduce `AppSettingsDto` for both directions:

- **GET** returns `insightBaseUrl`, `insightModel`, `insightApiKeyConfigured` (boolean) and
  `insightApiKeyLast4`. Never the key.
- **PUT** accepts a key value with these semantics:
  - field **absent or empty string** → leave the stored key unchanged;
  - field **non-empty** → replace the stored key;
  - explicit **`clearInsightApiKey: true`** → clear it.

The empty-means-unchanged rule is what stops every unrelated settings save from wiping the
key, and it is why clearing needs its own affordance rather than an empty field.

### 3.3 What this removes

`app.insight.api-key` and `app.insight.model` are deleted from `application.properties`,
`GEMINI_API_KEY` from `compose.yaml` and the README's environment table, and the
`@Value`-injected `apiKey` / `model` fields from `InsightService` — including its dead
`gemini-2.0-flash-lite` default, which already disagreed with the `gemini-3.5-flash` in
`application.properties`.

After this change **`SYNC_API_KEY` is the only secret in the environment**, which is the
condition `secrets-handling-spec.md` §3.5 was waiting on.

### 3.4 Accepted risk

The key lives in `atlas.db` in plaintext, so a copied database is now a leaked key. Given
the app has no auth and that database already holds every measurement ever taken, this is a
widening of an existing exposure rather than a new category. Recorded as a decision, not a
side effect.

---

## 4. The client

Replace `callGemini` / `doGeminiCall` with a single `POST {baseUrl}/chat/completions`:

```json
{
  "model": "<insight_model>",
  "messages": [
    {"role": "system", "content": "<SYSTEM_PROMPT>"},
    {"role": "user",   "content": "<built prompt>"}
  ],
  "temperature": 0.7,
  "max_tokens": 2000
}
```

`Authorization: Bearer <insight_api_key>`. Response text at
`choices[0].message.content`. The existing single retry on 503 is kept.

`buildPrompt(...)` and `SYSTEM_PROMPT` are unchanged — see §7.

---

## 5. Failure states

`apiErrorMessage(...)` maps HTTP statuses to friendly strings and everything else falls into
`catch (Exception e)` → "An unexpected error occurred". With a free-form `baseUrl` the
common failures become: Ollama not running, a model name that doesn't exist at that
endpoint, a `baseUrl` missing `/v1`, and no key configured — all currently rendered as the
same nine words. Self-serve setup fails precisely where diagnosis is impossible.

Three distinct states replace it:

| State | Trigger | Message |
|---|---|---|
| **Not configured** | `insight_api_key` blank | "Insights are off — add a provider key in Settings." Not an error: no red styling, no retry affordance. This is a fresh install's normal state, and today it renders as a crash. |
| **Unreachable** | `ResourceAccessException` (connection refused, timeout) | "Couldn't reach the provider at `{baseUrl}`" — naming the URL back is what makes a typo self-evident. |
| **Provider error** | `RestClientResponseException` | Friendly line for the status, plus the provider's own message: "Provider returned 404: model 'llama3.2' not found". |

**Truncate provider error bodies to ~300 characters, and never log a response body at INFO.**
Providers occasionally echo the request back in errors; that is how a key reaches the logs a
second time, which is the exact failure `secrets-handling-spec.md` exists to clean up.

Raw passthrough was rejected: it produces walls of JSON in a card sized for two paragraphs.

---

## 6. Output parsing

The prompt demands `VERDICT: …` / `INSIGHT: …`, and `parseRawText` requires
`raw.startsWith("VERDICT:")` exactly. Gemini complies reliably; small local models often
emit `**VERDICT:**`, a leading newline, or unlabelled prose. On mismatch the parser dumps
the **entire raw reply into the insight body**, so the card renders `VERDICT: Plateau …
INSIGHT: Your weight …` as one unlabelled blob — a silent failure that reads as an app bug
rather than a model mismatch. Rare today; routine once any model can be selected.

Make the parser forgiving instead — case-insensitive, tolerant of markdown emphasis and
leading whitespace, roughly:

```
/^\s*\**\s*verdict\s*:\**\s*(.+?)\s*\**\s*insight\s*:\**\s*(.*)$/is
```

A reply with no recognisable verdict stores its prose as-is with a null verdict.
`InsightCard` already renders the verdict conditionally, so this needs no UI change — but a
null verdict stops meaning "something went wrong" and becomes a normal outcome for a weaker
model.

Structured JSON output was rejected per §2.2. And stated plainly: **no parser makes a small
local model produce good coaching prose.** This keeps weak output readable, not good.

---

## 7. Prompt copy — no changes

The to-do expected the prompt to carry the original user's framing. It does not:
`"You are a personal fitness coach. The user tracks body metrics and workouts in a personal
app…"` is a generic role, and "warm and direct" / 2nd person is product voice, not
personalisation. Nothing to sweep. **This bullet closes with no action.**

Units are the one real coupling. The prompt ships metric labels inline (`- Weight: 82.3 kg`,
`- Body water: 41.2 L`, goal targets via `metricUnit(...)`). This is correct today because
the units are stated explicitly, so the model knows what it is reading. It becomes wrong
when **to-do §5** introduces an imperial display preference and the card shows pounds while
the insight text says kilos.

**Resolved 2026-08-13.** [`units-preference-spec.md`](units-preference-spec.md) settled the
storage question: canonical metric in the database, converted at the display boundary, with
a `unit_system` column on `app_settings`. `InsightService.buildPrompt(...)` is named there as
one of the two conversion sites — it reads `unitSystem` from `AppSettingsService`, which it
already injects for `targetWorkoutsPerWeek`, and converts before writing units into the
prompt.

That spec sequences itself **after** this one, because this one introduces `AppSettingsDto`
and the settings UI section that `unitSystem` then slots into. Nothing in this spec changes;
the prompt work happens in §5's pass.

---

## 8. Prerequisite defect — failed generations overwrite good insights

`InsightController.regenerate()` persists the result unconditionally:

```java
String storedText = result.verdict() != null ? "VERDICT:" + ... : result.text();
entry.setInsightText(storedText);
entry.setInsightGeneratedAt(result.generatedAt());
bodyMetricsRepository.save(entry);
```

When generation fails, `result.text()` is an error string and `verdict()` is null — so
"Insight service rate limited. Please try again later." is written into
`body_metrics.insight_text`, **destroying the last good insight**. `GET /api/insights` then
re-serves it as an insight with `fallback` hardcoded `false` (`InsightController:37`), so the
UI's "(auto-generated)" hint never appears and the loss is invisible.

Today this needs a provider outage. After BYO-key, the first-run path is: paste key, typo
it, regenerate, 401 — and setup errors become the most common way to lose data in the app.
The refactor is what makes the latent bug routine, so the refactor owns it (same reasoning
as the sync-source spec absorbing the missing unique index).

**Fix:** persist only when `!result.fallback()`. Errors return in the HTTP response and
leave stored state untouched. `insight_generated_at` regains its literal meaning, and `GET`
stops lying — `fallback` is then always legitimately `false` there, since only successes are
ever stored.

---

## 9. Frontend

### 9.1 Settings

New "Insights" section: `baseUrl` (text), `model` (text), and an API key field showing
`Configured ✓ ····1a2b` with **Replace** and **Remove** actions. The key field must never be
bound to a fetched value — there is no fetched value.

### 9.2 Insight card

Renders the three states from §5. "Not configured" is a neutral empty state with a link to
Settings, not an error.

### 9.3 Demo build

`insightGate.js` already blocks regeneration behind a modal. The new Settings fields must be
read-only or hidden in the demo, or it presents a key form that silently does nothing. **The
demo seed must never contain a key value.**

---

## 10. Verification

1. Fresh install, no key → card shows "Insights are off", no error styling, no 500.
2. `GET /api/settings` → response contains no key value under any field name. Assert this in
   a test; it is the §3.2 leak, and a future `AppSettings` field could reintroduce it.
3. `PUT /api/settings` with the key field absent → stored key unchanged.
4. `PUT` with `clearInsightApiKey: true` → key cleared, state returns to not-configured.
5. Bad `baseUrl` (nothing listening) → "Couldn't reach the provider at …", naming the URL.
6. Valid key, nonexistent model → provider's 404 message surfaced and truncated.
7. Regenerate with an invalid key → 401 surfaced **and the previously stored insight is
   still intact** (§8).
8. Parser: `**VERDICT:** X\n**INSIGHT:** Y`, leading newlines, and unlabelled prose all
   produce sane output — the third with a null verdict and no stray labels.
9. Against a real OpenAI-compatible endpoint that is not Gemini (Ollama locally is enough),
   end-to-end generation succeeds. This is the whole point of the spec and the one test that
   cannot be faked with a mock.

---

## 11. Sequencing

Per the standing rule, nothing here ships before the cutover. It is also the largest of the
remaining to-dos and touches settings storage, so it should land **after**
`secrets-handling-spec.md` §3.5 is understood but ideally in the same pass — the two share
the `application.properties` edit, and doing them separately means touching that file twice.

Order within this spec: §3 (schema and DTO, including the leak test) → §4 (client) → §8
(defect fix) → §5/§6 (states and parser) → §9 (UI).

---

## 13. What implementation found — added 2026-08-16

Four things this spec asserted or omitted that did not survive contact with the code.

**§3.1's migration claim was wrong.** "Existing installs get the new columns via
`ddl-auto=update` with the same defaults" — they do not. `ALTER TABLE ADD COLUMN` leaves
existing rows NULL, and `AppSettingsService.ensureSeeded()` early-returned whenever the row
existed, which is the case on every install holding real data. Taken literally the spec would
have shipped `insight_base_url = NULL` to exactly the databases that matter. `ensureSeeded()`
now inserts *and then* backfills NULL columns via COALESCE, so it converges either starting
point and stays idempotent. To-do §5 adds `unit_system` to the same table and inherits the fix.

**§8 named one of three write sites.** `InsightController.regenerate()` was the site this spec
found, but `BodyMetricsService.save(...)` and `BodyMetricsService.regenerateInsight(...)` both
persisted results unconditionally too. The `save` path is the worse one: on an install with no
provider, *creating a measurement* stamped "Insights are off — add a provider key in Settings."
into `insight_text` permanently, and `GET` served it back as a genuine insight. Caught by
running the app, not by the test suite. The rule now lives in one place —
`InsightService.applyIfGenerated(...)` — so a fourth call site cannot get it wrong, and §8's
claim that "only successes are ever stored" is true for the first time.
(`regenerateInsight(Long)` turned out to be dead code — no controller exposes it.)

**Three states needed a field, not just prose.** §5 and §9.2 require the UI to tell "not set up"
from "broken", but the API carried only `fallback: boolean` and the frontend recovered the rest
by string-matching `text.includes('could not be generated')`. Rewording the messages would have
silently broken that. `InsightState` is now an explicit field; `fallback` is derived and
deprecated.

**The success path had no coverage at all.** Every test written for §5, §6 and §8 exercises a
failure — no key, nothing listening, parser input handed over directly — so the one path this
spec exists for was the only one never executed. `InsightProviderClientTest` closes it with a
JDK `HttpServer` stub: it asserts the outgoing request (path, `Bearer` header, model,
`messages` roles, `temperature`, `max_tokens`) and the reply handling (content extraction,
markdown-wrapped labels off the wire, the 503 retry firing exactly once, error bodies surfaced
and truncated, and a failure leaving stored state alone). Confirmed to have teeth by breaking
the response path on purpose — five of its eight tests failed. It does not replace verification
9: a stub proves Atlas holds up its end of the format, not that any vendor agrees. Verification
9 was subsequently run by hand and passed, so both halves are now covered — the stub guards
against regressions, the manual run confirmed the format was right to begin with.

**Deleting `application-local.properties` broke local dev.** Not this spec's change, but its
consequence landed here: the file supplied dev port 9090 *and* `SYNC_API_KEY`, which has no
default, so the documented `spring-boot:run` command no longer started. `server/README.md` now
carries a self-contained command.

---

## 12. Out of scope

- A provider interface or per-vendor implementations — §2.1.
- Structured/JSON output modes — §2.2, §6.
- Streaming responses, token accounting, cost display.
- Unit preference plumbing — to-do §5, see §7.
- Auth on `/api/settings`. It has none today and this spec does not add any; §3.2 is written
  so the key is safe *without* it.
