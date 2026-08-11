<template>
  <div>
    <h3>Active days per week</h3>
    <div v-if="loading" class="chart-box skeleton-chart">
      <div class="sk-bar-row">
        <div class="skeleton sk-bar" v-for="i in 8" :key="i" :style="{ height: (30 + Math.random() * 140) + 'px' }"></div>
      </div>
    </div>
    <div v-else-if="weeks.length === 0" class="muted">No active days this period.</div>
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
  Chart as ChartJS, Title, Tooltip, BarElement, CategoryScale, LinearScale, PointElement, LineElement
} from 'chart.js'

ChartJS.register(Title, Tooltip, BarElement, CategoryScale, LinearScale, PointElement, LineElement)

const props = defineProps({
  startDate: { type: String, default: null },
  endDate: { type: String, default: null },
  refresh: Number
})

const WEEKS_DEFAULT = 8

const loading = ref(true)
const targetPerWeek = ref(4)
const weeks = ref([])

function formatWeekLabel(date) {
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

async function load() {
  loading.value = true
  try {
    let start, end, chartEnd
    if (props.startDate && props.endDate) {
      start = new Date(props.startDate + 'T00:00:00')
      end = new Date(props.endDate + 'T00:00:00')
      chartEnd = new Date(props.endDate + 'T00:00:00')
    } else {
      end = new Date()
      end.setDate(end.getDate() - ((end.getDay() + 1) % 7 || 7))
      start = new Date(end)
      start.setDate(start.getDate() - end.getDay())
      start.setDate(start.getDate() - (WEEKS_DEFAULT - 1) * 7)
      chartEnd = new Date(end)
    }
    const sundayStart = new Date(start)
    sundayStart.setDate(sundayStart.getDate() - sundayStart.getDay())

    const heatmapEnd = props.endDate || toLocalDateStr(end)

    const [heatmapRes, settingsRes] = await Promise.all([
      getHeatmap(toLocalDateStr(sundayStart), heatmapEnd),
      getSettings()
    ])
    const data = Array.isArray(heatmapRes.data) ? heatmapRes.data : (heatmapRes.data.content || [])
    targetPerWeek.value = settingsRes.data.targetWorkoutsPerWeek ?? 4

    const dayMap = {}
    for (const d of data) {
      dayMap[d.date] = (d.workouts || []).length > 0 ? 1 : 0
    }

    const result = []
    const cursor = new Date(sundayStart)
    const endTime = end.getTime()
    while (cursor.getTime() <= endTime) {
      let activeDays = 0
      const weekStart = new Date(cursor)
      for (let i = 0; i < 7; i++) {
        activeDays += dayMap[toLocalDateStr(cursor)] || 0
        cursor.setDate(cursor.getDate() + 1)
      }
      result.push({ weekStart, activeDays })
    }
    weeks.value = result
  } catch {
    weeks.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.startDate, props.endDate, props.refresh], load)

const chartData = computed(() => ({
  labels: weeks.value.map(w => formatWeekLabel(w.weekStart)),
  datasets: [
    {
      type: 'bar',
      label: 'Active days',
      data: weeks.value.map(w => w.activeDays),
      backgroundColor: weeks.value.map(w =>
        w.activeDays >= targetPerWeek.value ? '#3DD68C' : '#FB923C'
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
  plugins: { legend: { display: false } },
  scales: {
    x: { ticks: { color: '#8C93A6', font: { size: 11 }, maxRotation: 0 }, grid: { display: false } },
    y: {
      min: 0,
      max: 7,
      ticks: { color: '#8C93A6', font: { family: 'JetBrains Mono', size: 11 }, stepSize: 1 },
      grid: { color: '#2A2E3A' }
    }
  }
}
</script>

<style scoped>
h3 { font-size: 0.85rem; color: var(--text-muted); margin: 0 0 12px; font-weight: 600; }
.chart-box { height: 200px; }
.muted { color: var(--text-muted); font-size: 0.85rem; text-align: center; padding: 32px 0; }

.skeleton-chart .sk-bar-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 100%;
  padding: 0 4px 4px;
}
.skeleton-chart .sk-bar {
  flex: 1;
  min-width: 0;
  border-radius: 4px 4px 0 0;
}
</style>
