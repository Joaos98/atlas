<template>
  <div>
    <h1>Body Metrics</h1>
    <BodyMetricsForm @logged="onLogged" />

    <h2>Progress</h2>
    <MetricChart label="Weight (kg)" :entries="chartEntries('weightKg')" />
    <MetricChart label="Muscle mass (kg)" :entries="chartEntries('muscleMassKg')" />
    <MetricChart label="Water (L)" :entries="chartEntries('waterLiters')" />
    <MetricChart label="Body fat (kg)" :entries="chartEntries('bodyFatKg')" />
    <MetricChart label="Body fat (%)" :entries="chartEntries('bodyFatPct')" />

    <h2>History & insights</h2>
    <table v-if="metrics.length">
      <thead>
        <tr>
          <th>Date</th><th>Weight</th><th>Muscle mass</th><th>Body fat %</th>
          <th>Muscle mass insight</th><th>Body fat insight</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in [...metrics].reverse()" :key="m.id">
          <td>{{ m.measuredOn }}</td>
          <td>{{ m.weightKg }}</td>
          <td>{{ m.muscleMassKg }}</td>
          <td>{{ m.bodyFatPct }}</td>
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
</script>