<template>
  <TransitionGroup name="toast" tag="div" class="toast-container">
    <div
      v-for="toast in store.toasts"
      :key="toast.id"
      class="toast"
      :class="toast.type"
      @click="store.remove(toast.id)"
    >
      <CheckCircle v-if="toast.type === 'success'" :size="16" />
      <AlertCircle v-else :size="16" />
      <span>{{ toast.message }}</span>
    </div>
  </TransitionGroup>
</template>

<script setup>
import { useToastStore } from '../stores/toast'
import { CheckCircle, AlertCircle } from 'lucide-vue-next'

const store = useToastStore()
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 16px;
  font-size: 0.85rem;
  color: var(--text);
  cursor: pointer;
  pointer-events: auto;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
  min-width: 200px;
  max-width: 360px;
}

.toast.success { border-left: 3px solid var(--green); }
.toast.success :deep(svg) { color: var(--green); flex-shrink: 0; }

.toast.error { border-left: 3px solid var(--orange); }
.toast.error :deep(svg) { color: var(--orange); flex-shrink: 0; }

.toast span { flex: 1; }

.toast-enter-active { transition: all 0.3s ease-out; }
.toast-leave-active { transition: all 0.2s ease-in; }
.toast-enter-from { opacity: 0; transform: translateX(40px); }
.toast-leave-to { opacity: 0; transform: translateX(40px); }
</style>
