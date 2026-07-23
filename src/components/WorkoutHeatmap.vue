<template>
  <div class="heatmap-wrapper">
    <div class="month-row">
      <span class="day-labels-spacer"></span>
      <div class="month-inner">
        <span v-for="(week, weekIndex) in weeks" :key="weekIndex" class="month-slot">
          {{ monthLabelMap[weekIndex] || '' }}
        </span>
      </div>
    </div>

    <div class="content-row">
      <div class="day-labels">
        <span v-for="(label, i) in DAY_LABELS" :key="i" class="day-label-cell">{{ label }}</span>
      </div>

      <div ref="gridEl" class="heatmap-grid" role="grid" aria-label="Workout activity heatmap" @mouseleave="hideTooltip">
        <div v-for="(week, weekIndex) in weeks" :key="weekIndex" class="week-column">
          <div
            v-for="day in week"
            :key="day.date"
            class="day-cell"
            :class="{ padding: day.isPadding, today: day.isToday }"
            :aria-label="day.isPadding ? undefined : describeDay(day)"
            @mouseenter="showTooltip(day, $event)"
            @mousemove="moveTooltip"
            @mouseleave="hideTooltip"
          >
            <div
              v-for="(workout, i) in day.workouts"
              :key="i"
              class="day-segment"
              :style="{ backgroundColor: workout.colorHex }"
            />
          </div>
        </div>
      </div>
    </div>

    <div class="heatmap-footer">
      <span class="footer-total data-value">{{ totalWorkouts }} workout{{ totalWorkouts === 1 ? '' : 's' }}</span>
      <div v-if="legend.length" class="legend">
        <span v-for="item in legend" :key="item.type" class="legend-item">
          <span class="legend-dot" :style="{ backgroundColor: item.colorHex }"></span>
          {{ item.type }}
        </span>
      </div>
    </div>

    <div v-if="tooltip" class="heatmap-tooltip" :style="tooltipStyle">
      <p class="tooltip-date">{{ formatDateLabel(tooltip.day.date) }}</p>
      <template v-if="tooltip.day.workouts.length">
        <div v-for="(w, i) in tooltip.day.workouts" :key="i" class="tooltip-row">
          <span class="legend-dot" :style="{ backgroundColor: w.colorHex }"></span>
          <span>{{ w.type }}</span>
          <span class="tooltip-duration data-value">{{ w.durationMinutes }} min</span>
        </div>
      </template>
      <p v-else class="tooltip-rest">Rest day</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { getHeatmap } from '../services/workoutService'
import { toLocalDateStr, todayLocal } from '../utils/date'

const props = defineProps({
  refresh: Number
})

// GitHub-style: only label Mon/Wed/Fri to reduce noise (indexes are Sun..Sat).
const DAY_LABELS = ['', 'Mon', '', 'Wed', '', 'Fri', '']

// Cells are always square (aspect-ratio: 1). Instead of a fixed date range, the
// range adapts to the grid width so cells stay close to TARGET_CELL px on any
// screen: wider containers simply show more weeks. Keep CELL_GAP in sync with
// --cell-gap in the styles below.
const TARGET_CELL = 20
const CELL_GAP = 4
const MIN_WEEKS = 8
const MAX_WEEKS = 53

const rawData = ref([])
const startDate = ref(null)
const endDate = ref(null)
const tooltip = ref(null)
const gridEl = ref(null)

let resizeObserver = null
let resizeTimer = null

function parseLocalDate(dateStr) {
  const [year, month, day] = dateStr.split('-').map(Number)
  return new Date(year, month - 1, day)
}

// Sizes the visible range to the grid width (ending today, aligned to full
// Sunday–Saturday weeks) and reloads only if the range actually changed.
function updateRange() {
  if (!gridEl.value) return
  const width = gridEl.value.clientWidth
  if (width <= 0) return

  const columns = Math.min(MAX_WEEKS, Math.max(MIN_WEEKS,
    Math.round((width + CELL_GAP) / (TARGET_CELL + CELL_GAP))
  ))

  const end = new Date()
  const gridEnd = new Date(end)
  gridEnd.setDate(gridEnd.getDate() + (6 - gridEnd.getDay()))

  const start = new Date(gridEnd)
  start.setDate(start.getDate() - (columns * 7 - 1))

  const sameRange = startDate.value && endDate.value &&
    toLocalDateStr(start) === toLocalDateStr(startDate.value) &&
    toLocalDateStr(end) === toLocalDateStr(endDate.value)
  if (sameRange) return

  startDate.value = start
  endDate.value = end
  load()
}

async function load() {
  hideTooltip()
  if (!startDate.value || !endDate.value) return

  try {
    const response = await getHeatmap(toLocalDateStr(startDate.value), toLocalDateStr(endDate.value))
    rawData.value = response.data
  } catch {
    rawData.value = []
  }
}

onMounted(() => {
  updateRange()
  resizeObserver = new ResizeObserver(() => {
    clearTimeout(resizeTimer)
    resizeTimer = setTimeout(updateRange, 150)
  })
  resizeObserver.observe(gridEl.value)
  window.addEventListener('scroll', hideTooltip, { passive: true })
})
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  clearTimeout(resizeTimer)
  window.removeEventListener('scroll', hideTooltip)
})
watch(() => props.refresh, load)

const dataByDate = computed(() => {
  const map = {}
  for (const day of rawData.value) {
    map[day.date] = day.workouts
  }
  return map
})

