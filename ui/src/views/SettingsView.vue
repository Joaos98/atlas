<template>
  <div v-if="loading" class="page">
    <h1>Settings</h1>
    <section>
      <h2>Workout target</h2>
      <div class="card card-fit">
        <SkeletonLoader height="2.5rem" width="280px" />
      </div>
    </section>
    <section>
      <h2>Units</h2>
      <div class="card card-fit">
        <SkeletonLoader height="2.5rem" width="260px" />
      </div>
    </section>
    <section>
      <h2>Insights</h2>
      <div class="card card-fit">
        <SkeletonLoader height="2.5rem" width="320px" />
        <SkeletonLoader height="2.5rem" width="280px" />
      </div>
    </section>
    <section>
      <h2>Workout types</h2>
      <div class="card card-fit">
        <SkeletonLoader height="2.5rem" width="320px" />
      </div>
    </section>
    <section>
      <h2>Health Connect mappings</h2>
      <div class="card card-fit">
        <SkeletonLoader height="2rem" width="300px" />
        <SkeletonLoader height="2rem" width="220px" />
      </div>
    </section>
  </div>
  <div v-else class="page">
    <h1>Settings</h1>

    <!-- Seven sections stacked full-width ran to nearly three screens. The short ones pair up;
         only the tables need the whole row. -->
    <div class="settings-grid">
    <!-- Two one-line controls that each had their own heading and card, leaving a column of
         dead space beside anything taller. -->
    <section>
      <h2>Preferences</h2>
      <div class="card card-fit">
        <div class="pref-row">
          <span class="pref-label">
            <Target :size="14" class="inline-icon" /> Workout target
          </span>
          <form class="inline-form" @submit.prevent="saveTarget">
            <input v-model.number="target" type="number" min="1" max="7" required class="target-input" aria-label="Target workouts per week" />
            <span class="inline-label">per week</span>
            <button type="submit" class="btn-small">Save</button>
          </form>
        </div>
        <p v-if="targetMessage" class="form-success">{{ targetMessage }}</p>
        <p v-if="targetError" class="form-error">{{ targetError }}</p>

        <div class="pref-row">
          <span class="pref-label">
            <Ruler :size="14" class="inline-icon" /> Units
            <InfoHint text="Display only. Measurements are always stored in metric, so switching back and forth never changes your data." />
          </span>
          <div class="unit-toggle">
          <button
            v-for="option in ['METRIC', 'IMPERIAL']"
            :key="option"
            type="button"
            class="unit-option"
            :class="{ active: unitSystem === option }"
            @click="saveUnitSystem(option)"
          >{{ option === 'METRIC' ? 'Metric (kg, L)' : 'Imperial (lb)' }}</button>
          </div>
        </div>
        <p v-if="unitError" class="form-error">{{ unitError }}</p>
      </div>
    </section>

    <section>
      <h2>
        Insights
        <InfoHint text="Works with any OpenAI-compatible provider — OpenAI, Gemini, Groq, OpenRouter, or a local Ollama. The base URL selects which one. Leave the key unset to turn insights off." />
      </h2>
      <div class="card card-fit">
        <p v-if="isDemo" class="muted-note">
          Read-only in the demo — generating needs a real backend.
        </p>
        <form class="insight-form" @submit.prevent="saveInsights">
          <div class="form-field">
            <label><Sparkles :size="14" /> Base URL</label>
            <input v-model="insightBaseUrl" type="url" class="wide-input" :disabled="isDemo"
                   placeholder="https://api.openai.com/v1" required />
          </div>
          <div class="form-field">
            <label><Box :size="14" /> Model</label>
            <input v-model="insightModel" class="wide-input" :disabled="isDemo"
                   placeholder="gpt-4o-mini" required />
          </div>
          <div class="form-field">
            <label><KeyRound :size="14" /> API key</label>
            <!-- Never bound to a fetched value: the key is write-only, so there is none. -->
            <div v-if="keyConfigured && !replacingKey" class="key-state">
              <span class="key-mask">Configured ✓ ····{{ insightKeyLast4 }}</span>
              <button type="button" class="btn-small" :disabled="isDemo" @click="replacingKey = true">Replace</button>
              <button type="button" class="btn-small btn-danger" :disabled="isDemo" @click="removeKey">Remove</button>
            </div>
            <div v-else class="key-state">
              <input v-model="newInsightKey" type="password" class="wide-input" :disabled="isDemo"
                     autocomplete="off" placeholder="Paste your provider API key" />
              <button v-if="keyConfigured" type="button" class="btn-small" @click="cancelReplace">Cancel</button>
            </div>
          </div>
          <div class="form-actions">
            <button type="submit" class="btn-primary" :disabled="isDemo">Save</button>
          </div>
        </form>
        <p v-if="insightMessage" class="form-success">{{ insightMessage }}</p>
        <p v-if="insightError" class="form-error">{{ insightError }}</p>
      </div>
    </section>

    <section>
      <h2>
        Workout types
        <InfoHint text="Your own labels for workouts. Sync adds one automatically the first time it sees an unfamiliar activity — combine two if you end up with near-duplicates, or if a new one should have joined an existing group." />
      </h2>
      <div class="card card-fit">
        <div class="types-list">
          <span v-for="type in types" :key="type.id" class="type-tag"
                :class="{ 'pending-review': type.pendingReview, merging: mergeSource?.id === type.id }">
            <span class="type-dot" :style="{ backgroundColor: type.colorHex }"></span>
            {{ type.name }}
            <span v-if="type.pendingReview" class="new-badge" title="Created automatically by sync">new</span>
            <button v-if="types.length > 1" class="btn-icon" title="Combine with another type"
                    @click="mergeSource = mergeSource?.id === type.id ? null : type">
              <Combine :size="12" />
            </button>
            <button class="btn-icon" title="Delete type" @click="deleteType(type)"><X :size="12" /></button>
          </span>
          <span v-if="types.length === 0" class="empty-line">No types yet.</span>
        </div>

        <!-- Shown only for the type being combined, so the explanation appears once and at the
             moment it is needed, rather than as a dropdown on every tag saying nothing. -->
        <div v-if="mergeSource" class="merge-panel">
          <p class="merge-explain">
            Combine <strong>{{ mergeSource.name }}</strong> into another type. Its workouts and
            Health Connect mappings move across, and <strong>{{ mergeSource.name }}</strong> is
            removed. Nothing is deleted.
          </p>
          <div class="merge-actions">
            <select v-model="mergeTargetId" class="type-input">
              <option :value="null" disabled>Keep as…</option>
              <option v-for="other in types.filter(t => t.id !== mergeSource.id)" :key="other.id" :value="other.id">
                {{ other.name }}
              </option>
            </select>
            <button class="btn-small" :disabled="!mergeTargetId" @click="confirmMerge">Combine</button>
            <button class="btn-small btn-danger" @click="cancelMerge">Cancel</button>
          </div>
        </div>
        <div v-if="types.length < PALETTE.length" class="type-add">
          <div class="color-picker">
            <button
              v-for="(hex, i) in PALETTE"
              :key="i"
              type="button"
              class="color-dot"
              :class="{ selected: newTypeColor === hex }"
              :style="{ backgroundColor: hex }"
              @click="newTypeColor = hex"
            ></button>
          </div>
          <input v-model="newTypeName" placeholder="Type name" class="type-input" />
          <button class="btn-small" @click="addType">Add</button>
        </div>
        <p v-else class="muted-note">All 5 types in use.</p>
        <p v-if="typeError" class="form-error">{{ typeError }}</p>
      </div>
    </section>

    <section class="section-wide">
      <h2>
        Health Connect mappings
        <InfoHint text="Which of your workout types each Health Connect activity is logged as. Anything not listed gets a type of its own the first time it arrives, so nothing is ever dropped — map activities to group them together, or to ignore one entirely." />
      </h2>
      <div class="card card-fit">
        <!-- Only the mappings that represent a decision. A mapping whose type simply carries
             the Health Connect name is what auto-create writes by itself, so listing it beside
             a real relabel buries the one row that says anything. -->
        <table v-if="decidedMappings.length" class="mappings-table">
          <tbody>
            <tr v-for="m in decidedMappings" :key="m.healthConnectType">
              <td>
                {{ exerciseTypeName(m.healthConnectType) }}
                <span class="hc-code">{{ m.healthConnectType }}</span>
              </td>
              <td class="mapping-arrow">→</td>
              <td>
                <template v-if="m.workoutType">
                  <span class="type-dot" :style="{ backgroundColor: m.workoutType.colorHex }"></span>
                  {{ m.workoutType.name }}
                </template>
                <span v-else class="ignored-tag">Never logged</span>
              </td>
              <td>
                <button class="btn-icon" title="Remove mapping" @click="deleteMappingHandler(m.healthConnectType)"><X :size="12" /></button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else-if="!defaultMappings.length" class="empty-line">No mappings yet.</p>

        <div v-if="defaultMappings.length" class="defaults-block">
          <button class="defaults-toggle" @click="showDefaults = !showDefaults">
            <ChevronRight :size="13" :class="{ open: showDefaults }" />
            {{ defaultMappings.length }}
            {{ defaultMappings.length === 1 ? 'activity uses its' : 'activities use their' }} Health Connect name
          </button>
          <table v-if="showDefaults" class="mappings-table defaults-table">
            <tbody>
              <tr v-for="m in defaultMappings" :key="m.healthConnectType">
                <td>
                  {{ exerciseTypeName(m.healthConnectType) }}
                  <span class="hc-code">{{ m.healthConnectType }}</span>
                </td>
                <td>
                  <span class="type-dot" :style="{ backgroundColor: m.workoutType.colorHex }"></span>
                  {{ m.workoutType.name }}
                </td>
                <td>
                  <button class="btn-icon" title="Remove mapping" @click="deleteMappingHandler(m.healthConnectType)"><X :size="12" /></button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="mapping-add">
          <select v-model.number="newMappingType" class="wide-input">
            <option :value="null" disabled>Select an activity</option>
            <option v-for="option in exerciseTypes" :key="option.code" :value="option.code">
              {{ option.name }} ({{ option.code }})
            </option>
          </select>
          <select v-model="newMappingWorkoutTypeId" class="type-input">
            <option :value="null" disabled>Select workout type</option>
            <option v-for="type in types" :key="type.id" :value="type.id">{{ type.name }}</option>
            <option :value="IGNORE">Ignore this activity</option>
          </select>
          <button class="btn-small" @click="addMappingHandler" :disabled="newMappingType === null || !newMappingWorkoutTypeId">Add</button>
        </div>
        <p v-if="mappingError" class="form-error">{{ mappingError }}</p>
      </div>
    </section>
    <section class="section-wide">
      <h2>
        Sync sources
        <InfoHint text="Devices and apps that have sent workouts to Atlas. Nothing is logged until you enable its source. Anything received meanwhile is held rather than discarded, because the sender only transmits new changes and will not send them again." />
      </h2>
      <div class="card card-fit">
        <!-- Six columns will not fit a narrow window. Scroll the table, never the page. -->
        <div v-if="sources.length" class="table-scroll">
        <table class="mappings-table">
          <thead>
            <tr>
              <th>Source</th><th>Recording</th><th>First seen</th><th>Last seen</th><th>Held</th><th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in sources" :key="s.dataOrigin + s.recordingMethod">
              <td class="data-value">{{ s.dataOrigin }}</td>
              <td>{{ s.recordingMethod }}</td>
              <td>{{ shortDate(s.firstSeen) }}</td>
              <td>{{ shortDate(s.lastSeen) }}</td>
              <td class="data-value">{{ s.quarantinedCount || '—' }}</td>
              <!-- Live in the demo on purpose: enabling a held source and watching the backfill
                   arrive is the only way the quarantine design is visible at all. -->
              <td class="source-actions">
                <button v-if="!s.allowed" class="btn-small" @click="enableSource(s)">Enable</button>
                <button v-else class="btn-small btn-danger" @click="disableSource(s)">Disable</button>
                <button v-if="s.quarantinedCount" class="btn-small btn-danger" @click="dismissHeld(s)">Dismiss</button>
              </td>
            </tr>
          </tbody>
        </table>
        </div>
        <p v-else class="empty-line">No sources yet — they appear the first time a device sends a workout.</p>
        <p v-if="sourceMessage" class="form-success">{{ sourceMessage }}</p>
        <p v-if="sourceError" class="form-error">{{ sourceError }}</p>
      </div>
    </section>

    <section v-if="isDemo">
      <h2>
        Demo data
        <InfoHint text="This is a demo — your changes are stored in this browser only, and never leave it." />
      </h2>
      <div class="card card-fit">
        <button class="btn-small btn-danger" @click="resetDemo">Reset demo data</button>
      </div>
    </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import InfoHint from '../components/InfoHint.vue'
