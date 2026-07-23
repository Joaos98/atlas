<template>
  <div>
    <p class="period">Last {{ WEEKS_TO_SHOW }} weeks</p>
    <div v-if="loading" class="muted">Loading...</div>
    <div v-else-if="weeks.length === 0" class="muted">No workouts this period.</div>
    <div v-else class="chart-box">
      <Bar :data="chartData" :options="chartOptions" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getHeatmap } from '../services/workoutService'
import { getSettings } from '../services/settingsService'
import { toLocalDateStr } from '../utils/date'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  Title,
  Tooltip,
  BarElement,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement
} from 'chart.js'

ChartJS.register(Title, Tooltip, BarElement, CategoryScale, LinearScale, PointElement, LineElement)

const props = defineProps({
  refresh: Number
})

const loading = ref(true)
const rawData = ref([])
const targetPerWeek = ref(4)
const weeks = ref([])

const WEEKS_TO_SHOW = 8

async function load() {
  loading.value = true

  const today = new Date()
  // Most recent completed Saturday
  const end = new Date(today)
  const dow = end.getDay()
  const daysSinceLastSat = (dow + 1) % 7 // 0 means today is Saturday
  end.setDate(end.getDate() - (daysSinceLastSat || 7))

  // Go back 12 weeks to get the starting Sunday
  const start = new Date(end)
  start.setDate(start.getDate() - WEEKS_TO_SHOW * 7 + 1)

  try {
    const [heatmapRes, settingsRes] = await Promise.all([
      getHeatmap(toLocalDateStr(start), toLocalDateStr(end)),
      getSettings()
    ])
    rawData.value = heatmapRes.data
    targetPerWeek.value = settingsRes.data.targetWorkoutsPerWeek ?? 4
  } catch {
    rawData.value = []
  }

  computeWeeks(start, end)
  loading.value = false
}

function computeWeeks(start, end) {
  const map = {}
  for (const day of rawData.value) {
    map[day.date] = day.workouts.length
  }

  const result = []
  const cursor = new Date(start)
  while (cursor <= end) {
    let sessions = 0
    const weekStart = new Date(cursor)
    for (let i = 0; i < 7; i++) {
      const str = toLocalDateStr(cursor)
      sessions += map[str] || 0
      cursor.setDate(cursor.getDate() + 1)
    }
    result.push({ weekStart, sessions })
  }

  weeks.value = result
}

onMounted(load)
watch(() => props.refresh, load)

function formatWeekLabel(date) {
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

const chartData = computed(() => ({
  labels: weeks.value.map(w => formatWeekLabel(w.weekStart)),
  datasets: [
    {
      type: 'bar',
      label: 'Workouts',
      data: weeks.value.map(w => w.sessions),
      backgroundColor: weeks.value.map(w =>
        w.sessions >= targetPerWeek.value ? '#3DD68C' : '#FB923C'
      ),
      borderRadius: 6,
      barPercentage: 0.65
    },
    {
      type: 'line',
      label: `Target (${targetPerWeek.value})`,
      data: weeks.value.map(() => targetPerWeek.value),
      borderColor: '#FB923C',
      borderDash: [6, 4],
      borderWidth: 2,
      pointRadius: 0,
      fill: false
    }
  ]
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: '#1B1E27',
      titleColor: '#EDEFF4',
      bodyColor: '#EDEFF4',
      borderColor: '#2A2E3A',
      borderWidth: 1,
      bodyFont: { family: 'JetBrains Mono' }
    }
  },
  scales: {
    x: {
      ticks: { color: '#8C93A6', font: { size: 11 }, maxRotation: 0 },
      grid: { display: false }
    },
    y: {
      ticks: {
        color: '#8C93A6',
        font: { family: 'JetBrains Mono', size: 11 },
        stepSize: 1
      },
      grid: { color: '#2A2E3A' }
    }
  }
}
</script>

<style scoped>
.period {
  color: var(--text-muted);
  font-size: 0.75rem;
  margin: -6px 0 10px;
}
.chart-box {
  position: relative;
  height: 220px;
}
.muted {
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: center;
  padding: 32px 0;
}
</style>
