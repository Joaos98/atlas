<template>
  <div class="empty-state">
    <span class="empty-icon"><component :is="icon" :size="28" /></span>
    <span class="empty-title">{{ title }}</span>
    <span class="empty-desc">{{ description }}</span>
    <RouterLink v-if="action" :to="action.to" class="empty-action">{{ action.label }}</RouterLink>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  icon: { type: Object, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
  action: { type: Object, default: null }
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-6) 0;
  color: var(--text-muted);
}

.empty-icon {
  color: var(--border);
  margin-bottom: var(--space-1);
}

.empty-title {
  font-weight: 600;
  color: var(--text-muted);
}

.empty-desc {
  font-size: 0.85rem;
  text-align: center;
}

.empty-action {
  margin-top: var(--space-2);
  padding: 8px 16px;
  background: var(--blue);
  color: var(--bg);
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 500;
  text-decoration: none;
}

.empty-action:hover {
  filter: brightness(1.1);
}
</style>
