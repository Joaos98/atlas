<template>
  <div v-if="metrics.length >= 2" class="latest-measurement-grid">
    <StatCard
      label="Weight"
      :value="`${latest.weightKg} Kg`"
      color="blue"
      :icon="Scale"
      :subtitle="deltaText('weightKg', 'kg')"
    >
      <template #sparkline>
        <MetricSparkline :data="sparkData('weightKg')" :color="sparkColor('weightKg')" />
      </template>
    </StatCard>
    <StatCard
      label="Body Water"
      :value="`${latest.waterLiters} L`"
      color="blue"
      :icon="Droplets"
      :subtitle="deltaText('waterLiters', 'L')"
    >
      <template #sparkline>
        <MetricSparkline :data="sparkData('waterLiters')" :color="sparkColor('waterLiters')" />
      </template>
    </StatCard>
    <StatCard
      label="Muscle Mass"
      :value="`${latest.muscleMassKg} Kg`"
      :color="deltaColor('muscleMassKg')"
      :icon="Dumbbell"
      :subtitle="deltaText('muscleMassKg', 'kg')"
    >
      <template #sparkline>
        <MetricSparkline :data="sparkData('muscleMassKg')" :color="sparkColor('muscleMassKg')" />
      </template>
    </StatCard>
    <StatCard
      label="Body Fat Mass"
      :value="`${latest.bodyFatKg} Kg`"
      :color="deltaColor('bodyFatKg')"
      :icon="ChartPie"
      :subtitle="deltaText('bodyFatKg', 'kg')"
    >
      <template #sparkline>
        <MetricSparkline :data="sparkData('bodyFatKg')" :color="sparkColor('bodyFatKg')" />
      </template>
    </StatCard>
    <StatCard
      label="Body Fat Percentage"
      :value="`${latest.bodyFatPct} %`"
      :color="deltaColor('bodyFatPct')"
      :icon="Percent"
      :subtitle="deltaText('bodyFatPct', '%')"
    >
      <template #sparkline>
        <MetricSparkline :data="sparkData('bodyFatPct')" :color="sparkColor('bodyFatPct')" />
      </template>
    </StatCard>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import StatCard from './StatCard.vue'
import MetricSparkline from './MetricSparkline.vue'
import { Scale, Droplets, Dumbbell, ChartPie, Percent } from 'lucide-vue-next'

const props = defineProps({
  metrics: { type: Array, default: () => [] }
})

const latest = computed(() => props.metrics[props.metrics.length - 1] || {})
const previous = computed(() => props.metrics.length >= 2 ? props.metrics[props.metrics.length - 2] : null)

function deltaValue(field) {
  if (!previous.value) return null
  return latest.value[field] - previous.value[field]
}

function deltaText(field, unit) {
  const diff = deltaValue(field)
  if (diff == null) return ''
  const sign = diff > 0 ? '+' : ''
  return `${sign}${diff.toFixed(1)} ${unit} since previous`
}

function deltaColor(field) {
  const diff = deltaValue(field)
  if (diff == null) return 'blue'
  // muscle: gain good, body fat: loss good
  if (field === 'muscleMassKg') return diff > 0 ? 'green' : 'orange'
  if (field === 'bodyFatKg' || field === 'bodyFatPct') return diff < 0 ? 'green' : 'orange'
  return 'blue'
}

const SPARK_MONTHS = 12

function sparkData(field) {
  const now = new Date()
  const y = now.getFullYear()
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  const cutoff = y <= SPARK_MONTHS ? '0000-01-01' : `${y - 1}-${mm}-${dd}`
  return props.metrics.filter(m => m.measuredOn >= cutoff).map(m => m[field])
}

function sparkColor(field) {
  const diff = deltaValue(field)
  if (diff == null) return '#4F8DFF'
  if (field === 'muscleMassKg') return diff > 0 ? '#3DD68C' : '#FB923C'
  if (field === 'bodyFatKg' || field === 'bodyFatPct') return diff < 0 ? '#3DD68C' : '#FB923C'
  return '#4F8DFF'
}
</script>

<style scoped>
.latest-measurement-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
}
</style>
