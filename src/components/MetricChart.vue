<template>
  <Line :data="chartData" :options="chartOptions" />
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
  entries: Array, // [{ measuredOn, value }]
})

const chartData = computed(() => ({
  labels: props.entries.map(e => e.measuredOn),
  datasets: [{
    label: props.label,
    data: props.entries.map(e => e.value),
    borderColor: '#378ADD',
    backgroundColor: '#378ADD',
    pointRadius: 5,
    tension: 0
  }]
}))

const chartOptions = {
  responsive: true,
  plugins: { legend: { display: false } }
}
</script>