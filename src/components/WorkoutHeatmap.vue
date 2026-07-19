<template>
  <div class="heatmap-wrapper">
    <div class="month-row">
      <span class="day-labels-spacer"></span>
      <span
        v-for="label in monthLabels"
        :key="label.weekIndex"
        class="month-label"
        :style="{ gridColumnStart: label.weekIndex + 2 }"
      >
        {{ label.text }}
      </span>
    </div>
    <div class="content-row">
      <div class="day-labels">
        <span v-for="d in ['Sun','Mon','Tue','Wed','Thu','Fri','Sat']" :key="d" class="day-label-cell">{{ d }}</span>
      </div>
      <div class="heatmap-grid">
        <template v-for="(week, weekIndex) in weeks" :key="weekIndex">
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
        </template>
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

async function load() {
  const end = new Date()
  const start = new Date()
  start.setMonth(start.getMonth() - 3)
  endDate.value = end
  startDate.value = start

  const response = await getHeatmap(
    start.toISOString().split('T')[0],
    end.toISOString().split('T')[0]
  )
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

// build a Sunday-to-Saturday grid covering the full range
const weeks = computed(() => {
  if (!startDate.value || !endDate.value) return []

  const gridStart = new Date(startDate.value)
  gridStart.setDate(gridStart.getDate() - gridStart.getDay()) // back up to Sunday

  const gridEnd = new Date(endDate.value)
  gridEnd.setDate(gridEnd.getDate() + (6 - gridEnd.getDay())) // forward to Saturday

  const result = []
  let currentWeek = []
  const cursor = new Date(gridStart)

  while (cursor <= gridEnd) {
    const dateStr = cursor.toISOString().split('T')[0]
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

// place a month label above the first week that starts a new month
const monthLabels = computed(() => {
  const labels = []
  let lastMonth = null

  weeks.value.forEach((week, weekIndex) => {
    const sunday = new Date(week[0].date)
    const month = sunday.getMonth()
    if (month !== lastMonth) {
      labels.push({
        weekIndex,
        text: sunday.toLocaleDateString('en-GB', { month: 'short' })
      })
      lastMonth = month
    }
  })

  return labels
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
  return new Date(dateStr).toLocaleDateString('en-GB', {
    weekday: 'short', day: 'numeric', month: 'short'
  })
}
</script>

<style scoped>
.heatmap-wrapper {
  display: inline-block;
}
.month-row {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 14px;
  gap: 3px;
  margin-bottom: 4px;
}
.month-label {
  font-size: 0.65rem;
  color: #888;
}
.heatmap-grid {
  display: grid;
  grid-auto-flow: column;
  grid-template-rows: repeat(7, 14px);
  grid-auto-columns: 14px;
  gap: 3px;
}
.day-cell {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1px solid #ddd;
  overflow: hidden;
  display: flex;
}
.day-cell.padding {
  visibility: hidden;
}
.day-segment {
  flex: 1;
  height: 100%;
}
.content-row {
  display: flex;
  gap: 6px;
}
.day-labels {
  width: 24px;
  display: grid;
  grid-template-rows: repeat(7, 14px);
  gap: 3px;
}
.day-label-cell {
  font-size: 0.6rem;
  color: #888;
  line-height: 14px;
  text-align: right;
}
.day-labels-spacer {
  grid-column: 1;
  width: 24px;
}
</style>