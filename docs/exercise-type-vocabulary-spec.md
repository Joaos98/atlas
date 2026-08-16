# Spec: Exercise Type Vocabulary — Auto-Created Workout Types

**Status:** Refined, ready for implementation
**Date:** 2026-08-12
**Implements:** `atlas-generalization-todos.md` §2
**Ships with:** [`sync-source-allowlist-spec.md`](sync-source-allowlist-spec.md) (§3) — one bundle, after the cutover
**Component:** `server` — `SyncService`, `WorkoutTypeService`, new `ExerciseTypeCatalog`; `ui` — `SettingsView`, dashboard

---

## 1. Problem

Health Connect reports exercises as numeric type codes. Atlas resolves them through
`exercise_type_mapping`, a table the user must populate by hand, and drops anything unmapped:

```java
Optional<ExerciseTypeMapping> mapping = mappingRepository.findById(healthConnectType);
if (mapping.isEmpty()) {
    log.info("Unmapped Health Connect exercise type: {}. Add a mapping to auto-log this type.", healthConnectType);
    skipped++;
    continue;
}
```

The mapping UI is a raw integer field (`SettingsView.vue:106`, `placeholder="HC type code"`), so a
new user must discover that walking is `79` before a single walk is ever logged. The prerequisite
is backwards: the app knows the vocabulary, and asks the user to reproduce it.

---

## 2. Solution

`WorkoutType` keeps its current meaning — a user-facing label. What changes is that the bridge
from Health Connect's vocabulary to those labels builds itself.

1. **Auto-create.** An exercise type with no mapping row creates its `WorkoutType` (name and color
   from a static catalog) and its mapping, then logs the workout. "Unmapped" ceases to exist as a
   skip reason.
2. **Precedence.** An explicit mapping row always wins. Auto-create only fills gaps.
3. **Ignore.** A mapping row with a null workout type means *never log this activity*, replacing
   the capability that "just don't map it" provides today.
4. **Merge.** Types can be combined, reassigning their logs and mappings.
5. **Announce.** Auto-created types are surfaced for review rather than appearing silently.

### 2.1 Why not seed the vocabulary

The to-do proposed seeding Health Connect's ~70 exercise types as the canonical `WorkoutType`
set. Rejected: it puts a 70-item dropdown in front of manual logging, forces a reconciliation of
existing types against the seeded set (the exact migration risk the to-do worried about), and
gives every user a list dominated by sports they have never done.

Auto-create reaches the same destination — sync works out of the box, nothing is dropped — with
no seeding, no migration, and a type list that matches what the user actually does.

### 2.2 Why not a curated grouping table

Also considered: author a complete HC→label mapping over ~8 labels (Run, Ride, Swim, Strength,
Sport, Class, Other). It ships a taxonomy opinion, buries detail in "Other", and someone must
defend whether padel is a Sport. The catalog below is mechanical — one row per constant, names
taken from the enum — and carries no grouping judgment.

---

## 3. Existing installs need no migration

Because an explicit mapping row always wins, every hand-made type and mapping on an existing
install keeps working untouched. There is nothing to migrate and no way to orphan
`workout_logs.workout_type_id`. The to-do's stated risk does not arise under this design.

### 3.1 But groupings will fragment — hence §6.2

Confirmed 2026-08-12: this install has grouping types, e.g. **`Cardio`** covering several HC
exercise types. Auto-create does not know a grouping exists or what belongs in it. The first
elliptical session after upgrade creates an `Elliptical` type rather than extending `Cardio`, and
the history splits — coarse labels before, fine labels after, one activity at a time.

Nothing is lost, and §5.3 merge repairs each case, but only if the user notices. That is why
auto-creation is announced (§6.2) rather than silent, and why the upgrade checklist (§8) includes
mapping the HC types each grouping should cover **before** the first sync.

---

## 4. The catalog

A static table in the backend: HC exercise type code → display name. Data, not logic.

### 4.1 Source and transcription

Transcribe from the Health Connect `ExerciseSessionRecord` constants. Both reference pages render
their constant tables via JavaScript and cannot be scraped; transcribe from the source instead:

