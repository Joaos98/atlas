<template>
  <div class="demo-banner">
    <span class="demo-banner-icon"><FlaskConical :size="14" /></span>
    <span>Demo — your data stays in your browser.</span>
    <a v-if="selfHostUrl" :href="selfHostUrl" target="_blank" rel="noopener">Self-host Atlas instead</a>
    <!-- Belongs with the sentence explaining that this data is disposable, rather than as a
         section in Settings competing with real configuration. -->
    <button class="demo-reset" title="Restore the seeded demo data" @click="reset">
      <RotateCcw :size="13" /> Reset demo data
    </button>
  </div>
</template>

<script setup>
import { FlaskConical, RotateCcw } from 'lucide-vue-next'

const selfHostUrl = import.meta.env.VITE_SELF_HOST_URL || ''

async function reset() {
  if (!confirm('Reset all demo data to the seeded state?')) return
  const { resetDemoData } = await import('./demoApi')
  resetDemoData()
}
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
.demo-reset {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  background: transparent;
  border: 1px solid rgba(139, 92, 246, 0.4);
  color: var(--text-muted);
  font-size: 0.78rem;
  padding: 3px 10px;
  border-radius: 6px;
  cursor: pointer;
}
.demo-reset:hover {
  color: var(--text);
  border-color: var(--purple);
}
</style>
