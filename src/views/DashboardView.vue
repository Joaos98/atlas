<template>
  <div v-if="loading">Loading...</div>
  <div v-else-if="stats" class="page">
    <section>
      <h2>Streaks</h2>
      <div class="stat-grid">
        <StatCard label="Current streak" :value="`${stats.streakStats.currentStreak} weeks`" color="purple" :icon="Flame" />
        <StatCard label="Longest streak" :value="`${stats.streakStats.longestStreak} weeks`" color="purple" :icon="Flame" />
      </div>
    </section>

    <h2>Workout activity</h2>
    <section class="card">
      <WorkoutHeatmap :refresh="0" />
    </section>

    <section>
      <h2>This month</h2>
      <div class="stat-grid">
        <StatCard label="Workouts" :value="stats.workoutStats.totalWorkoutsThisMonth" color="blue" :icon="Dumbbell" />
        <StatCard label="Longest session" :value="formatDuration(stats.workoutStats.longestSessionThisMonth)" color="blue" :icon="Clock" />
        <StatCard label="Avg duration" :value="formatDuration(stats.workoutStats.averageDurationThisMonth)" color="blue" :icon="Clock" />
        <StatCard label="Most frequent type" :value="stats.workoutStats.mostFrequentTypeThisMonth || '—'" color="blue" :icon="Tag" />
        <StatCard
          v-if="stats.workoutStats.averageCaloriesBurnedThisMonth"
          label="Avg calories/session"
          :value="`${Math.round(stats.workoutStats.averageCaloriesBurnedThisMonth)} Kcal`"
          color="blue"
          :icon="Zap"
        />
      </div>
    </section>

    <section>
      <h2>Body composition since first measurement</h2>
      <div class="stat-grid">
        <StatCard label="Weight" :value="formatChange(stats.bodyCompositionStats.weightChangeKg, 'kg')" color="blue" :icon="Scale" />
        <StatCard label="Muscle mass" :value="formatChange(stats.bodyCompositionStats.muscleMassChangeKg, 'kg')" :color="directionColor(stats.bodyCompositionStats.muscleMassChangeKg, false)" :icon="TrendingUp" />
        <StatCard label="Body fat %" :value="formatChange(stats.bodyCompositionStats.bodyFatPctChange, '%')" :color="directionColor(stats.bodyCompositionStats.bodyFatPctChange, true)" :icon="TrendingDown" />
      </div>
    </section>
  </div>
  <div v-else>Could not load stats.</div>
</template>


<script setup>
import { ref, onMounted } from 'vue'
import { getStats } from '../services/statsService'
import StatCard from '../components/StatCard.vue'
import WorkoutHeatmap from '../components/WorkoutHeatmap.vue'
import { Flame, Dumbbell, Clock, Tag, Zap, Scale, TrendingUp, TrendingDown } from 'lucide-vue-next'

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
if (minutes == null) return '—';
if (minutes < 60) {
  return `${Math.floor(minutes)} min`;
}
let h = Math.floor(minutes / 60);
let m = Math.floor((minutes % 60));
return m > 0 ? `${h}h ${m}min` : `${h}h`;
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
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
}
</style>