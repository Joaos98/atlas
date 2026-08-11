<template>
  <div class="chart-card">
    <h3>Workout days</h3>
    <div v-if="loading" class="chart-box"><SkeletonLoader height="200px" /></div>
    <div v-else-if="totalWorkouts === 0" class="muted">No data this period.</div>
    <div v-else class="chart-box">
      <Bar :data="chartData" :options="chartOptions" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getHeatmap } from '../services/workoutService'
import SkeletonLoader from './SkeletonLoader.vue'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS, Title, Tooltip, BarElement, CategoryScale, LinearScale
} from 'chart.js'

ChartJS.register(Title, Tooltip, BarElement, CategoryScale, LinearScale)

const props = defineProps({
  startDate: { type: String, required: true },
  endDate: { type: String, required: true },
  refresh: Number
})
const loading = ref(true)
const dayCounts = ref([0, 0, 0, 0, 0, 0, 0])

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

const totalWorkouts = computed(() => dayCounts.value.reduce((s, c) => s + c, 0))

function barColor(index) {
  const today = new Date().getDay()
  return index === today ? '#4F8DFF' : 'rgba(79, 141, 255, 0.4)'
}

async function load() {
  loading.value = true
  try {
    const res = await getHeatmap(props.startDate, props.endDate)
    const data = Array.isArray(res.data) ? res.data : (res.data.content || [])
    const counts = [0, 0, 0, 0, 0, 0, 0]
    for (const day of data) {
      if (day.workouts && day.workouts.length > 0) {
        const d = new Date(day.date + 'T00:00:00').getDay()
        counts[d]++
      }
    }
    dayCounts.value = counts
  } catch {
    dayCounts.value = [0, 0, 0, 0, 0, 0, 0]
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.startDate, props.endDate, props.refresh], load)

const chartData = computed(() => ({
  labels: DAYS,
  datasets: [{
    data: dayCounts.value,
    backgroundColor: dayCounts.value.map((_, i) => barColor(i)),
    borderRadius: 6,
    barPercentage: 0.6
  }]
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      callbacks: {
        label: ctx => `${ctx.parsed.y} workout day${ctx.parsed.y === 1 ? '' : 's'}`
      }
    }
  },
  scales: {
    x: { ticks: { color: '#8C93A6', font: { size: 11 } }, grid: { display: false } },
    y: { ticks: { color: '#8C93A6', font: { family: 'JetBrains Mono', size: 11 }, stepSize: 1 }, grid: { color: '#2A2E3A' } }
  }
}
</script>

<style scoped>
.chart-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px 20px;
}
h3 { font-size: 0.85rem; color: var(--text-muted); margin: 0 0 12px; font-weight: 600; }
.chart-box { height: 200px; }
.muted { color: var(--text-muted); font-size: 0.85rem; text-align: center; padding: 32px 0; }
</style>
