<template>
  <div class="date-picker" ref="root">
    <button type="button" class="dp-trigger" :class="{ empty: !modelValue }" @click="toggle">
      <span class="dp-value">{{ displayValue }}</span>
      <X v-if="modelValue && clearable" :size="14" class="dp-clear" @click.stop="clear" />
      <Calendar :size="14" class="dp-icon" />
    </button>

    <div v-if="open" class="dp-popup">
      <div class="dp-header">
        <button type="button" class="dp-nav" @click="shiftMonth(-1)"><ChevronLeft :size="16" /></button>
        <span class="dp-month">{{ monthLabel }}</span>
        <button type="button" class="dp-nav" @click="shiftMonth(1)"><ChevronRight :size="16" /></button>
      </div>
      <div class="dp-grid dp-weekdays">
        <span v-for="(d, i) in ['S', 'M', 'T', 'W', 'T', 'F', 'S']" :key="i">{{ d }}</span>
      </div>
      <div class="dp-grid">
        <span v-for="n in leadingBlanks" :key="'b' + n" class="dp-cell blank"></span>
        <button
          v-for="day in daysInMonth"
          :key="day"
          type="button"
          class="dp-cell"
          :class="{ today: isToday(day), selected: isSelected(day) }"
          @click="select(day)"
        >
          {{ day }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Calendar, ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { formatDateBr, toLocalDateStr } from '../utils/date'

const props = defineProps({
  modelValue: { type: String, default: null },
  clearable: { type: Boolean, default: false },
  placeholder: { type: String, default: 'dd/mm/yyyy' }
})
const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const root = ref(null)

const now = new Date()
const viewYear = ref(now.getFullYear())
const viewMonth = ref(now.getMonth())

const displayValue = computed(() =>
  props.modelValue ? formatDateBr(props.modelValue) : props.placeholder
)

const monthLabel = computed(() =>
  new Date(viewYear.value, viewMonth.value, 1).toLocaleDateString('en-GB', {
    month: 'long',
    year: 'numeric'
  })
)

// Week starts on Sunday (app-wide constant)
const daysInMonth = computed(() => new Date(viewYear.value, viewMonth.value + 1, 0).getDate())
const leadingBlanks = computed(() => new Date(viewYear.value, viewMonth.value, 1).getDay())

function toggle() {
  open.value = !open.value
  if (open.value && props.modelValue) {
    // Parse as local date — new Date(str) would shift by the UTC offset
    const [y, m] = props.modelValue.split('-').map(Number)
    viewYear.value = y
    viewMonth.value = m - 1
  }
}

function close() {
  open.value = false
}

function shiftMonth(delta) {
  const d = new Date(viewYear.value, viewMonth.value + delta, 1)
  viewYear.value = d.getFullYear()
  viewMonth.value = d.getMonth()
}

function cellDate(day) {
  return toLocalDateStr(new Date(viewYear.value, viewMonth.value, day))
}

function isSelected(day) {
  return props.modelValue === cellDate(day)
}

function isToday(day) {
  return cellDate(day) === toLocalDateStr(new Date())
}

function select(day) {
  emit('update:modelValue', cellDate(day))
  close()
}

function clear() {
  emit('update:modelValue', null)
}

function onClickOutside(e) {
  if (root.value && !root.value.contains(e.target)) close()
}

function onKeydown(e) {
  if (e.key === 'Escape') close()
}

onMounted(() => {
  document.addEventListener('click', onClickOutside)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.date-picker {
  position: relative;
}
.dp-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  font-family: var(--font-data);
  font-weight: 400;
  text-align: left;
}
.dp-trigger:hover {
  border-color: var(--border-hover);
}
.dp-trigger:focus-visible {
  outline: none;
  border-color: var(--blue);
  box-shadow: 0 0 0 3px rgba(79, 141, 255, 0.18);
}
.dp-trigger.empty .dp-value {
  color: var(--text-muted);
  opacity: 0.45;
}
.dp-value {
  flex: 1;
}
.dp-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}
.dp-clear {
  color: var(--text-muted);
  flex-shrink: 0;
  border-radius: 4px;
}
.dp-clear:hover {
  color: var(--text);
}

.dp-popup {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 50;
  width: 244px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}
.dp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.dp-month {
  font-size: 0.85rem;
  font-weight: 600;
}
.dp-nav {
  display: flex;
  align-items: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 6px;
}
.dp-nav:hover {
  color: var(--text);
  background: var(--bg);
}
.dp-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}
.dp-weekdays {
  margin-bottom: 2px;
}
.dp-weekdays span {
  text-align: center;
  font-size: 0.65rem;
  color: var(--text-muted);
  padding: 4px 0;
}
.dp-cell {
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  font-family: var(--font-data);
  font-size: 0.8rem;
  font-weight: 400;
  color: var(--text);
  padding: 0;
}
.dp-cell:not(.blank):hover {
  background: var(--bg);
}
.dp-cell.selected:hover {
  background: var(--blue);
}
.dp-cell.today {
  border-color: var(--blue);
}
.dp-cell.selected {
  background: var(--blue);
  color: var(--bg);
  font-weight: 600;
}
</style>