const weeks = computed(() => {
  if (!startDate.value || !endDate.value) return []

  const today = todayLocal()
  const gridStart = new Date(startDate.value)
  gridStart.setDate(gridStart.getDate() - gridStart.getDay())

  const gridEnd = new Date(endDate.value)
  gridEnd.setDate(gridEnd.getDate() + (6 - gridEnd.getDay()))

  const result = []
  let currentWeek = []
  const cursor = new Date(gridStart)

  while (cursor <= gridEnd) {
    const dateStr = toLocalDateStr(cursor)
    const isPadding = cursor < startDate.value || cursor > endDate.value

    currentWeek.push({
      date: dateStr,
      isPadding,
      isToday: dateStr === today,
      workouts: dataByDate.value[dateStr] || []
    })

    if (currentWeek.length === 7) {
      result.push(currentWeek)
      currentWeek = []
    }
    cursor.setDate(cursor.getDate() + 1)
  }

  return result
})

const monthLabelMap = computed(() => {
  const map = {}
  let lastMonth = null

  weeks.value.forEach((week, weekIndex) => {
    const sunday = parseLocalDate(week[0].date)
    const month = sunday.getMonth()
    if (month !== lastMonth) {
      map[weekIndex] = sunday.toLocaleDateString('en-GB', { month: 'short' })
      lastMonth = month
    }
  })

  return map
})

const totalWorkouts = computed(() =>
  rawData.value.reduce((sum, day) => sum + day.workouts.length, 0)
)

const legend = computed(() => {
  const seen = new Map()
  for (const day of rawData.value) {
    for (const w of day.workouts) {
      if (!seen.has(w.type)) seen.set(w.type, w.colorHex)
    }
  }
  return [...seen.entries()].map(([type, colorHex]) => ({ type, colorHex }))
})

// Approx. half of the tooltip's max-width, used to keep it inside the viewport.
const TOOLTIP_HALF_WIDTH = 110
const TOOLTIP_OFFSET = 12

function showTooltip(day, event) {
  if (day.isPadding) return
  tooltip.value = { day, x: event.clientX, y: event.clientY }
}

function moveTooltip(event) {
  if (!tooltip.value) return
  tooltip.value.x = event.clientX
  tooltip.value.y = event.clientY
}

function hideTooltip() {
  tooltip.value = null
}

const tooltipStyle = computed(() => {
  if (!tooltip.value) return {}
  const x = Math.min(
    Math.max(tooltip.value.x, TOOLTIP_HALF_WIDTH),
    window.innerWidth - TOOLTIP_HALF_WIDTH
  )
  const above = tooltip.value.y > 150
  return {
    left: `${x}px`,
    top: `${tooltip.value.y}px`,
    transform: above
      ? `translate(-50%, calc(-100% - ${TOOLTIP_OFFSET}px))`
      : `translate(-50%, ${TOOLTIP_OFFSET}px)`
  }
})

function describeDay(day) {
  const label = formatDateLabel(day.date)
  if (day.workouts.length === 0) return `${label} — rest day`
  const details = day.workouts.map(w => `${w.type}, ${w.durationMinutes} min`).join('; ')
  return `${label} — ${details}`
}

function formatDateLabel(dateStr) {
  return parseLocalDate(dateStr).toLocaleDateString('en-GB', {
    weekday: 'short', day: 'numeric', month: 'short'
  })
}
</script>

<style scoped>
.heatmap-wrapper {
  --cell-gap: 4px;
  --labels-w: 30px;
  width: 100%;
}

.month-row {
  display: flex;
  margin-bottom: 6px;
}
.day-labels-spacer {
  width: calc(var(--labels-w) + 6px);
  flex-shrink: 0;
}
.month-inner {
  flex: 1;
  display: flex;
  gap: var(--cell-gap);
}
.month-slot {
  flex: 1;
  min-width: 0;
  font-size: 0.65rem;
  color: var(--text-muted);
  font-family: var(--font-data);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.content-row {
  display: flex;
  gap: 6px;
}
.day-labels {
  width: var(--labels-w);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: var(--cell-gap);
}
.day-label-cell {
  flex: 1;
  font-size: 0.6rem;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 2px;
}

.heatmap-grid {
  flex: 1;
  display: flex;
  gap: var(--cell-gap);
}
.week-column {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--cell-gap);
}
.day-cell {
  aspect-ratio: 1 / 1;
  border-radius: 4px;
  background: var(--bg);
  border: 1px solid var(--border);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  display: flex;
  outline: 1.5px solid transparent;
  outline-offset: 0.5px;
  transition: outline-color 0.1s ease;
}
.day-cell:not(.padding):hover {
  outline-color: rgba(237, 239, 244, 0.9);
}
.day-cell.today {
  outline-color: var(--blue);
}
.day-cell.today:not(.padding):hover {
  outline-color: var(--blue);
}
.day-cell.padding {
  visibility: hidden;
}
.day-segment {
  flex: 1;
  height: 100%;
}

.heatmap-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px var(--space-4);
  margin-top: var(--space-3);
  padding-left: calc(var(--labels-w) + 6px);
}
.footer-total {
  font-size: 0.72rem;
  color: var(--text-muted);
}
.legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.7rem;
  color: var(--text-muted);
  white-space: nowrap;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.heatmap-tooltip {
  position: fixed;
  z-index: 100;
  pointer-events: none;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  min-width: 110px;
  max-width: 220px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
}
.tooltip-date {
  margin: 0 0 6px;
  font-size: 0.72rem;
  font-weight: 600;
}
.tooltip-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 1px 0;
  font-size: 0.75rem;
}
.tooltip-duration {
  margin-left: auto;
  padding-left: 12px;
  font-size: 0.7rem;
  color: var(--text-muted);
}
.tooltip-rest {
  margin: 0;
  font-size: 0.72rem;
  color: var(--text-muted);
}

@media (max-width: 700px) {
  .day-labels {
    display: none;
  }
  .day-labels-spacer {
    width: 0;
  }
  .heatmap-footer {
    padding-left: 0;
  }
}
</style>
