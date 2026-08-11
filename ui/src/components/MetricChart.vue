<template>
  <div class="chart-card">
    <h3>{{ label }} ({{ unit }})</h3>
    <Line v-if="entries.length" :data="chartData" :options="chartOptions" />
    <div v-else class="chart-empty">
      <p class="chart-empty-text">No data yet.</p>
      <p class="chart-empty-hint">Add measurements to see progress.</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  Title,
  Tooltip,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale
} from 'chart.js'

ChartJS.register(Title, Tooltip, LineElement, PointElement, CategoryScale, LinearScale)

const props = defineProps({
  label: String,
  entries: Array,
  color: { type: String, default: '#4F8DFF' },
  unit: { type: String, default: '' }, // 1. Add the unit prop
  goalTarget: { type: Number, default: null }
})

const formatDate = (dateInput) => {
  const date = new Date(dateInput);
  const d = String(date.getDate()).padStart(2, '0');
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const y = String(date.getFullYear()).slice(-2);
  return `${d}/${m}/${y}`;
};

const chartData = computed(() => {
  const datasets = [{
    label: props.label,
    data: props.entries.map(e => e.value),
    borderColor: props.color,
    backgroundColor: props.color,
    pointRadius: 5,
    pointHoverRadius: 7,
    tension: 0
  }]

  if (props.goalTarget != null) {
    datasets.push({
      label: `Target: ${props.goalTarget}${props.unit}`,
      data: props.entries.map(() => props.goalTarget),
      borderColor: '#8B5CF6',
      borderDash: [5, 5],
      borderWidth: 2,
      pointRadius: 0,
      fill: false
    })
  }

  return {
    labels: props.entries.map(e => formatDate(e.measuredOn)),
    datasets
  }
})

const chartOptions = computed(() => ({
  responsive: true,
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: '#1B1E27',
      titleColor: '#EDEFF4',
      bodyColor: '#EDEFF4',
      borderColor: '#2A2E3A',
      borderWidth: 1,
      bodyFont: { family: 'JetBrains Mono' },
      callbacks: {
        label: function(context) {
          return `${context.dataset.label}: ${context.parsed.y}${props.unit}`;
        }
      }
    }
  },
  scales: {
    x: { 
      ticks: { 
        color: '#8C93A6',
        autoSkip: true,
        maxTicksLimit: 5,
        maxRotation: 0,
      }, 
      grid: { color: '#2A2E3A' } 
    },
    y: { 
      ticks: { 
        color: '#8C93A6', 
        font: { family: 'JetBrains Mono' },
        maxTicksLimit: 5 // Limits the Y-axis to a maximum of 5 numbers
      }, 
      grid: { color: '#2A2E3A' } 
    }
  }
}))
</script>

<style scoped>
.chart-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px 20px;
}
h3 {
  font-size: 0.95rem;
  color: var(--text-muted);
  font-family: var(--font-body);
  font-weight: 600;
  margin: 0 0 12px;
}

.chart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  gap: 4px;
}

.chart-empty-text {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0;
}

.chart-empty-hint {
  color: var(--border);
  font-size: 0.75rem;
  margin: 0;
}
</style>