import { useSettingsStore } from '../stores/settings'
import { useToastStore } from '../stores/toast'
import { getWorkoutTypes, createWorkoutType, deleteWorkoutType, mergeWorkoutType } from '../services/workoutService'
import {
  getMappings, addMapping, deleteMapping,
  getExerciseTypes, getSyncSources, setSyncSourceAllowed, dismissQuarantine
} from '../services/syncService'
import { formatDateBr } from '../utils/date'
import { Target, X, Sparkles, KeyRound, Box, ChevronRight, Combine, Ruler } from 'lucide-vue-next'

const isDemo = import.meta.env.MODE === 'demo'

async function resetDemo() {
  if (!confirm('Reset all demo data to the seeded state?')) return
  const { resetDemoData } = await import('../demo/demoApi')
  resetDemoData()
}

const toast = useToastStore()
const settingsStore = useSettingsStore()

const PALETTE = ['#4F8DFF', '#8B5CF6', '#2DD4BF', '#F472B6', '#FACC15']

const loading = ref(true)
const types = ref([])
const mappings = ref([])

const target = ref(4)
const targetMessage = ref('')
const targetError = ref('')

const unitSystem = computed(() => settingsStore.unitSystem)
const unitError = ref('')

async function saveUnitSystem(option) {
  if (option === unitSystem.value) return
  unitError.value = ''
  try {
    // Nothing else to do: every view reads the preference from the store, so the whole app
    // re-renders in the new units the moment this resolves.
    await settingsStore.save({ unitSystem: option })
  } catch {
    unitError.value = 'Failed to change units'
  }
}

