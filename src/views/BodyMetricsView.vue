<template>
  <div>
    <h1>Body Metrics</h1>
    <BodyMetricsForm @logged="onLogged" />

    <h2>Progress</h2>
    <MetricChart label="Weight (kg)" :entries="chartEntries('weightKg')" color="#4F8DFF" />
    <MetricChart label="Muscle mass (kg)" :entries="chartEntries('muscleMassKg')" color="#3DD68C" />
    <MetricChart label="Water (L)" :entries="chartEntries('waterLiters')" color="#2DD4BF" />
    <MetricChart label="Body fat (kg)" :entries="chartEntries('bodyFatKg')" color="#FB923C" />
    <MetricChart label="Body fat (%)" :entries="chartEntries('bodyFatPct')" color="#FB923C" />

    <h2>History & insights</h2>
    <table v-if="metrics.length">
      <thead>
        <tr>
          <th>Date</th><th>Weight</th><th>Body Water</th><th>Muscle Mass (Kg)</th><th>Body Fat (Kg)</th><th>Body Fat (%)</th>
          <th>Muscle Mass Insight</th><th>Body Fat Insight</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in [...metrics].reverse()" :key="m.id">
          <td>{{ m.measuredOn }}</td>
          <td class="data-value">{{ m.weightKg }}Kg</td>
          <td class="data-value">{{ m.waterLiters }}L</td>
          <td class="data-value">{{ m.muscleMassKg }}Kg</td>
          <td class="data-value">{{ m.bodyFatKg }}</td>
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
</script>