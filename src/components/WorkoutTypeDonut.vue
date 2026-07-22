<template>
  <div>
    <p class="period">Last {{ WEEKS }} weeks</p>
    <div v-if="loading" class="muted">Loading...</div>
    <div v-else-if="breakdown.length === 0" class="muted">No data this period.</div>
    <div v-else class="chart-box">
      <Doughnut :data="chartData" :options="chartOptions" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getHeatmap } from '../services/workoutService'
import { toLocalDateStr } from '../utils/date'
import { Doughnut } from 'vue-chartjs'
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend
} from 'chart.js'

ChartJS.register(ArcElement, Tooltip, Legend)

const props = defineProps({
  refresh: Number
})

const loading = ref(true)
const breakdown = ref([])

const WEEKS = 8

async function load() {
  loading.value = true

  const today = new Date()
  // Most recent completed Saturday
  const end = new Date(today)
  const dow = end.getDay()
  const daysSinceLastSat = (dow + 1) % 7
  end.setDate(end.getDate() - (daysSinceLastSat || 7))

  const start = new Date(end)
  start.setDate(start.getDate() - WEEKS * 7 + 1)

  try {
    const res = await getHeatmap(toLocalDateStr(start), toLocalDateStr(end))
    const map = {}
    for (const day of res.data) {
      for (const w of day.workouts) {
        if (!map[w.type]) {
          map[w.type] = { count: 0, colorHex: w.colorHex }
        }
        map[w.type].count++
      }
    }
    breakdown.value = Object.entries(map).map(([name, d]) => ({
      name,
      count: d.count,
      colorHex: d.colorHex
    }))
  } catch {
    breakdown.value = []
  }

  loading.value = false
}

onMounted(load)
watch(() => props.refresh, load)

const chartData = computed(() => ({
  labels: breakdown.value.map(b => `${b.name} (${b.count})`),
  datasets: [
    {
      data: breakdown.value.map(b => b.count),
      backgroundColor: breakdown.value.map(b => b.colorHex),
      borderWidth: 2,
      borderColor: '#1B1E27'
    }
  ]
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'bottom',
      labels: {
        color: '#8C93A6',
        font: { size: 11 },
        padding: 12,
        usePointStyle: true,
        pointStyle: 'circle'
      }
    },
    tooltip: {
      backgroundColor: '#1B1E27',
      titleColor: '#EDEFF4',
      bodyColor: '#EDEFF4',
      bodyFont: { family: 'JetBrains Mono' },
      borderColor: '#2A2E3A',
      borderWidth: 1,
      callbacks: {
        label: function (ctx) {
          const total = ctx.dataset.data.reduce((a, b) => a + b, 0)
          const pct = total > 0 ? ((ctx.parsed / total) * 100).toFixed(0) : 0
          return ` ${ctx.label}: ${ctx.parsed} session${ctx.parsed === 1 ? '' : 's'} (${pct}%)`
        }
      }
    }
  },
  cutout: '65%'
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