const insightBaseUrl = ref('')
const insightModel = ref('')
const keyConfigured = ref(false)
const insightKeyLast4 = ref('')
const newInsightKey = ref('')
const replacingKey = ref(false)
const insightMessage = ref('')
const insightError = ref('')

const newTypeName = ref('')
const newTypeColor = ref(PALETTE[0])
const typeError = ref('')

// Sentinel for the mapping dropdown: a mapping to no workout type means "never log this".
const IGNORE = 'IGNORE'

const newMappingType = ref(null)
const newMappingWorkoutTypeId = ref(null)
const mappingError = ref('')
const exerciseTypes = ref([])

const sources = ref([])
const sourceMessage = ref('')
const sourceError = ref('')
const showDefaults = ref(false)

function exerciseTypeName(code) {
  return exerciseTypes.value.find(t => t.code === code)?.name ?? `Activity ${code}`
}

/** A mapping says something only when it renames the activity, or silences it. */
function isDecision(m) {
  return !m.workoutType || m.workoutType.name !== exerciseTypeName(m.healthConnectType)
}

const decidedMappings = computed(() => mappings.value.filter(isDecision))
const defaultMappings = computed(() => mappings.value.filter(m => !isDecision(m)))

function shortDate(value) {
  return value ? formatDateBr(String(value).split('T')[0]) : '—'
}

