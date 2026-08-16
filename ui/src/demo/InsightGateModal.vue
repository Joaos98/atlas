<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="close">
      <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="gate-title">
        <div class="modal-header">
          <Sparkles :size="18" class="modal-icon" />
          <h3 id="gate-title">Insight regeneration</h3>
          <button class="btn-icon" title="Close" @click="close"><X :size="16" /></button>
        </div>
        <p class="modal-text">
          This is a static demo — insight generation needs a real backend. Self-host Atlas
          and point it at any OpenAI-compatible provider — including a local Ollama — to
          regenerate insights from your data.
        </p>
        <div class="modal-actions">
          <button class="btn-primary" @click="close">Got it</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import { Sparkles, X } from 'lucide-vue-next'
import { insightGateOpen, closeInsightGate } from './insightGate'

const open = computed(() => insightGateOpen.value)

function close() {
  closeInsightGate()
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 16px;
}
.modal-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  max-width: 420px;
  width: 100%;
  padding: 20px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3);
}
.modal-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.modal-icon {
  color: var(--purple);
}
.modal-header h3 {
  margin: 0;
  flex: 1;
  font-family: var(--font-display);
  font-size: 1.05rem;
}
.modal-text {
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.6;
  margin: 0 0 16px;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
}
.btn-icon {
  display: flex;
  align-items: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
  cursor: pointer;
}
.btn-icon:hover {
  color: var(--text);
  background: var(--bg);
}
.btn-primary {
  background: var(--blue);
  color: var(--bg);
  border: none;
  padding: 9px 18px;
  font-size: 0.88rem;
  border-radius: 8px;
  cursor: pointer;
}
.btn-primary:hover {
  filter: brightness(1.1);
}
</style>
