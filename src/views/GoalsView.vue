<template>
  <div>
    <h1>Goals</h1>
    <GoalForm @created="load" />

    <h2>Active goals</h2>
    <div v-for="goal in activeGoals" :key="goal.id">
      <p>{{ metricLabel(goal.metricType) }} → {{ goal.targetValue }}{{ metricUnit(goal.metricType) }}</p>
      <p>Current: {{ currentValue(goal.metricType) }}{{ metricUnit(goal.metricType) }} → Target: {{ goal.targetValue }}{{ metricUnit(goal.metricType) }}</p>
      <p v-if="goal.targetDate">Target date: {{ goal.targetDate }}</p>
      <button @click="updateStatus(goal.id, 'ACHIEVED')">Mark achieved</button>
      <button @click="updateStatus(goal.id, 'ABANDONED')">Abandon</button>
    </div>
    <p v-if="activeGoals.length === 0">No active goals.</p>

    <h2>Past goals</h2>
    <div v-for="goal in pastGoals" :key="goal.id">
      <p>{{ metricLabel(goal.metricType) }} → {{ goal.targetValue }}{{ metricUnit(goal.metricType) }}
        <span>({{ goal.status.toLowerCase() }})</span>
      </p>
    </div>
    <p v-if="pastGoals.length === 0">No past goals.</p>
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