async function enableSource(source) {
  sourceMessage.value = ''
  sourceError.value = ''
  if (source.quarantinedCount && !confirm(
    `Enable ${source.dataOrigin}?\n\n` +
    `${source.quarantinedCount} held workout(s) will be added.\n\n` +
    'Atlas deduplicates workouts by their exact start time. A source that revises timestamps ' +
    'between syncs will create duplicate entries.'
  )) return

  try {
    const { data } = await setSyncSourceAllowed(source.dataOrigin, source.recordingMethod, true)
    await load()
    sourceMessage.value = data.created
      ? `Added ${data.created} workout(s) from ${source.dataOrigin}`
      : `${source.dataOrigin} enabled`
    setTimeout(() => { sourceMessage.value = '' }, 5000)
  } catch {
    sourceError.value = 'Failed to enable source'
  }
}

async function disableSource(source) {
  // Existing workouts are kept: turning a source off is not a claim its history was wrong.
  if (!confirm(`Stop logging workouts from ${source.dataOrigin}? Existing workouts are kept.`)) return
  sourceError.value = ''
  try {
    await setSyncSourceAllowed(source.dataOrigin, source.recordingMethod, false)
    await load()
  } catch {
    sourceError.value = 'Failed to disable source'
  }
}

async function dismissHeld(source) {
  if (!confirm(`Discard ${source.quarantinedCount} held workout(s) from ${source.dataOrigin}? This cannot be undone.`)) return
  sourceError.value = ''
  try {
    await dismissQuarantine(source.dataOrigin, source.recordingMethod)
    await load()
  } catch {
    sourceError.value = 'Failed to dismiss held workouts'
  }
}