- `androidx.health.connect.client.records.ExerciseSessionRecord` (Companion constants), or
- `android.health.connect.datatypes.ExerciseSessionRecord` for the platform equivalent.

Cite the exact source and access date in a comment at the head of the catalog class. This is a
one-time transcription of roughly 70 entries; treat accuracy of the *codes* as the critical part
— a wrong name is cosmetic and user-fixable, a wrong code silently mislabels workouts.

### 4.2 Verification against real data

This install's `exercise_type_mapping` rows are ground truth: each is a code the owner mapped by
hand to a label he chose. After transcription, check the catalog's name for each of those codes
against the label he gave it. Disagreement means either a transcription error or a deliberate
relabel — both worth knowing before shipping.

### 4.3 Unknown codes

A code absent from the catalog (a future Health Connect release) still auto-creates, under a
generic name — `Activity 84`. It is never dropped and never quarantined: the only decision
available to the user is "yes, name it", so asking would be theatre. They rename it in Settings.

### 4.3.1 `type` is numeric — verified 2026-08-13

This design assumes the payload's `type` is a **numeric** Health Connect code:
`SyncService:70` does `Integer.parseInt(entry.getType())` and skips anything else. Upstream
describes `type` as *"Exercise type from Health Connect (string form)"*, which was ambiguous
between the code as a JSON string (benign) and a name like `"RUNNING"` (breaking — every
entry silently skipped).

**Resolved against a real delivery**, sender version **1.9.14**, 2026-08-13:

```json
{"type":"79","start_time":"2026-08-11T02:07:07.648Z", ...}
{"type":"0", "start_time":"2026-08-11T21:00:36Z",    ...}
```

Numeric codes as JSON strings. The catalog in §4 is keyed correctly and this spec is
unblocked. Recorded with the version number because a future release could still change it;
the check to repeat is exactly the one above.

### 4.4 Colors

The catalog carries names only. Color comes from a fixed palette of ~12 entries — extending the
existing demo family (`#e63946`, `#457b9d`, `#2a9d8f`, `#e9c46a`) — assigned by creation order,
so it is stable within an install and adjacent types never collide. Types remain recolorable in
Settings as they are today.

Rejected: hash-derived colors (frequently ugly, no ordering guarantee) and a curated color per
constant (~70 aesthetic decisions nobody will see most of).

---

## 5. Backend changes

### 5.1 Auto-create in `SyncService`

Replace the `mapping.isEmpty()` skip branch. For an entry whose type has no mapping row:

1. Look up the code in the catalog (§4.1); fall back to the generic name (§4.3).
2. Create the `WorkoutType` with that name, the next palette color, and
   `pending_review = true` (§6.2).
3. Create the `exercise_type_mapping` row pointing at it.
4. Continue into the normal insert path.

Creation runs in its own short transaction, consistent with the rule established in the dedup
spec §5.4: `sync()` is not transactional and no single entry may poison the batch.

### 5.2 Ignore

`exercise_type_mapping.workout_type_id` is **already nullable** — `@ManyToOne` without
`nullable = false` — so this needs no DDL:

```sql
exercise_type_mapping (health_connect_type integer not null, workout_type_id bigint, primary key (health_connect_type))
```

A row with a null workout type means ignore. Ordering matters in `SyncService`: the mapping row
is consulted first, so an ignore row suppresses both logging and auto-create.

Ignored entries are **dropped**, not quarantined — counted in the response and named in the log
line, but not retained. The principle from §3 spec: quarantine holds entries affected by a
decision the user has not made yet. Ignoring is a decision already made, and the high-volume
types people ignore (phone-detected walks) would grow the table indefinitely under that spec's
no-prune policy.

### 5.3 Merge

`POST /api/workout-types/{sourceId}/merge-into/{targetId}`:

1. `UPDATE workout_logs SET workout_type_id = target WHERE workout_type_id = source`
2. `UPDATE exercise_type_mapping SET workout_type_id = target WHERE workout_type_id = source`
3. `DELETE FROM workout_types WHERE id = source`

