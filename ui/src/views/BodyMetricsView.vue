<template>
  <div v-if="loading" class="page">
    <h1>Body Metrics</h1>
    <section>
      <div class="card"><BodyMetricsForm @logged="onLogged" /></div>
    </section>
    <section>
      <div class="card"><SkeletonLoader height="120px" /></div>
    </section>
    <section>
      <SkeletonLoader height="2rem" width="300px" />
    </section>
    <section>
      <div class="charts-grid">
        <div class="card" v-for="i in 5" :key="i"><SkeletonLoader height="200px" /></div>
      </div>
    </section>
  </div>
  <div v-else class="page">
    <h1>Body Metrics</h1>

    <section>
      <div class="card">
        <BodyMetricsForm @logged="onLogged" />
      </div>
    </section>

    <section v-if="metrics.length >= 2">
      <LatestMeasurementStats :metrics="metrics" :show-sparklines="false" />
    </section>

    <section v-if="latestInsight">
      <InsightCard :insight="latestInsight" :loading="insightLoading" @regenerate="refreshInsight" />
    </section>

    <section>
      <div class="range-row">
        <button
          v-for="preset in bodyPresets"
          :key="preset.months"
          class="range-preset"
          :class="{ active: bodyPreset === preset.months }"
          @click="setBodyPreset(preset.months)"
        >{{ preset.label }}</button>
      </div>
      <div v-if="filteredMetrics.length >= 2" class="metrics-delta-row">
        <div class="overview-card">
          <CalendarCheck :size="16" />
          <span class="overview-value data-value">{{ filteredMetrics.length }}</span>
          <span class="overview-label">measurements</span>
        </div>
        <div v-for="d in deltas" :key="d.label" class="overview-card" :class="d.color">
          <component :is="d.icon" :size="16" />
          <span class="overview-value data-value">{{ d.deltaStr }}</span>
          <span class="overview-label">{{ d.label }}</span>
        </div>
      </div>
      <div v-if="filteredMetrics.length" class="charts-grid">
        <MetricChart label="Weight" :entries="chartEntries('weightKg')" color="#4F8DFF" :unit="label('WEIGHT')" :goal-target="activeGoalTarget('WEIGHT')" />
        <MetricChart label="Muscle Mass" :entries="chartEntries('muscleMassKg')" color="#3DD68C" :unit="label('MUSCLE_MASS')" :goal-target="activeGoalTarget('MUSCLE_MASS')" />
        <MetricChart label="Body Water" :entries="chartEntries('waterLiters')" color="#2DD4BF" :unit="label('WATER')" :goal-target="activeGoalTarget('WATER')" />
        <MetricChart label="Body Fat Mass" :entries="chartEntries('bodyFatKg')" color="#FB923C" :unit="label('BODY_FAT_KG')" :goal-target="activeGoalTarget('BODY_FAT_KG')" />
        <MetricChart label="Body Fat Percentage" :entries="chartEntries('bodyFatPct')" color="#FB3C3C" :unit="label('BODY_FAT_PCT')" :goal-target="activeGoalTarget('BODY_FAT_PCT')" />
      </div>
      <EmptyState
        v-else
        :icon="Scale"
        title="No data yet"
        description="Add your first body measurement above to see progress charts."
      />
    </section>

    <section>
      <h2 class="collapsible-header" @click="historyCollapsed = !historyCollapsed">
        <ChevronRight v-if="historyCollapsed" :size="14" class="collapse-icon" />
        <ChevronDown v-else :size="14" class="collapse-icon" />
        History
      </h2>
      <div v-show="!historyCollapsed">
      <div v-if="metrics.length" class="card table-card">
        <table>
          <thead>
            <tr>
              <th>Date</th><th>Weight</th><th>Body Water</th><th>Muscle Mass</th><th>Body Fat Mass</th><th>Body Fat Percentage</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="m in [...metrics].reverse()" :key="m.id">
              <tr v-if="editingId !== m.id">
                <td>{{ formatDateBr(m.measuredOn) }}</td>
                <td class="data-value">{{ formatWithUnit(m.weightKg, 'weightKg') }}</td>
                <td class="data-value">{{ formatWithUnit(m.waterLiters, 'waterLiters') }}</td>
                <td class="data-value">{{ formatWithUnit(m.muscleMassKg, 'muscleMassKg') }}</td>
                <td class="data-value">{{ formatWithUnit(m.bodyFatKg, 'bodyFatKg') }}</td>
                <td class="data-value">{{ formatWithUnit(m.bodyFatPct, 'bodyFatPct') }}</td>
                <td class="actions-cell">
                  <button class="btn-icon" title="Edit entry" @click="startEdit(m)"><Pencil :size="14" /></button>
                  <button class="btn-icon" title="Delete entry" @click="deleteEntry(m.id)"><Trash2 :size="14" /></button>
                </td>
              </tr>
              <tr v-else class="edit-row">
                <td><DatePicker v-model="editForm.measuredOn" /></td>
                <td><input type="number" step="0.1" v-model="editForm.weightKg" class="edit-input" /></td>
                <td><input type="number" step="0.1" v-model="editForm.waterLiters" class="edit-input" /></td>
                <td><input type="number" step="0.1" v-model="editForm.muscleMassKg" class="edit-input" /></td>
                <td><input type="number" step="0.1" v-model="editForm.bodyFatKg" class="edit-input" /></td>
                <td><input type="number" step="0.1" v-model="editForm.bodyFatPct" class="edit-input" /></td>
                <td class="edit-actions">
                  <button class="btn-small save" @click="saveEdit">Save</button>
                  <button class="btn-small cancel" @click="cancelEdit">Cancel</button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-else
        :icon="Scale"
        title="No measurements yet"
        description="Add your first body measurement above to see progress charts and insights."
      />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getBodyMetrics, deleteBodyMetrics, updateBodyMetrics } from '../services/bodyMetricsService'