// The panel states what will happen, so a second confirm() dialog restating it adds nothing.
const mergeSource = ref(null)
const mergeTargetId = ref(null)

function cancelMerge() {
  mergeSource.value = null
  mergeTargetId.value = null
}

async function confirmMerge() {
  const target = types.value.find(t => t.id === mergeTargetId.value)
  typeError.value = ''
  try {
    await mergeWorkoutType(mergeSource.value.id, mergeTargetId.value)
    cancelMerge()
    await load()
    toast.success(`Combined into ${target.name}`)
  } catch {
    typeError.value = 'Could not combine types.'
  }
}

async function load() {
  try {
    const [settings, typesRes, mappingsRes, catalogRes, sourcesRes] = await Promise.all([
      settingsStore.load({ force: true }),
      getWorkoutTypes(),
      getMappings(),
      getExerciseTypes(),
      getSyncSources()
    ])
    target.value = settings.targetWorkoutsPerWeek
    applyInsightSettings(settings)
    types.value = typesRes.data
    mappings.value = mappingsRes.data
    exerciseTypes.value = catalogRes.data
    sources.value = sourcesRes.data
  } catch {
    targetError.value = 'Failed to load settings'
  }
}

function applyInsightSettings(settings) {
  insightBaseUrl.value = settings.insightBaseUrl ?? ''
  insightModel.value = settings.insightModel ?? ''
  keyConfigured.value = settings.insightApiKeyConfigured ?? false
  insightKeyLast4.value = settings.insightApiKeyLast4 ?? ''
  newInsightKey.value = ''
  replacingKey.value = false
}

async function saveInsights() {
  insightMessage.value = ''
  insightError.value = ''
  try {
    // An omitted key means "leave it alone" — that is what stops saving a URL from
    // wiping a key the form never had a copy of.
    const payload = { insightBaseUrl: insightBaseUrl.value, insightModel: insightModel.value }
    if (newInsightKey.value.trim()) payload.insightApiKey = newInsightKey.value

    applyInsightSettings(await settingsStore.save(payload))
    insightMessage.value = 'Saved'
    setTimeout(() => { insightMessage.value = '' }, 3000)
  } catch {
    insightError.value = 'Failed to save insight settings'
  }
}

async function removeKey() {
  if (!confirm('Remove the stored API key? Insights will be turned off until you add another.')) return
  insightMessage.value = ''
  insightError.value = ''
  try {
    applyInsightSettings(await settingsStore.save({ clearInsightApiKey: true }))
    toast.success('API key removed')
  } catch {
    insightError.value = 'Failed to remove the API key'
  }
}

