# Spec: Documenting the Sync Pipeline's Phone Side

**Status:** Refined, ready for implementation
**Date:** 2026-08-13
**Implements:** `atlas-generalization-todos.md` §6
**Builds on:** [`webhook-sync-deduplication-spec.md`](webhook-sync-deduplication-spec.md) §2 (observed sender behaviour), [`sync-source-allowlist-spec.md`](sync-source-allowlist-spec.md) §5 (quarantine)
**Component:** `README.md`. No code changes.

---

## 1. Problem

The sync pipeline's phone side is undocumented. The README describes the endpoint
(`POST /api/sync`, `X-API-Key`) but never says what sends to it, so nobody can reproduce the
pipeline — the app's most distinctive feature — without already knowing the author's setup.

### 1.1 The to-do's premise does not hold

§6 proposed to "open-source the webhook app into this (or a sibling) repository with its own
README." The sender is **HC Webhook** (<https://hcwebhook.com/>), a third-party product on
the Play Store and App Store — Android does automatic background sync, iOS goes through the
Shortcuts app.

It is **already open source**: <https://github.com/mcnaveen/health-connect-webhook>, under
**AGPL-3.0** with a `LICENSE.ADDENDUM` requiring a separate commercial licence for
redistribution on app stores. So the to-do's proposal is not blocked — it is *already done, by
someone else*. Vendoring a copy into this repository would fork a maintained AGPL project to
no benefit, inherit its licence obligations, and go stale. **Link it; do not copy it.**

The other open question closes on the facts rather than by decision:

