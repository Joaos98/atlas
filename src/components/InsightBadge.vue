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
.expected_dip { background: #eee; color: #555; }
.nice_surprise { background: #d4f4dd; color: #1a7a3c; }
.worth_investigating { background: #fde3d0; color: #a04a1a; }
.on_track { background: #d4f4dd; color: #1a7a3c; }
</style>