<template>
  <div v-if="loading" class="loading"></div>
  <div v-else class="page">
    <h1>Body Metrics</h1>

    <section>
      <h2>Add new measurement</h2>
      <div class="card">
        <BodyMetricsForm @logged="onLogged" />
      </div>
    </section>

    <section v-if="metrics.length >= 2">
      <h2>Latest measurement</h2>
      <div class="stat-grid">
        <StatCard
          label="Weight"
          :value="`${latest.weightKg} Kg`"
          color="blue"
          :icon="Scale"
          :subtitle="deltaText('weightKg', 'kg')"
        />
        <StatCard
          label="Body Water"
          :value="`${latest.waterLiters} L`"
          color="blue"
          :icon="Droplets"
          :subtitle="deltaText('waterLiters', 'L')"
        />
        <StatCard
          label="Muscle Mass"
          :value="`${latest.muscleMassKg} Kg`"
          :color="deltaColor('muscleMassKg')"
          :icon="Dumbbell"
          :subtitle="deltaText('muscleMassKg', 'kg')"
        />
        <StatCard
          label="Body Fat Mass"
          :value="`${latest.bodyFatKg} Kg`"
          :color="deltaColor('bodyFatKg')"
          :icon="ChartPie"
          :subtitle="deltaText('bodyFatKg', 'kg')"
        />
        <StatCard
          label="Body Fat Percentage"
          :value="`${latest.bodyFatPct} %`"
          :color="deltaColor('bodyFatPct')"
          :icon="Percent"
          :subtitle="deltaText('bodyFatPct', '%')"
        />
      </div>
    </section>

    <section>
      <h2>Progress</h2>
      <div class="charts-grid">
        <MetricChart label="Weight" :entries="chartEntries('weightKg')" color="#4F8DFF" unit="Kg" :goal-target="activeGoalTarget('WEIGHT')" />
        <MetricChart label="Muscle Mass" :entries="chartEntries('muscleMassKg')" color="#3DD68C" unit="Kg" :goal-target="activeGoalTarget('MUSCLE_MASS')" />
        <MetricChart label="Body Water" :entries="chartEntries('waterLiters')" color="#2DD4BF" unit="L" :goal-target="activeGoalTarget('WATER')" />
        <MetricChart label="Body Fat Mass" :entries="chartEntries('bodyFatKg')" color="#FB923C" unit="Kg" :goal-target="activeGoalTarget('BODY_FAT_KG')" />
        <MetricChart label="Body Fat Percentage" :entries="chartEntries('bodyFatPct')" color="#FB3C3C" unit="%" :goal-target="activeGoalTarget('BODY_FAT_PCT')" />
      </div>
    </section>

    <section>
      <h2>History & insights</h2>
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
                <td class="data-value">{{ m.weightKg }}Kg</td>
                <td class="data-value">{{ m.waterLiters }}L</td>
                <td class="data-value">{{ m.muscleMassKg }}Kg</td>
                <td class="data-value">{{ m.bodyFatKg }}Kg</td>
                <td class="data-value">{{ m.bodyFatPct }}%</td>
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
      <p v-else class="empty">No measurements yet.</p>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getBodyMetrics, deleteBodyMetrics, updateBodyMetrics } from '../services/bodyMetricsService'
import { getGoals } from '../services/goalsService'
import BodyMetricsForm from '../components/BodyMetricsForm.vue'
import MetricChart from '../components/MetricChart.vue'
import StatCard from '../components/StatCard.vue'
import DatePicker from '../components/DatePicker.vue'
import { formatDateBr } from '../utils/date'
import { Scale, Droplets, Dumbbell, ChartPie, Percent, Trash2, Pencil } from 'lucide-vue-next'

const loading = ref(true)
const metrics = ref([])
const goals = ref([])

const editingId = ref(null)
const editForm = ref({
  measuredOn: '',
  weightKg: null,
  waterLiters: null,
  muscleMassKg: null,
  bodyFatKg: null,
  bodyFatPct: null
})