import { getGoals } from '../services/goalsService'
import { getInsights, regenerateInsights } from '../services/insightService'
import { useToastStore } from '../stores/toast'
import { useUnits } from '../composables/useUnits'
import { openInsightGate } from '../demo/insightGate'
import BodyMetricsForm from '../components/BodyMetricsForm.vue'
import MetricChart from '../components/MetricChart.vue'
import InsightCard from '../components/InsightCard.vue'
import LatestMeasurementStats from '../components/LatestMeasurementStats.vue'
import EmptyState from '../components/EmptyState.vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import DatePicker from '../components/DatePicker.vue'
import { formatDateBr, toLocalDateStr } from '../utils/date'
import { Scale, Trash2, Pencil, ChevronRight, ChevronDown, CalendarCheck, Dumbbell, Droplets, ChartPie, Percent } from 'lucide-vue-next'

const toast = useToastStore()
const { label, toDisplay, format, formatWithUnit, toCanonicalPreservingUnedited } = useUnits()

const loading = ref(true)
const metrics = ref([])
const goals = ref([])
const insight = ref(null)
const insightLoading = ref(false)
const historyCollapsed = ref(true)

const bodyPreset = ref(0)
const bodyPresets = [
  { months: 6, label: 'Last 6 months' },
  { months: 12, label: 'Last year' },
  { months: 0, label: 'All time' }
]

const bodyRangeStart = ref(null)
const bodyRangeEnd = ref(null)

function setBodyPreset(months) {
  bodyPreset.value = months
  if (months === 0) {
    bodyRangeStart.value = null
    bodyRangeEnd.value = null
  } else {
    const start = new Date()
    start.setMonth(start.getMonth() - months)
    bodyRangeStart.value = toLocalDateStr(start)
    bodyRangeEnd.value = toLocalDateStr(new Date())
  }
}

