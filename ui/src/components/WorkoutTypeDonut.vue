<template>
  <div>
    <h3>Type breakdown</h3>
    <div v-if="loading" class="chart-box skeleton-donut">
      <div class="sk-ring"></div>
    </div>
    <div v-else-if="breakdown.length === 0" class="muted">No data this period.</div>
    <div v-else class="chart-box">
      <Doughnut :data="chartData" :options="chartOptions" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getHeatmap } from '../services/workoutService'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'

ChartJS.register(ArcElement, Tooltip, Legend)

const props = defineProps({
  startDate: { type: String, required: true },
  endDate: { type: String, required: true },
  refresh: Number
})

const loading = ref(true)
const breakdown = ref([])

async function load() {
  loading.value = true
  try {
    const res = await getHeatmap(props.startDate, props.endDate)
    const data = Array.isArray(res.data) ? res.data : (res.data.content || [])
    const map = {}
    for (const day of data) {
      const workouts = Array.isArray(day.workouts) ? day.workouts : []
      for (const w of workouts) {
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
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.startDate, props.endDate, props.refresh], load)

const chartData = computed(() => ({
  labels: breakdown.value.map(b => `${b.name} (${b.count})`),
  datasets: [{
    data: breakdown.value.map(b => b.count),
    backgroundColor: breakdown.value.map(b => b.colorHex),
    borderWidth: 2,
    borderColor: '#1B1E27'
  }]
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
h3 { font-size: 0.85rem; color: var(--text-muted); margin: 0 0 12px; font-weight: 600; }
.chart-box { height: 200px; }
.muted { color: var(--text-muted); font-size: 0.85rem; text-align: center; padding: 32px 0; }

.skeleton-donut {
  display: flex;
  align-items: center;
  justify-content: center;
}
.skeleton-donut .sk-ring {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  border: 12px solid var(--border);
  border-top-color: rgba(42, 46, 58, 0.3);
  animation: spin 1.2s linear infinite;
}
</style>
