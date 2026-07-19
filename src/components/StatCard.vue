<template>
  <div class="stat-card">
    <p class="label">{{ label }}</p>
    <p class="value">{{ value }}</p>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  value: [String, Number]
})
</script>

<style scoped>
.stat-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 12px 16px;
  min-width: 140px;
}
.label {
  font-size: 0.8rem;
  color: #666;
}
.value {
  font-size: 1.4rem;
  font-weight: bold;
}
</style>