const filteredMetrics = computed(() => {
  if (!bodyRangeStart.value && !bodyRangeEnd.value) return metrics.value
  return metrics.value.filter(m => {
    if (bodyRangeStart.value && m.measuredOn < bodyRangeStart.value) return false
    if (bodyRangeEnd.value && m.measuredOn > bodyRangeEnd.value) return false
    return true
  })
})

const deltas = computed(() => {
  const list = filteredMetrics.value
  if (list.length < 2) return []
  const first = list[0]
  const last = list[list.length - 1]
  return [
    { label: 'Weight', field: 'weightKg', good: null, icon: Scale },
    { label: 'Water', field: 'waterLiters', good: null, icon: Droplets },
    { label: 'Muscle', field: 'muscleMassKg', good: 'up', icon: Dumbbell },
    { label: 'Body fat mass', field: 'bodyFatKg', good: 'down', icon: ChartPie },
    { label: 'Body fat %', field: 'bodyFatPct', good: 'down', icon: Percent }
  ].map(({ label, field, good, icon }) => {
    const diff = (last[field] ?? 0) - (first[field] ?? 0)
    const sign = diff >= 0 ? '+' : ''
    const deltaStr = `${sign}${formatWithUnit(diff, field)}`
    let color = ''
    if (good === 'up') color = diff > 0 ? 'positive' : diff < 0 ? 'negative' : ''
    else if (good === 'down') color = diff < 0 ? 'positive' : diff > 0 ? 'negative' : ''
    return { label, deltaStr, color, icon }
  })
})

const latestInsight = computed(() => {
  if (!insight.value?.text) return null
  const [datePart] = (insight.value.generatedAt || '').split('T')
  return {
    text: insight.value.text,
    verdict: insight.value.verdict || null,
    date: datePart,
    state: insight.value.state ?? 'OK'
  }
})

const editingId = ref(null)
const editOriginal = ref(null)
const editForm = ref({
  measuredOn: '',
  weightKg: null,
  waterLiters: null,
  muscleMassKg: null,
  bodyFatKg: null,
  bodyFatPct: null
})

async function load() {
  try {
    const [metricsRes, goalsRes, insightRes] = await Promise.all([getBodyMetrics(), getGoals(), getInsights()])
    metrics.value = Array.isArray(metricsRes.data) ? metricsRes.data : (metricsRes.data.content || [])
    goals.value = goalsRes.data
    insight.value = insightRes.data
  } catch {
    metrics.value = []
    goals.value = []
    insight.value = null
  }
}

onMounted(async () => {
  await load()
  loading.value = false
})

function onLogged() { load() }

async function refreshInsight() {
  if (import.meta.env.MODE === 'demo') {
    openInsightGate()
    return
  }
  insightLoading.value = true
  try {
    const res = await regenerateInsights()
    insight.value = res.data
    toast.success('Insight regenerated')
  } catch {
    toast.error('Failed to regenerate insight')
  }
  insightLoading.value = false
}

async function deleteEntry(id) {
  if (!confirm('Delete this measurement entry?')) return
  try { await deleteBodyMetrics(id); await load(); toast.success('Measurement deleted') }
  catch { toast.error('Failed to delete measurement') }
}

const METRIC_FIELDS = ['weightKg', 'waterLiters', 'muscleMassKg', 'bodyFatKg', 'bodyFatPct']

function startEdit(m) {
  editingId.value = m.id
  // Kept so saveEdit can tell an untouched field from an edited one — see below.
  editOriginal.value = m
  editForm.value = { measuredOn: m.measuredOn }
  for (const field of METRIC_FIELDS) {
    const shown = format(m[field], field)
    editForm.value[field] = shown == null ? null : Number(shown)
  }
}

function cancelEdit() {
  editingId.value = null
  editOriginal.value = null
}