async function load() {
  const [metricsRes, goalsRes] = await Promise.all([getBodyMetrics(), getGoals()])
  metrics.value = Array.isArray(metricsRes.data) ? metricsRes.data : (metricsRes.data.content || [])
  goals.value = goalsRes.data
}

onMounted(async () => {
  await load()
  loading.value = false
})

function onLogged() {
  load()
}

async function deleteEntry(id) {
  if (!confirm('Delete this measurement entry?')) return
  try {
    await deleteBodyMetrics(id)
    await load()
  } catch (err) {
    console.error('Delete entry error:', err.response?.status, err.message)
  }
}

function startEdit(m) {
  editingId.value = m.id
  editForm.value = {
    measuredOn: m.measuredOn,
    weightKg: m.weightKg,
    waterLiters: m.waterLiters,
    muscleMassKg: m.muscleMassKg,
    bodyFatKg: m.bodyFatKg,
    bodyFatPct: m.bodyFatPct
  }
}

function cancelEdit() {
  editingId.value = null
}

async function saveEdit() {
  try {
    await updateBodyMetrics(editingId.value, {
      measuredOn: editForm.value.measuredOn,
      weightKg: editForm.value.weightKg,
      waterLiters: editForm.value.waterLiters,
      muscleMassKg: editForm.value.muscleMassKg,
      bodyFatKg: editForm.value.bodyFatKg,
      bodyFatPct: editForm.value.bodyFatPct
    })
    editingId.value = null
    await load()
  } catch (err) {
    console.error('Update entry error:', err.response?.status, err.message)
  }
}

const latest = computed(() => metrics.value[metrics.value.length - 1] || {})
const previous = computed(() => metrics.value.length >= 2 ? metrics.value[metrics.value.length - 2] : null)

function deltaValue(field) {
  if (!previous.value) return null
  const diff = latest.value[field] - previous.value[field]
  return diff
}

function deltaText(field, unit) {
  const diff = deltaValue(field)
  if (diff == null) return ''
  const sign = diff > 0 ? '+' : ''
  return `${sign}${diff.toFixed(1)} ${unit} since previous`
}

function deltaColor(field) {
  const diff = deltaValue(field)
  if (diff == null) return 'blue'
  // muscle: gain good, body fat: loss good
  if (field === 'muscleMassKg') return diff > 0 ? 'green' : 'orange'
  if (field === 'bodyFatKg' || field === 'bodyFatPct') return diff < 0 ? 'green' : 'orange'
  return 'blue'
}

function activeGoalTarget(metricType) {
  const goal = goals.value.find(g => g.metricType === metricType && g.status === 'ACTIVE')
  return goal ? goal.targetValue : null
}

function chartEntries(field) {
  return metrics.value.map(m => ({ measuredOn: m.measuredOn, value: m[field] }))
}
</script>

<style scoped>
.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: var(--space-4);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
}
.table-card {
  overflow-x: auto;
}
tbody tr:hover {
  background: rgba(237, 239, 244, 0.03);
}
.empty {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.edit-row {
  background: rgba(79, 141, 255, 0.06);
}
.edit-row td {
  padding: 2px 6px;
}
.edit-input {
  width: 75px;
  padding: 5px 6px;
  font-size: 0.8rem;
}
.edit-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: stretch;
}
.btn-small {
  padding: 4px 8px;
  font-size: 0.75rem;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  white-space: nowrap;
}
.btn-small.save {
  background: var(--green);
  color: var(--bg);
}
.btn-small.save:hover { filter: brightness(1.1); }
.btn-small.cancel {
  background: transparent;
  color: var(--text-muted);
  border: 1px solid var(--border);
}
.btn-small.cancel:hover {
  color: var(--text);
  border-color: var(--border-hover);
}
.actions-cell {
  display: flex;
  gap: 4px;
}
.btn-icon {
  display: flex;
  align-items: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
}
.btn-icon:hover {
  color: var(--orange);
  background: rgba(251, 146, 60, 0.12);
}
</style>
