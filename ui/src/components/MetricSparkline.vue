<template>
  <svg v-if="points.length >= 2" class="sparkline" :viewBox="viewBox" preserveAspectRatio="none">
    <polyline :points="linePoints" :stroke="color" fill="none" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  color: { type: String, default: '#4F8DFF' }
})

const points = computed(() => props.data)
const min = computed(() => Math.min(...points.value))
const max = computed(() => Math.max(...points.value))
const rng = computed(() => max.value - min.value || 2)

const viewBox = computed(() => `0 0 ${points.value.length - 1} ${rng.value}`)

const linePoints = computed(() =>
  points.value.map((v, i) => `${i},${max.value - v}`).join(' ')
)
</script>

<style scoped>
.sparkline {
  display: block;
  width: 100%;
  height: 16px;
  margin-top: 10px;
}
.sparkline polyline {
  vector-effect: non-scaling-stroke;
}
</style>
