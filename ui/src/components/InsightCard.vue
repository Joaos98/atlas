<template>
  <div class="card insight-card" :class="{ unavailable: isError, 'not-configured': isNotConfigured }">
    <p v-if="insight.verdict" class="insight-verdict">
      <Sparkles :size="16" class="verdict-icon" /> {{ insight.verdict }}
    </p>
    <p class="insight-text" :class="{ dimmed: loading }">{{ insight.text || 'No insight yet.' }}</p>

    <div v-if="isNotConfigured" class="insight-cta">
      <RouterLink to="/settings">Set up an insight provider</RouterLink>
    </div>
    <div v-else-if="!insight.text" class="insight-cta">
      <slot name="cta" />
    </div>

    <!-- Nothing to regenerate and nothing to date until a provider exists. -->
    <div v-if="!isNotConfigured" class="insight-footer">
      <span v-if="insight.date && !loading" class="insight-date">Updated after your {{ insight.date }} measurement</span>
      <span v-if="loading" class="insight-loading">Regenerating...</span>
      <button class="btn-icon" title="Regenerate" @click="$emit('regenerate')" :disabled="loading">
        <RefreshCw :size="14" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { Sparkles, RefreshCw } from 'lucide-vue-next'

const props = defineProps({
  insight: { type: Object, required: true },
  loading: { type: Boolean, default: false }
})

defineEmits(['regenerate'])

// The backend says which of these it is; nothing here reads the message text to find out.
const isNotConfigured = computed(() => props.insight.state === 'NOT_CONFIGURED')
const isError = computed(
  () => props.insight.state === 'UNREACHABLE' || props.insight.state === 'PROVIDER_ERROR'
)
</script>

<style scoped>
.insight-card {
  border-left: 3px solid var(--purple);
}
.insight-card.unavailable {
  border-left-color: var(--orange);
}
/* Not an error — a fresh install has simply not set this up yet. */
.insight-card.not-configured {
  border-left-color: var(--border);
}
.insight-card.not-configured .insight-text {
  color: var(--text-muted);
}
.insight-verdict {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 1.05rem;
  color: var(--text);
  background: rgba(139, 92, 246, 0.12);
  border: 1px solid rgba(139, 92, 246, 0.25);
  border-radius: 6px;
  padding: 4px 10px;
  margin: 0 0 10px;
}
.verdict-icon {
  color: var(--purple);
  flex-shrink: 0;
}
.insight-card.unavailable .insight-verdict {
  background: rgba(251, 146, 60, 0.12);
  border-color: rgba(251, 146, 60, 0.25);
}
.insight-card.unavailable .verdict-icon {
  color: var(--orange);
}
.insight-card.unavailable .btn-icon {
  color: var(--orange);
}
.insight-text {
  font-size: 0.88rem;
  line-height: 1.6;
  margin: 0;
  white-space: pre-line;
}
.insight-text.dimmed { opacity: 0.4; }
.insight-cta {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin: 6px 0 0;
}
.insight-cta :deep(a) {
  color: var(--blue);
  text-decoration: none;
  font-weight: 500;
}
.insight-cta :deep(a:hover) {
  text-decoration: underline;
}
.insight-footer {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--border);
}
.insight-date {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.insight-loading {
  font-size: 0.8rem;
  color: var(--text-muted);
  animation: pulse 1.5s ease-in-out infinite;
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
  margin-left: auto;
}
.btn-icon:hover { color: var(--text); background: var(--bg); }
.btn-icon:disabled { opacity: 0.4; cursor: default; }
</style>
