<template>
  <span v-if="insight" class="badge" :class="insight.messageType">
    {{ messageText }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  insight: Object
})

const messages = {
  expected_dip: 'Expected dip — matches reduced activity',
  nice_surprise: 'Nice surprise — better than expected',
  worth_investigating: 'Worth investigating — check diet or sleep',
  on_track: 'On track — consistency paying off'
}

const messageText = computed(() => messages[props.insight?.messageType] || '')
</script>

<style scoped>
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
}
.expected_dip { background: var(--bg); color: var(--text-muted); border: 1px solid var(--border); }
.nice_surprise { background: rgba(61, 214, 140, 0.15); color: var(--green); }
.worth_investigating { background: rgba(251, 146, 60, 0.15); color: var(--orange); }
.on_track { background: rgba(61, 214, 140, 0.15); color: var(--green); }
</style>