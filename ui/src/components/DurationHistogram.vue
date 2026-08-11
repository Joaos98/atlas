<template>
  <div class="chart-card">
    <h3>Session length</h3>
    <div v-if="loading" class="chart-box"><SkeletonLoader height="200px" /></div>
    <div v-else-if="totalCount === 0" class="muted">No data this period.</div>
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
const buckets = ref([0, 0, 0, 0, 0])

const BUCKET_LABELS = ['< 30 min', '30–45 min', '45–60 min', '60–90 min', '90+ min']
const BUCKET_COLORS = ['rgba(79, 141, 255, 0.3)', 'rgba(79, 141, 255, 0.45)', 'rgba(79, 141, 255, 0.6)', 'rgba(79, 141, 255, 0.75)', '#4F8DFF']

const totalCount = computed(() => buckets.value.reduce((s, c) => s + c, 0))

function classify(duration) {
  if (duration < 30) return 0
  if (duration <= 45) return 1
  if (duration <= 60) return 2
  if (duration <= 90) return 3
  return 4
}

async function load() {
  loading.value = true
  try {
    const res = await getHeatmap(props.startDate, props.endDate)
    const data = Array.isArray(res.data) ? res.data : (res.data.content || [])
    const counts = [0, 0, 0, 0, 0]
    for (const day of data) {
      const workouts = Array.isArray(day.workouts) ? day.workouts : []
      for (const w of workouts) {
        counts[classify(w.durationMinutes)]++
      }
    }
    buckets.value = counts
  } catch {
    buckets.value = [0, 0, 0, 0, 0]
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.startDate, props.endDate, props.refresh], load)

const chartData = computed(() => ({
  labels: BUCKET_LABELS,
  datasets: [{
    data: buckets.value,
    backgroundColor: BUCKET_COLORS,
    borderRadius: 6,
    barPercentage: 0.7
  }]
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      callbacks: {
        label: ctx => `${ctx.parsed.y} workout${ctx.parsed.y === 1 ? '' : 's'}`
      }
    }
  },
  scales: {
    x: { ticks: { color: '#8C93A6', font: { size: 10 }, maxRotation: 0 }, grid: { display: false } },
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