- **"Whether fixing its double-fire is in scope."** Not Atlas's to fix. The backend is
  authoritative permanently — which the dedup spec already assumed ("the duplicate sends will
  continue and the backend must be authoritative"). §1.4 now offers a likely cause.

### 1.2 Upstream documents the contract — and it disagrees with reality

`hcwebhook.com` is a landing page with no technical detail, but the repository carries
`docs/webhook.md`, a field-level payload reference. **This spec previously claimed no upstream
documentation existed and that the contract had to be authored here. That was wrong**, and it
changes the shape of the work: Atlas's job is to link upstream and document *its own*
requirements and the places where its install diverges — not to reverse-engineer a contract
from scratch.

What upstream documents that the dedup spec had to infer:

- **Sync semantics** — a rolling **48-hour window**, filtered to records new or updated since
  the last successful sync watermark *per type*. This confirms the dedup spec's "delta, not a
  time window" finding and explains the carried-over newest workout as window overlap. It also
  resolves that spec's §10.3 "unverified inference".
- **Retries** — up to **3 attempts with exponential backoff**, with failed deliveries retried
  on the next successful trigger.
- **Scheduling** — Interval mode (WorkManager, 15-minute minimum) or Scheduled mode
  (AlarmManager, default 08:00 and 21:00). This install's 22:00 daily is a configured
  Scheduled-mode time.
- **Custom headers** — configurable per webhook URL, which is how `X-API-Key` is attached.
  There is no built-in signature or auth header.

### 1.4 Four divergences between upstream docs and this install

Verified against `SyncRequest`, `SyncService` and the delivery logs in the dedup spec §2:

All verified against a real delivery of **2026-08-13** (sender version `1.9.14`, three
exercise entries, `200`, 159 384 ms) as well as the six logs in the dedup spec §2:

| Upstream says | Real traffic shows | Consequence |
|---|---|---|
| Timestamps are ISO-8601 UTC with fractional seconds (`…12:34:56.789Z`) | **Two formats in one payload** — `2026-08-11T02:07:07.648Z` next to `2026-08-11T21:00:36Z` | Confirms §2.1: a strict schema would reject real traffic |
| Exercise records carry no `data_origin` / `recording_method` | Every entry carries `metadata.data_origin` and `metadata.recording_method`, and the entire allow-list design depends on them | Upstream's field table is **incomplete**; do not treat it as exhaustive |
| `end_time` is required | Present in every entry — but `SyncRequest` does not model it; Atlas uses `start_time` + `duration_seconds` | Harmless, and the divergence is Atlas's, not the sender's |
| Optional `distance_meters`, `steps`, `avg_cadence_spm`, `stride_length_m` | Absent from this install's payloads (tracking not enabled) | Would be silently dropped. Relevant to to-do §5's scope claim |
| Not documented at all | Top-level `timestamp` and `app_version`; `metadata.device` with `manufacturer` (`"xiaomi"`) and `type` | All unmodelled by Atlas. `app_version` is worth capturing for diagnostics; `device.manufacturer` could give the allow-list a friendlier source label than a package name |

**The double-fire probably is not a bug.** Upstream's 3-attempt exponential backoff, combined
with this install's 144–173 second cold-start response times, is a better explanation than a
defective app: the client gives up waiting, retries, and the original request succeeds anyway
— producing two deliveries seconds apart with identical content and two `200`s. That fits the
Aug 4 evidence (5.27 s apart, identical, both 200) better than "the app fires twice". Stated
as a hypothesis, not a finding; confirming it would need the app's timeout value.

### 1.5 `type` is numeric — resolved 2026-08-13

Upstream documents `type` as *"Exercise type from Health Connect (string form)"*, ambiguous
between the numeric code as a JSON string and a name like `"RUNNING"`. Atlas does
`Integer.parseInt(entry.getType())` (`SyncService:70`) and skips non-numeric types with a
warning, so the second reading would mean every entry silently skipped — a `200` with zero
created, indistinguishable from the quarantine case in §4.

**Resolved: numeric.** Sender version `1.9.14` sends `"type":"79"` and `"type":"0"`. The
README may safely tell people to install this app, and
[`exercise-type-vocabulary-spec.md`](exercise-type-vocabulary-spec.md) §4.3.1 — whose
auto-create design keys on numeric codes — is unblocked. Both specs record the version
number, since a future release could still change it.

### 1.6 What one delivery confirms about the window

The 2026-08-13 01:00 UTC delivery carried workouts starting `2026-08-11T02:07Z` and
`2026-08-11T21:00Z` — 47 and 28 hours old, both inside the documented 48-hour rolling window
and both near enough its edge to be consistent with it.

Note what that implies: the 21:00 workout was already four hours old at the *previous* daily
sync, so either it was re-sent (the watermark not suppressing it) or the preceding delivery
failed. One log cannot distinguish those, and it does not need to — re-sends are safe by
design. It is recorded because it is the observation that would tell you which, if the
question ever matters: **compare two consecutive deliveries for an overlapping entry.**

### 1.3 The dependency is real and belongs in the README

The pipeline depends on a product the author does not control, which may change its payload,
its pricing, or its availability. §2 bounds that risk but does not remove it, and a reader
should be told plainly rather than discovering it.

---

## 2. Solution

**Document the sender, and treat `POST /api/sync` as a published interface.**

Naming HC Webhook alone would hand a new user a hard dependency on one product with no way to
substitute anything. Publishing the contract alongside it turns the endpoint from "the thing
one app happens to speak to" into an interface any sender can satisfy — including one the
user writes. HC Webhook becomes *an* example that satisfies the contract, not the requirement.

Building a first-party sender was rejected: an Android app is a second product with its own
build, signing, distribution, permission review and permanent maintenance burden, and it is
unnecessary when the endpoint is the contract and a working sender already exists.

Because upstream publishes `docs/webhook.md` (§1.2), Atlas's documentation **links** the
payload reference rather than restating it, and confines itself to what upstream cannot know:
which fields Atlas requires, which it ignores, the idempotency guarantee, the allow-list
two-step, and the divergences in §1.4. Restating a maintained upstream document is how
documentation goes quietly stale.

### 2.1 The contract is descriptive, not prescriptive

It states what `/api/sync` **accepts and guarantees**, not what senders ought to do. The
headline guarantee:

> The endpoint deduplicates by signature. A sender may re-send freely; repeat deliveries are
> safe.

That is already true by design — it is what the unique constraint from the dedup spec buys —
and it is the most useful thing to tell someone writing a sender, because it means the phone
side needs no reliable-delivery tracking.

A formal schema with `400` on nonconforming payloads was rejected: the only known working
sender emits **two timestamp formats inside a single payload**
(`2026-08-02T15:13:36.618Z` alongside `2026-08-03T20:59:32Z`), so strict validation would
reject the author's own pipeline. Prescriptive `SHOULD` language was rejected as words
without consequence — a sender author cannot act on "SHOULD NOT re-send" when HC Webhook is
a black box to them too.

This reframes the double-fire correctly. It is not a tolerated defect; it is harmless
*because the endpoint is idempotent* — the same fact stated as a property of this system
rather than a flaw in someone else's.

---

## 3. What the README carries

A "Syncing workouts from your phone" section, short enough that the README stays a README:

1. **What sends the data** — HC Webhook, linked to both <https://hcwebhook.com/> and the
   AGPL-3.0 source at <https://github.com/mcnaveen/health-connect-webhook>, noted as
   third-party, with Android (background sync) and iOS (Shortcuts) both available. Link
   `docs/webhook.md` for the payload reference rather than restating it.
2. **How to point it here** — `POST http(s)://<host>:8080/api/sync`, header
   `X-API-Key: <your SYNC_API_KEY>`, on a daily schedule.
3. **The guarantee** — one line: the endpoint is idempotent, duplicate deliveries are safe,
   so retries and double-fires need no handling on the phone.
4. **First run is two steps** — §4.
5. **The limitation** — §1.3, stated plainly: third-party dependency, bounded by the fact
   that any sender satisfying the contract works.

**Example payloads must be invented, not copied from real deliveries.** Real logs carry the
author's workout timestamps, device origin and daily schedule — a precise record of when the
house is empty. Hand-sanitising a real payload is how a real value survives into a committed
file; this repository has already had one near-miss of that kind
(`secrets-handling-spec.md` §1.3).

---

## 4. First run is two steps, and the docs must say so

`sync-source-allowlist-spec.md` leaves the allow-list **empty on fresh installs**, with
rejected entries quarantined rather than dropped. So a correctly configured new sender gets
`200 OK`, zero workouts created, everything quarantined, and an empty app — until the user
enables their device as a source in Settings, at which point quarantine replays and nothing
is lost.

Auto-enabling the first origin seen was considered and rejected: the allow-list is a
dedup-safety gate, not a device preference, and a first HC Webhook payload contains both
stable Xiaomi records *and* Google activity-detection records whose timestamps drift and can
never be deduplicated. Auto-enabling would silently admit exactly the class of record the
gate exists to exclude, on the one sync where the user has no basis for noticing.

The cost of keeping it is one Settings visit that recovers everything. What it demands from
the documentation: the two-step must be stated up front, and the sync response's
"everything was quarantined" signal (`sync-source-allowlist-spec.md` §5.2) is what makes
step two discoverable. Without both, the honest description of the experience is "the app
silently does not work" — the exact failure this to-do list exists to remove.

---

## 5. Handoff — what the Saturn documentation site must cover

The full treatment of sync belongs on the planned Docusaurus site covering Atlas and the
other Saturn projects, not in this repository. **§6 does not depend on it** (§6 closes when
the README section exists — see §7), but the material must not evaporate. The page should
cover, all of it already written down in the specs cited:

| Topic | Source |
|---|---|
| Payload shape and field semantics | upstream `docs/webhook.md`; `SyncRequest` for what Atlas models |
| Divergences between upstream docs and real traffic | §1.4 above |
| 48-hour rolling window + per-type watermark | upstream README; dedup spec §2 |
| The double-fire, and why it is harmless | dedup spec §2, §1.4 and §2.1 above |
| Timestamp-format variance within one payload | dedup spec §2, §1.4 above |
| The dedup signature and its unique constraint | dedup spec §3 |
| Device-origin allow-list, quarantine and replay | sync-source-allowlist spec §5 |
| Exercise-type auto-creation on unmapped codes | exercise-type-vocabulary spec §5.1 |

Assembled from these, the page is an editing job. Left unrecorded, it is a second
reverse-engineering exercise.

---

## 6. Non-goals, restated because the to-do proposed them

- **Publishing the sender.** Already public under AGPL-3.0; link it rather than fork it
  (§1.1).
- **Fixing the double-fire.** Not Atlas's to fix, and probably a retry rather than a defect
  (§1.4). Backend is authoritative.
- **Building a first-party sender app.** Rejected in §2.
- **Republishing HC Webhook's exported configuration.** Version-specific, goes stale
  silently, and is a placeholder-substitution accident waiting to happen. Generic setup steps
  instead (§3).
- **Schema validation on `/api/sync`.** Rejected in §2.1.

---

## 7. Verification

1. A reader who has never seen the project can wire up a phone from the README alone —
   install, point at the host, set the header, and see workouts after enabling the source.
2. The README states the idempotency guarantee, the two-step first run, and the third-party
   dependency.
3. No example payload in the repository contains a real timestamp, device origin or host.
4. `§6` is closed by the README section; the doc-site page is tracked as Saturn work, not as
   an Atlas to-do.

---

## 8. Sequencing

Behind the cutover freeze with everything else, and last among the to-dos: it documents
behaviour that `sync-source-allowlist-spec.md` and `exercise-type-vocabulary-spec.md` change.
Writing it before those land means documenting a pipeline that is about to behave
differently — in particular the quarantine two-step in §4, which does not exist yet.
