<template>
  <div class="stat-card" :style="{ borderLeftColor: `var(--${color})` }">
    <p class="label">{{ label }}</p>
    <p class="value data-value">{{ value }}</p>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  value: [String, Number],
  color: { type: String, default: 'blue' }
})
</script>

<style scoped>
.stat-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-left: 3px solid;
  border-radius: 8px;
  padding: 12px 16px;
  min-width: 140px;
}
.label {
  font-size: 0.8rem;
  color: var(--text-muted);
}
.value {
  font-size: 1.4rem;
  font-weight: 600;
  margin-top: 4px;
}
</style>