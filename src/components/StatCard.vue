<template>
  <div class="stat-card" :style="{ borderLeftColor: `var(--${color})` }">
    <p class="label"><component :is="icon" :size="14" class="icon" /> {{ label }}</p>
    <p class="value data-value">{{ value }}</p>
    <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  value: [String, Number],
  color: { type: String, default: 'blue' },
  icon: { type: [Object, Function], default: null },
  subtitle: { type: String, default: '' }
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
.icon { vertical-align: -2px; margin-right: 4px; color: var(--text-muted); }
.subtitle { font-size: 0.75rem; color: var(--text-muted); margin-top: 2px; }
</style>