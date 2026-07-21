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
        <span v-for="d in ['Sun','Mon','Tue','Wed','Thu','Fri','Sat']" :key="d" class="day-label-cell">{{ d }}</span>
      </div>
      <div class="heatmap-grid">
        <div v-for="(week, weekIndex) in weeks" :key="weekIndex" class="week-column">
          <div
            v-for="(day, dayIndex) in week"
            :key="dayIndex"
            class="day-cell"
            :class="{ padding: day.isPadding }"
            :title="day.isPadding ? '' : formatTooltip(day)"
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
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { getHeatmap } from '../services/workoutService'

const props = defineProps({
  refresh: Number
})

const rawData = ref([])
const startDate = ref(null)
const endDate = ref(null)

function toLocalDateStr(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseLocalDate(dateStr) {
  const [year, month, day] = dateStr.split('-').map(Number)
  return new Date(year, month - 1, day)
}

async function load() {
  const end = new Date()
  const start = new Date()
  start.setMonth(start.getMonth() - 6)
  endDate.value = end
  startDate.value = start

  const response = await getHeatmap(toLocalDateStr(start), toLocalDateStr(end))
  rawData.value = response.data
}

onMounted(load)
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

function formatTooltip(day) {
  const label = formatDateLabel(day.date)
  if (day.workouts.length === 0) return `${label} — rest day`
  const details = day.workouts
    .map(w => `${w.type} — ${w.durationMinutes} min${w.calories ? `, ${w.calories} kcal` : ''}`)
    .join('\n')
  return `${label}\n${details}`
}

function formatDateLabel(dateStr) {
  return parseLocalDate(dateStr).toLocaleDateString('en-GB', {
    weekday: 'short', day: 'numeric', month: 'short'
  })
}
</script>

<style scoped>
.heatmap-wrapper { width: 100%; }
.month-row {
  display: flex;
  margin-bottom: 8px;
}
.day-labels-spacer { width: 44px; flex-shrink: 0; }
.month-inner { flex: 1; display: flex; gap: 5px; }
.month-slot {
  flex: 1;
  font-size: 0.7rem;
  color: var(--text-muted);
  font-family: var(--font-data);
  white-space: nowrap;
  overflow: hidden;
}
.content-row {
  display: flex;
  gap: 10px;
}
.day-labels {
  width: 34px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.day-label-cell {
  flex: 1;
  font-size: 0.65rem;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 4px;
}
.heatmap-grid {
  flex: 1;
  display: flex;
  gap: 5px;
}
.week-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.day-cell {
  aspect-ratio: 1 / 1;
  width: 100%;
  border-radius: 6px;
  background: var(--bg);
  border: 1px solid var(--border);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  display: flex;
  transition: transform 0.1s ease;
}
.day-cell:not(.padding):hover {
  transform: scale(1.15);
  z-index: 1;
}
.day-cell.padding {
  visibility: hidden;
}
.day-segment {
  flex: 1;
  height: 100%;
}
</style>