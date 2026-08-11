<template>
  <div class="demo-banner">
    <span class="demo-banner-icon"><FlaskConical :size="14" /></span>
    <span>Demo — your data stays in your browser.</span>
    <a v-if="selfHostUrl" :href="selfHostUrl" target="_blank" rel="noopener">Self-host Atlas instead</a>
  </div>
</template>

<script setup>
import { FlaskConical } from 'lucide-vue-next'

const selfHostUrl = import.meta.env.VITE_SELF_HOST_URL || ''
</script>

<style scoped>
.demo-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(139, 92, 246, 0.12);
  border-bottom: 1px solid rgba(139, 92, 246, 0.25);
  color: var(--text);
  font-size: 0.85rem;
  padding: 8px 16px;
}
.demo-banner-icon {
  color: var(--purple);
  display: flex;
}
.demo-banner a {
  color: var(--blue);
  font-weight: 500;
  text-decoration: none;
}
.demo-banner a:hover {
  text-decoration: underline;
}
</style>