function cancelReplace() {
  newInsightKey.value = ''
  replacingKey.value = false
}

async function saveTarget() {
  targetMessage.value = ''
  targetError.value = ''
  try {
    await settingsStore.save({ targetWorkoutsPerWeek: target.value })
    targetMessage.value = 'Saved'
    setTimeout(() => { targetMessage.value = '' }, 3000)
  } catch {
    targetError.value = 'Failed to save'
  }
}

async function addType() {
  const name = newTypeName.value.trim()
  if (!name) return
  typeError.value = ''
  try {
    await createWorkoutType({ name, colorHex: newTypeColor.value })
    newTypeName.value = ''
    await load()
    toast.success('Workout type added')
  } catch {
    typeError.value = 'Could not create type.'
  }
}

async function deleteType(type) {
  if (!confirm(`Delete "${type.name}"? This will fail if it has existing workout logs or mappings.`)) return
  typeError.value = ''
  try {
    await deleteWorkoutType(type.id)
    await load()
    toast.success('Workout type deleted')
  } catch {
    typeError.value = `Cannot delete "${type.name}" — it has existing workout logs or mappings.`
  }
}

async function addMappingHandler() {
  mappingError.value = ''
  try {
    // A null workoutTypeId is the "ignore this activity" mapping, not a missing value.
    await addMapping({
      healthConnectType: newMappingType.value,
      workoutTypeId: newMappingWorkoutTypeId.value === IGNORE ? null : newMappingWorkoutTypeId.value
    })
    newMappingType.value = null
    newMappingWorkoutTypeId.value = null
    await load()
    toast.success('Mapping added')
  } catch {
    mappingError.value = 'Could not add mapping.'
  }
}

async function deleteMappingHandler(healthConnectType) {
  if (!confirm(`Delete mapping for type ${healthConnectType}?`)) return
  mappingError.value = ''
  try {
    await deleteMapping(healthConnectType)
    await load()
    toast.success('Mapping deleted')
  } catch {
    mappingError.value = 'Could not delete mapping.'
  }
}

onMounted(async () => {
  await load()
  loading.value = false
})
</script>

<style scoped>
.form-row {
  align-items: flex-end;
}

.types-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: var(--space-3);
}
.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 5px 8px 5px 12px;
  font-size: 0.85rem;
}
.type-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  vertical-align: middle;
}
.type-add {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.color-picker {
  display: flex;
  gap: 6px;
}
.color-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  transition: border-color 0.15s;
}
.color-dot.selected {
  border-color: var(--text);
}
.color-dot:hover {
  border-color: var(--text-muted);
}
.color-dot.selected:hover {
  border-color: var(--text);
}
.type-input {
  width: 160px;
}

/* Two columns at most. auto-fit gave three on a wide screen, which left the one-line
   sections stranded beside a tall neighbour with a column of dead space under them. */
.settings-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
  align-items: start;
}
@media (min-width: 900px) {
  .settings-grid { grid-template-columns: 1fr 1fr; }
}
/* min-width:0 is load-bearing: a grid item defaults to min-width:auto and refuses to shrink
   below its content, so a wide table stretches the card past the viewport and .table-scroll
   never engages. */
.settings-grid > section { margin: 0; min-width: 0; }
/* .card-fit is display:inline-block, which shrink-wraps to its content and will happily grow
   past the column — that is what pushed a wide table off-screen. Inside the grid the column
   sets the width, so the cards line up instead of each being a different size. */
.settings-grid .card-fit {
  display: block;
  width: 100%;
}

/* Headings carry their own explanation now, so they sit tighter to the card. */
.settings-grid h2 {
  display: flex;
  align-items: center;
  margin-bottom: var(--space-2);
}
/* A one-control section does not need a full card's worth of padding around it. */
.settings-grid section:not(.section-wide) .card-fit {
  padding: var(--space-3);
}
.settings-grid .form-row {
  margin: 0;
}

