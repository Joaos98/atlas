<template>
  <div v-if="loading">Loading...</div>
  <div v-else-if="stats" class="dashboard">
    <h2>Streaks</h2>
    <div class="stat-grid">
      <StatCard label="Current streak" :value="`${stats.streakStats.currentStreak} weeks`" color="purple" />
      <StatCard label="Longest streak" :value="`${stats.streakStats.longestStreak} weeks`" color="purple" />
    </div>

    <h2>This month</h2>
    <div class="stat-grid">
      <StatCard label="Workouts" :value="stats.workoutStats.totalWorkoutsThisMonth" color="blue" />
      <StatCard label="Longest session" :value="formatDuration(stats.workoutStats.longestSessionThisMonth)" color="blue" />
      <StatCard label="Avg duration" :value="formatDuration(stats.workoutStats.averageDurationThisMonth)" color="blue" />
      <StatCard label="Most frequent type" :value="stats.workoutStats.mostFrequentTypeThisMonth || '—'" color="blue" />
      <StatCard 
        v-if="stats.workoutStats.averageCaloriesBurnedThisMonth"
        label="Avg calories/session"
        :value="Math.round(stats.workoutStats.averageCaloriesBurnedThisMonth)"
        color="blue"
      />
    </div>

    <h2>Body composition since first measurement</h2>
    <div class="stat-grid">
      <StatCard label="Weight" :value="formatChange(stats.bodyCompositionStats.weightChangeKg, 'kg')" color="blue" />
      <StatCard label="Muscle mass" :value="formatChange(stats.bodyCompositionStats.muscleMassChangeKg, 'kg')" :color="directionColor(stats.bodyCompositionStats.muscleMassChangeKg, false)" />
      <StatCard label="Body fat %" :value="formatChange(stats.bodyCompositionStats.bodyFatPctChange, '%')" :color="directionColor(stats.bodyCompositionStats.bodyFatPctChange, true)" />
    </div>
  </div>
  <div v-else>Could not load stats.</div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getStats } from '../services/statsService'
import StatCard from '../components/StatCard.vue'

const stats = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const response = await getStats()
    stats.value = response.data
  } catch (e) {
    stats.value = null
  } finally {
    loading.value = false
  }
})

function formatDuration(minutes) {
  return minutes != null ? `${minutes} min` : '—'
}

function formatChange(value, unit) {
  if (value == null) return '—'
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)} ${unit}`
}

function directionColor(value, lowerIsBetter) {
  if (value == null) return 'blue'
  const isGood = lowerIsBetter ? value < 0 : value > 0
  return isGood ? 'green' : 'orange'
}
</script>

<style scoped>
.stat-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
}
</style>