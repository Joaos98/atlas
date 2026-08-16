<template>
  <div v-if="metrics.length >= 2" class="latest-measurement-grid">
    <StatCard
      label="Weight"
      :value="formatWithUnit(latest.weightKg, 'weightKg')"
      color="blue"
      :icon="Scale"
      :subtitle="deltaText('weightKg')"
    >
      <template v-if="showSparklines" #sparkline>
        <MetricSparkline :data="sparkData('weightKg')" :color="sparkColor('weightKg')" />
      </template>
    </StatCard>
    <StatCard
      label="Body Water"
      :value="formatWithUnit(latest.waterLiters, 'waterLiters')"
      color="blue"
      :icon="Droplets"
      :subtitle="deltaText('waterLiters')"
    >
      <template v-if="showSparklines" #sparkline>
        <MetricSparkline :data="sparkData('waterLiters')" :color="sparkColor('waterLiters')" />
      </template>
    </StatCard>
    <StatCard
      label="Muscle Mass"
      :value="formatWithUnit(latest.muscleMassKg, 'muscleMassKg')"
      :color="deltaColor('muscleMassKg')"
      :icon="Dumbbell"
      :subtitle="deltaText('muscleMassKg')"
    >
      <template v-if="showSparklines" #sparkline>
        <MetricSparkline :data="sparkData('muscleMassKg')" :color="sparkColor('muscleMassKg')" />
      </template>
    </StatCard>
    <StatCard
      label="Body Fat Mass"
      :value="formatWithUnit(latest.bodyFatKg, 'bodyFatKg')"
      :color="deltaColor('bodyFatKg')"
      :icon="ChartPie"
      :subtitle="deltaText('bodyFatKg')"
    >
      <template v-if="showSparklines" #sparkline>
        <MetricSparkline :data="sparkData('bodyFatKg')" :color="sparkColor('bodyFatKg')" />
      </template>
    </StatCard>
    <StatCard
      label="Body Fat Percentage"
      :value="formatWithUnit(latest.bodyFatPct, 'bodyFatPct')"
      :color="deltaColor('bodyFatPct')"
      :icon="Percent"
      :subtitle="deltaText('bodyFatPct')"
    >
      <template v-if="showSparklines" #sparkline>
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
import { useUnits } from '@/composables/useUnits'

const { format, label, formatWithUnit } = useUnits()

const props = defineProps({
  metrics: { type: Array, default: () => [] },
  showSparklines: { type: Boolean, default: true }
})

const latest = computed(() => props.metrics[props.metrics.length - 1] || {})
const previous = computed(() => props.metrics.length >= 2 ? props.metrics[props.metrics.length - 2] : null)

function deltaValue(field) {
  if (!previous.value) return null
  return latest.value[field] - previous.value[field]
}

// A delta converts with the same factor as a value — the scale is linear with no offset —
// so the difference is converted directly rather than recomputed from converted endpoints.
function deltaText(field) {
  const diff = deltaValue(field)
  if (diff == null) return ''
  const sign = diff > 0 ? '+' : ''
  return `${sign}${format(diff, field)} ${label(field)} since previous`
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