.pref-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-2) 0;
}
.pref-row + .pref-row {
  border-top: 1px solid var(--border);
}
.pref-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.9rem;
  color: var(--text);
}

.merge-panel {
  border-top: 1px solid var(--border);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
}
.merge-explain {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin: 0 0 var(--space-2);
  max-width: 46ch;
}
.merge-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.type-tag.merging {
  border-color: var(--blue);
}

.inline-form {
  display: flex;
  align-items: center;
  gap: 8px;
}
.inline-icon { color: var(--text-muted); flex-shrink: 0; }
.target-input {
  width: 56px;
  text-align: center;
}
.inline-label {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-right: auto;
}
/* Tables need the row; everything else pairs up. */
.section-wide { grid-column: 1 / -1; }

/* Wide content scrolls inside its own box so the page body never scrolls sideways. */
.table-scroll {
  overflow-x: auto;
  margin-bottom: var(--space-3);
}
.table-scroll .mappings-table { margin-bottom: 0; }

.mapping-arrow {
  color: var(--text-muted);
  padding: 0 var(--space-2);
  width: 1px;
}

.defaults-block { margin-top: var(--space-2); }
.defaults-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 0.8rem;
  padding: 4px 0;
  cursor: pointer;
}
.defaults-toggle:hover { color: var(--text); }
.defaults-toggle svg { transition: transform 0.15s ease; }
.defaults-toggle svg.open { transform: rotate(90deg); }
.defaults-table { margin-top: var(--space-2); opacity: 0.75; }

.hc-code {
  font-family: var(--font-mono, monospace);
  font-size: 0.7rem;
  color: var(--text-muted);
  margin-left: 6px;
}
.ignored-tag {
  font-size: 0.75rem;
  color: var(--text-muted);
  border: 1px dashed var(--border);
  border-radius: 4px;
  padding: 1px 6px;
}
.source-actions {
  display: flex;
  gap: 6px;
  white-space: nowrap;
}
.type-tag.pending-review {
  border-color: var(--blue);
}
.new-badge {
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--blue);
  border: 1px solid currentColor;
  border-radius: 4px;
  padding: 0 4px;
}
.merge-select {
  font-size: 0.7rem;
  padding: 1px 4px;
  max-width: 90px;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-muted);
  border-radius: 4px;
}

.unit-toggle {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}
.unit-option {
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 9px 16px;
  font-size: 0.85rem;
  cursor: pointer;
}
.unit-option:hover { color: var(--text); }
.unit-option.active {
  background: var(--blue);
  color: var(--bg);
}

.insight-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  align-items: flex-start;
}
.wide-input {
  width: 340px;
  max-width: 100%;
}
.key-state {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.key-mask {
  font-family: var(--font-mono, monospace);
  font-size: 0.85rem;
  color: var(--text-muted);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 8px 12px;
}

.section-desc {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0 0 var(--space-3);
  max-width: 520px;
}

.mappings-table {
  margin-bottom: var(--space-3);
}
.mappings-table .type-dot {
  margin-right: 6px;
}

.mapping-add {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.btn-small {
  background: var(--blue);
  color: var(--bg);
  border: none;
  padding: 9px 14px;
  font-size: 0.85rem;
  border-radius: 8px;
  cursor: pointer;
}
.btn-small:hover {
  filter: brightness(1.1);
}
.btn-small:disabled {
  opacity: 0.3;
  cursor: default;
}
/* Was already used by "Reset demo data" without ever being defined. */
.btn-small.btn-danger {
  background: transparent;
  color: var(--red, #ef4444);
  border: 1px solid currentColor;
}
.btn-small.btn-danger:hover {
  background: rgba(239, 68, 68, 0.1);
  filter: none;
}
.btn-icon {
  display: flex;
  align-items: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
  cursor: pointer;
}
.btn-icon:hover {
  color: var(--orange);
  background: rgba(251, 146, 60, 0.12);
}
.muted-note {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin: 0;
}
.empty-line {
  color: var(--text-muted);
  font-size: 0.85rem;
}
</style>