async function saveEdit() {
  try {
    const payload = { measuredOn: editForm.value.measuredOn }
    for (const field of METRIC_FIELDS) {
      // The form holds displayed values rounded to 1 dp, so converting them straight back
      // would rewrite every field of an untouched row — 82.3 kg shows as 181.4 lb and
      // returns as 82.28. Fields the user did not actually change keep their stored value.
      payload[field] = toCanonicalPreservingUnedited(
        editForm.value[field], editOriginal.value?.[field], field)
    }

    await updateBodyMetrics(editingId.value, payload)
    editingId.value = null
    editOriginal.value = null
    await load()
    toast.success('Measurement updated')
  } catch { toast.error('Failed to update measurement') }
}

function activeGoalTarget(metricType) {
  const goal = goals.value.find(g => g.metricType === metricType && g.status === 'ACTIVE')
  return goal ? toDisplay(goal.targetValue, metricType) : null
}

// Charts plot whatever they are handed, so the series is converted here rather than in
// MetricChart. The goal-target line below must use the same conversion or it renders at the
// wrong height against a converted series.
function chartEntries(field) {
  return filteredMetrics.value.map(m => ({ measuredOn: m.measuredOn, value: toDisplay(m[field], field) }))
}
</script>

<style scoped>
.range-row {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-bottom: var(--space-3);
}
.range-preset {
  font-size: 0.8rem;
  padding: 5px 12px;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-muted);
  border-radius: 6px;
  cursor: pointer;
}
.range-preset:hover { border-color: var(--border-hover); color: var(--text); }
.range-preset.active { background: var(--blue); border-color: var(--blue); color: var(--bg); }

.metrics-delta-row {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
}
.overview-card {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--text-muted);
}
.overview-card.positive { border-color: rgba(61, 214, 140, 0.25); background: linear-gradient(180deg, rgba(61, 214, 140, 0.06) 0%, transparent 60%); }
.overview-card.negative { border-color: rgba(251, 146, 60, 0.25); background: linear-gradient(180deg, rgba(251, 146, 60, 0.06) 0%, transparent 60%); }
.overview-card.positive .overview-value { color: var(--green); }
.overview-card.negative .overview-value { color: var(--orange); }
.overview-value {
  font-family: var(--font-data);
  font-weight: 600;
  color: var(--text);
}
.overview-label {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: var(--space-4);
}
.btn-icon {
  display: flex; align-items: center; background: transparent; border: none;
  color: var(--text-muted); padding: 4px; border-radius: 4px; cursor: pointer;
  margin-left: auto;
}
.btn-icon:hover { color: var(--text); background: var(--bg); }
.btn-icon:disabled { opacity: 0.4; cursor: default; }

.collapsible-header {
  display: flex; align-items: center; gap: 6px;
  cursor: pointer; user-select: none; color: var(--text-muted);
}
.collapsible-header:hover { color: var(--text); }
.collapse-icon { flex-shrink: 0; }

.table-card { overflow-x: auto; }
tbody tr:hover { background: rgba(237, 239, 244, 0.03); }
.edit-row { background: rgba(79, 141, 255, 0.06); }
.edit-row td { padding: 2px 6px; }
.edit-input { width: 75px; padding: 5px 6px; font-size: 0.8rem; }
.edit-actions { display: flex; flex-direction: column; gap: 4px; align-items: stretch; }
.btn-small { padding: 4px 8px; font-size: 0.75rem; border-radius: 6px; border: none; cursor: pointer; white-space: nowrap; }
.btn-small.save { background: var(--green); color: var(--bg); }
.btn-small.save:hover { filter: brightness(1.1); }
.btn-small.cancel { background: transparent; color: var(--text-muted); border: 1px solid var(--border); }
.btn-small.cancel:hover { color: var(--text); border-color: var(--border-hover); }
.actions-cell { display: flex; gap: 4px; }
.btn-icon {
  display: flex; align-items: center; background: transparent; border: none;
  color: var(--text-muted); padding: 4px; border-radius: 4px; cursor: pointer;
}
.btn-icon:hover { color: var(--orange); background: rgba(251, 146, 60, 0.12); }
</style>
