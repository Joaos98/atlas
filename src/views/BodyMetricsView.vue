<template>
  <div>
    <h1>Body Metrics</h1>
    <h2>Add New Measurement</h2>
    <BodyMetricsForm @logged="onLogged" />

    <h2>Progress</h2>
    <div class="charts-wrapper">
      <MetricChart class="chart" label="Weight" :entries="chartEntries('weightKg')" color="#4F8DFF" unit="Kg" />
      <MetricChart class="chart" label="Muscle Mass" :entries="chartEntries('muscleMassKg')" color="#3DD68C" unit="Kg" />
      <MetricChart class="chart" label="Body Water" :entries="chartEntries('waterLiters')" color="#2DD4BF" unit="L" />
      <MetricChart class="chart" label="Body Fat Mass" :entries="chartEntries('bodyFatKg')" color="#FB923C" unit="Kg" />
      <MetricChart class="chart" label="Body Fat Percentage" :entries="chartEntries('bodyFatPct')" color="#FB3C3C" unit="%" />
    </div>

    <h2>History & insights</h2>
    <table v-if="metrics.length">
      <thead>
        <tr>
          <th>Date</th><th>Weight</th><th>Body Water</th><th>Muscle Mass</th><th>Body Fat Mass</th><th>Body Fat Percentage</th>
          <th>Muscle Mass Insight</th><th>Body Fat Insight</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in [...metrics].reverse()" :key="m.id">
          <td>{{formatDateBr(m.measuredOn)}}</td>
          <td class="data-value">{{ m.weightKg }}Kg</td>
          <td class="data-value">{{ m.waterLiters }}L</td>
          <td class="data-value">{{ m.muscleMassKg }}Kg</td>
          <td class="data-value">{{ m.bodyFatKg }}Kg</td>
          <td class="data-value">{{ m.bodyFatPct }}%</td>
          <td><InsightBadge :insight="m.muscleMassInsight" /></td>
          <td><InsightBadge :insight="m.bodyFatInsight" /></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getBodyMetrics } from '../services/bodyMetricsService'
import BodyMetricsForm from '../components/BodyMetricsForm.vue'
import MetricChart from '../components/MetricChart.vue'
import InsightBadge from '../components/InsightBadge.vue'

const metrics = ref([])

async function load() {
  const response = await getBodyMetrics()
  metrics.value = response.data
}

onMounted(load)

function onLogged() {
  load()
}

function chartEntries(field) {
  return metrics.value.map(m => ({ measuredOn: m.measuredOn, value: m[field] }))
}
function formatDateBr(dateString) {
  if (!dateString) return '';
  
  const [year, month, day] = dateString.split('-');
  return `${day}/${month}/${year}`;
}
</script>
<style scoped>
.charts-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}
.chart {
  flex: 0 0 calc(33% - 20px); 
}
</style>