In one transaction. Reject source == target.

Necessary because auto-create makes near-duplicates likely (`Run` vs `Running`, or §3.1's
grouping fragmentation) and `WorkoutTypeService.deleteWorkoutType` returns 409 for any type with
logs — without merge, a duplicate is permanent.

**No foreign keys exist.** The generated schema has none anywhere:
`workout_logs.workout_type_id` is a bare `bigint`. Nothing at the database level will catch a
merge that misses rows, so the implementation must be correct unaided and §7 case 6 checks for
orphans directly rather than trusting an integrity error to surface.

### 5.4 Catalog endpoint

`GET /api/sync/exercise-types` → `[{code, name}]` from the static catalog, for the Settings
select (§6.1). Read-only, no database access.

---

## 6. Frontend changes

### 6.1 Settings

- **Mappings section:** the `HC type code` integer input becomes a searchable select over the
  catalog endpoint, showing activity names. Each row gains an **Ignore** action (writes a null
  mapping) and shows ignored types distinctly.
- **Workout types section:** each row gains **Merge into…**, choosing a target type.

### 6.2 New-type notice

Auto-created types carry `pending_review = true` on `workout_types` — **the one schema addition
in this spec**, a single boolean. While any exist, the dashboard shows a notice reusing §3's
pattern:

> New activity type added: **Elliptical** (3 workouts).

with actions to merge it into an existing type, ignore the activity, or dismiss. The flag clears
on merge, rename, ignore, or dismiss — action-based, not time-based, matching how §3's quarantine
notice behaves.

This exists for §3.1: taxonomy drift must be visible the day it starts, not discovered six months
later as an extra slice on the donut.

### 6.3 Demo build

`DemoSeedData` takes its four or five types from the catalog — the same names and palette colors
auto-create would produce — replacing the hand-written
`TYPE_NAMES = {"Run", "Strength", "Cycling", "Swimming"}` at `DemoSeedData.java:50`. Seed a
couple of explicit mappings and one ignored type so precedence and ignore are both visible in the
only build strangers will run.

---

## 7. Verification

On the existing SQLite integration harness.

| # | Case | Expected |
|---|---|---|
| 1 | Sync an unmapped HC type | type created from catalog name, mapping created, workout logged |
| 2 | Sync the same type again | no second type; existing mapping reused |
| 3 | Type with an explicit mapping | user's label wins; no auto-create |
| 4 | Type with a null mapping | dropped, counted, logged; no type created |
| 5 | Code absent from the catalog | created as `Activity <code>`, workout still logged |
| 6 | Merge | logs and mappings reassigned, source deleted, **zero orphaned `workout_type_id`** |
| 7 | Merge source == target | rejected |
| 8 | Catalog integrity | no duplicate codes, no blank names, palette covers every entry |
| 9 | Auto-created type | `pending_review = true`; cleared by merge/rename/ignore/dismiss |

---

## 8. Upgrade checklist for this install

Before the first sync after upgrade (see §3.1):

1. List the current `workout_types` and note which are groupings (`Cardio` at minimum).
2. For each grouping, add explicit mapping rows for every HC type it should absorb — otherwise
   the first occurrence of each will auto-create its own type.
3. Add ignore rows for any activity currently excluded by *not* being mapped. This is the one
   behaviour change that could surprise: today an unmapped type is silently skipped, and after
   this change it will be logged.

Step 3 is the migration-shaped risk in this spec. There is no code to write for it, but skipping
it means phone-detected activity starts appearing in the history.

---

## 9. Relationship to the sync-source spec

Ships as one bundle with `sync-source-allowlist-spec.md`, after the cutover.

Because both land together, the `UNMAPPED_TYPE` quarantine state described in earlier drafts of
that spec never exists: quarantine replay can auto-create directly. That spec's `reason` column
is retained at a single value (`SOURCE_NOT_ALLOWED`) to keep the table self-describing.

Order within the bundle: catalog and auto-create first (they make the rest meaningful), then
ignore, then merge, then the UI and demo work.
