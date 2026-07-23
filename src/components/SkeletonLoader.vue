<template>
  <div class="skeleton" :style="style" :class="{ inline: inline }"></div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  width: { type: String, default: '100%' },
  height: { type: String, default: '1rem' },
  borderRadius: { type: String, default: '6px' },
  inline: { type: Boolean, default: false }
})

const style = computed(() => ({
  width: props.width,
  height: props.height,
  borderRadius: props.borderRadius
}))
</script>

<style scoped>
.inline {
  display: inline-block;
}
</style>
