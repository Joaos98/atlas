<template>
  <div>
    <h1>Goals</h1>
    <GoalForm @created="load" />

    <h2>Active goals</h2>
    <div v-for="goal in activeGoals" :key="goal.id" class="goal-card">
      <p class="metric-label">{{ metricLabel(goal.metricType) }}</p>
      <p class="data-value">
        {{ currentValue(goal.metricType) }}{{ metricUnit(goal.metricType) }}
        <span class="arrow">→</span>
        {{ goal.targetValue }}{{ metricUnit(goal.metricType) }}
      </p>
      <p v-if="goal.targetDate" class="target-date">Target date: {{ goal.targetDate }}</p>
      <div class="actions">
        <button class="achieve" @click="updateStatus(goal.id, 'ACHIEVED')">Mark achieved</button>
        <button class="abandon" @click="updateStatus(goal.id, 'ABANDONED')">Abandon</button>
      </div>
    </div>
    <p v-if="activeGoals.length === 0" class="empty">No active goals.</p>

    <h2>Past goals</h2>
    <div v-for="goal in pastGoals" :key="goal.id" class="goal-card past">
      <p class="metric-label">{{ metricLabel(goal.metricType) }}</p>
      <p class="data-value">
        {{ goal.targetValue }}{{ metricUnit(goal.metricType) }}
        <span class="status-tag" :class="goal.status.toLowerCase()">{{ goal.status.toLowerCase() }}</span>
      </p>
    </div>
    <p v-if="pastGoals.length === 0" class="empty">No past goals.</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getGoals, updateGoalStatus } from '../services/goalsService'
import GoalForm from '../components/GoalForm.vue'
import { getBodyMetrics } from '../services/bodyMetricsService'

const latestMetrics = ref(null)

const goals = ref([])

async function load() {
  const [goalsRes, metricsRes] = await Promise.all([getGoals(), getBodyMetrics()])
  goals.value = goalsRes.data
  const entries = metricsRes.data
  if (entries.length > 0) {
    latestMetrics.value = entries[entries.length - 1]
  }
}

const metricFields = {
  WEIGHT: 'weightKg', MUSCLE_MASS: 'muscleMassKg',
  WATER: 'waterLiters', BODY_FAT_KG: 'bodyFatKg', BODY_FAT_PCT: 'bodyFatPct'
}

onMounted(load)

const activeGoals = computed(() => goals.value.filter(g => g.status === 'ACTIVE'))
const pastGoals = computed(() => goals.value.filter(g => g.status !== 'ACTIVE'))

async function updateStatus(id, status) {
  await updateGoalStatus(id, status)
  await load()
}

const labels = {
  WEIGHT: 'Weight', MUSCLE_MASS: 'Muscle mass',
  WATER: 'Water', BODY_FAT_KG: 'Body fat', BODY_FAT_PCT: 'Body fat'
}
const units = {
  WEIGHT: ' kg', MUSCLE_MASS: ' kg',
  WATER: ' L', BODY_FAT_KG: ' kg', BODY_FAT_PCT: '%'
}

function metricLabel(type) { return labels[type] || type }
function metricUnit(type) { return units[type] || '' }
function currentValue(metricType) {
  if (!latestMetrics.value) return '—'
  const field = metricFields[metricType]
  return latestMetrics.value[field] ?? '—'
}
</script>
<style scoped>
.goal-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-left: 3px solid var(--purple);
  border-radius: 8px;
  padding: 14px 18px;
  margin-bottom: 12px;
  max-width: 400px;
}
.goal-card.past {
  border-left-color: var(--border);
}
.metric-label {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0 0 4px;
}
.arrow {
  color: var(--text-muted);
  margin: 0 6px;
}
.target-date {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin: 4px 0 0;
}
.actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.achieve {
  background: var(--green);
  color: var(--bg);
  border: none;
  font-size: 0.8rem;
}
.abandon {
  background: transparent;
  color: var(--text-muted);
  font-size: 0.8rem;
}
.status-tag {
  font-family: var(--font-body);
  font-size: 0.7rem;
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 4px;
}
.status-tag.achieved { background: rgba(61, 214, 140, 0.15); color: var(--green); }
.status-tag.abandoned { background: rgba(140, 147, 166, 0.15); color: var(--text-muted); }
.empty { color: var(--text-muted); font-size: 0.85rem; }
</style>