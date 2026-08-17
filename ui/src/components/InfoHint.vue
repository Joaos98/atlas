<template>
  <!-- Explanations that are worth having but not worth reading every visit. Native title
       rather than a custom popover: it needs no positioning logic, no dismiss handling, and
       works on keyboard focus. -->
  <span class="info-hint" :title="text" tabindex="0" role="note" :aria-label="text">
    <Info :size="13" />
  </span>
</template>

<script setup>
import { Info } from 'lucide-vue-next'

defineProps({
  text: { type: String, required: true }
})
</script>

<style scoped>
.info-hint {
  display: inline-flex;
  align-items: center;
  color: var(--text-muted);
  cursor: help;
  vertical-align: middle;
  margin-left: 6px;
}
.info-hint:hover,
.info-hint:focus-visible {
  color: var(--blue);
  outline: none;
}
</style>
