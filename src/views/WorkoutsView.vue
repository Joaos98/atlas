<template>
  <div class="page">
    <h1>Workouts</h1>

    <section>
      <div class="card">
        <WorkoutForm @logged="onLogged" />
      </div>
    </section>

    <template v-if="loading">
      <section>
        <div class="week-progress-row">
          <div class="week-progress">
            <SkeletonLoader height="2rem" width="100%" />
          </div>
          <div class="card skeleton-overview" v-for="i in 2" :key="'st-'+i">
            <SkeletonLoader height="0.85rem" width="60%" />
            <SkeletonLoader height="0.7rem" width="40%" />
          </div>
        </div>
      </section>
      <section>
        <div class="card"><SkeletonLoader height="160px" /></div>
      </section>
      <section>
        <div class="charts-row">
          <div class="card"><SkeletonLoader height="180px" /></div>
          <div class="card"><SkeletonLoader height="180px" /></div>
        </div>
      </section>
      <section>
        <SkeletonLoader height="40px" width="360px" />
      </section>
      <section>
        <div class="card table-card">
          <SkeletonLoader height="2.5rem" v-for="i in 5" :key="'row-'+i" />
        </div>
      </section>
    </template>

    <template v-else>

    <section>
      <div class="week-progress-row">
        <div class="week-progress">
          <span class="progress-label">This week</span>
          <div class="progress-bar-track">
            <div class="progress-bar-fill" :style="{ width: weekProgressPct + '%' }"></div>
          </div>
          <span class="progress-ratio data-value">{{ thisWeekSessions }} / {{ targetPerWeek }} workouts</span>
        </div>
        <div class="overview-card">
          <Flame :size="16" />
          <span class="overview-value">{{ currentStreak }} weeks</span>
          <span class="overview-label">current streak</span>
        </div>
        <div class="overview-card">
          <Flame :size="16" />
          <span class="overview-value">{{ longestStreak }} weeks</span>
          <span class="overview-label">longest streak</span>
        </div>
      </div>
    </section>

    <section>
      <div class="card">
        <WorkoutHeatmap :refresh="refreshKey" />
      </div>
    </section>

    <section>
      <div class="insight-range-row">
        <button
          v-for="preset in presets"
          :key="preset.months"
          class="range-preset"
          :class="{ active: activePreset === preset.months }"
          @click="setRangePreset(preset.months)"
        >{{ preset.label }}</button>
      </div>
      <div class="insight-stats-row">
        <div class="overview-card">
          <Dumbbell :size="16" />
          <span class="overview-value data-value">{{ insightStats.totalWorkouts }}</span>
          <span class="overview-label">workouts</span>
        </div>
        <div class="overview-card">
          <CalendarCheck :size="16" />
          <span class="overview-value data-value">{{ insightStats.avgPerWeek }}</span>
          <span class="overview-label">avg / week</span>
        </div>
        <div class="overview-card">
          <Target :size="16" />
          <span class="overview-value data-value">{{ insightStats.weeksOnTarget }} / {{ Math.round(insightStats.totalWeeks) }}</span>
          <span class="overview-label">weeks on target</span>
        </div>
        <div class="overview-card">
          <Activity :size="16" />
          <span class="overview-value data-value">{{ insightStats.consistency }}%</span>
          <span class="overview-label">consistency</span>
        </div>
        <div class="overview-card">
          <Clock :size="16" />
          <span class="overview-value data-value">{{ formatHours(insightStats.totalMinutes) }}</span>
          <span class="overview-label">total time</span>
        </div>
        <div class="overview-card">
          <TrendingUp :size="16" />
          <span class="overview-value data-value">{{ insightStats.avgDuration ? Math.round(insightStats.avgDuration) + ' min' : '—' }}</span>
          <span class="overview-label">avg duration</span>
        </div>
        <div class="overview-card">
          <Zap :size="16" />
          <span class="overview-value data-value">{{ insightStats.longestSession ? insightStats.longestSession + ' min' : '—' }}</span>
          <span class="overview-label">longest</span>
        </div>
        <div class="overview-card">
          <Star :size="16" />
          <span class="overview-value data-value">{{ insightStats.topType || '—' }}</span>
          <span class="overview-label">top type</span>
        </div>
      </div>
      <div class="insights-grid">
        <div class="card chart-card">
          <WeeklyWorkoutsChart :start-date="insightStartDate" :end-date="insightEndDate" :refresh="refreshKey" />
        </div>
        <div class="card chart-card">
          <WorkoutTypeDonut :start-date="insightStartDate" :end-date="insightEndDate" :refresh="refreshKey" />
        </div>
        <div class="card chart-card">
          <DayOfWeekChart :start-date="insightStartDate" :end-date="insightEndDate" :refresh="refreshKey" />
        </div>
        <div class="card chart-card">
          <DurationHistogram :start-date="insightStartDate" :end-date="insightEndDate" :refresh="refreshKey" />
        </div>
      </div>
    </section>

    <section>
      <h2 class="collapsible-header" @click="historyCollapsed = !historyCollapsed">
        <ChevronRight v-if="historyCollapsed" :size="14" class="collapse-icon" />
        <ChevronDown v-else :size="14" class="collapse-icon" />
        History
      </h2>
      <div v-show="!historyCollapsed">
      <div v-if="logs.length" class="card table-card">
        <table>
          <thead>
            <tr>
              <th>Date</th><th>Type</th><th>Duration</th><th></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="log in logs" :key="log.id">
              <tr v-if="editingLogId !== log.id">
                <td>{{ formatDateBr(log.logDate) }}</td>
                <td>
                  <span class="type-dot" :style="{ backgroundColor: log.workoutType?.colorHex }"></span>
                  {{ log.workoutType?.name }}
                </td>
                <td class="data-value">{{ log.durationMinutes }} min</td>
                <td class="actions-cell">
                  <button class="btn-icon" title="Edit log" @click="startEditLog(log)"><Pencil :size="14" /></button>
                  <button class="btn-icon" title="Delete log" @click="deleteLog(log)"><Trash2 :size="14" /></button>
                </td>
              </tr>
              <tr v-else class="edit-row">
                <td><DatePicker v-model="editLogForm.logDate" /></td>
                <td>
                  <select v-model="editLogForm.workoutTypeId" class="edit-select">
                    <option v-for="type in types" :key="type.id" :value="type.id">{{ type.name }}</option>
                  </select>
                </td>
                <td><input type="number" v-model="editLogForm.durationMinutes" min="1" class="edit-input" /></td>
                <td class="edit-actions">
                  <button class="btn-small save" @click="saveEditLog">Save</button>
                  <button class="btn-small cancel" @click="cancelEditLog">Cancel</button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <div v-if="totalPages > 1" class="pagination">
          <button :disabled="page === 0" @click="loadLogs(page - 1)">Previous</button>
          <span class="page-indicator">Page {{ page + 1 }} of {{ totalPages }}</span>
          <button :disabled="page >= totalPages - 1" @click="loadLogs(page + 1)">Next</button>
        </div>
      </div>
      <EmptyState
        v-else
        :icon="Dumbbell"
        title="No workout logs yet"
        description="Log your first workout above to start tracking."
      />
      </div>
    </section>
  </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import WorkoutForm from '../components/WorkoutForm.vue'
import WorkoutHeatmap from '../components/WorkoutHeatmap.vue'
import WeeklyWorkoutsChart from '../components/WeeklyWorkoutsChart.vue'
import WorkoutTypeDonut from '../components/WorkoutTypeDonut.vue'
import DayOfWeekChart from '../components/DayOfWeekChart.vue'
import DurationHistogram from '../components/DurationHistogram.vue'
import EmptyState from '../components/EmptyState.vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import { getWorkoutTypes, getWorkoutLogs, deleteWorkoutLog, updateWorkoutLog, getHeatmap, getStreaks } from '../services/workoutService'
import { getSettings } from '../services/settingsService'
import { toLocalDateStr } from '../utils/date'
import { formatDateBr } from '../utils/date'
import DatePicker from '../components/DatePicker.vue'
import { useToastStore } from '../stores/toast'
import { Trash2, Pencil, Dumbbell, Flame, Clock, TrendingUp, Star, Zap, CalendarCheck, Activity, Target, ChevronRight, ChevronDown } from 'lucide-vue-next'

const toast = useToastStore()

const refreshKey = ref(0)
const loading = ref(true)
const types = ref([])
const logs = ref([])
const historyCollapsed = ref(true)
const page = ref(0)
const totalPages = ref(0)
const targetPerWeek = ref(4)
const thisWeekSessions = ref(0)
const currentStreak = ref(0)
const longestStreak = ref(0)

const insightStats = ref({ totalWorkouts: 0, totalMinutes: 0, avgDuration: 0, topType: '', longestSession: 0, avgPerWeek: 0, consistency: 0, weeksOnTarget: 0, totalWeeks: 0 })

const twoMonthsAgo = new Date()
twoMonthsAgo.setMonth(twoMonthsAgo.getMonth() - 2)
const insightStartDate = ref(toLocalDateStr(twoMonthsAgo))
const insightEndDate = ref(toLocalDateStr(new Date()))
const activePreset = ref(2)

const presets = [
  { months: 1, label: 'Last month' },
  { months: 2, label: 'Last 2 months' },
  { months: 3, label: 'Last 3 months' },
  { months: 6, label: 'Last 6 months' },
  { months: 12, label: 'Last year' }
]

function setRangePreset(months) {
  activePreset.value = months
  const start = new Date()
  start.setMonth(start.getMonth() - months)
  insightStartDate.value = toLocalDateStr(start)
  insightEndDate.value = toLocalDateStr(new Date())
  loadInsightStats()
}

const weekProgressPct = computed(() => {
  if (targetPerWeek.value <= 0) return 0
  return Math.min(100, (thisWeekSessions.value / targetPerWeek.value) * 100)
})

function formatHours(totalMinutes) {
  if (!totalMinutes) return '0h'
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return m > 0 ? `${h}h ${m}m` : `${h}h`
}

async function loadInsightStats() {
  try {
    const res = await getHeatmap(insightStartDate.value, insightEndDate.value)
    const data = Array.isArray(res.data) ? res.data : (res.data.content || [])
    let totalWorkouts = 0
    let totalMinutes = 0
    let longestSession = 0
    let activeDays = 0
    const typeCounts = {}

    for (const day of data) {
      const workouts = Array.isArray(day.workouts) ? day.workouts : []
      if (workouts.length > 0) activeDays++
      for (const w of workouts) {
        totalWorkouts++
        totalMinutes += w.durationMinutes || 0
        if (w.durationMinutes > longestSession) longestSession = w.durationMinutes
        typeCounts[w.type] = (typeCounts[w.type] || 0) + 1
      }
    }

    let topType = ''
    let topCount = 0
    for (const [type, count] of Object.entries(typeCounts)) {
      if (count > topCount) { topType = type; topCount = count }
    }

    const startDate = new Date(insightStartDate.value + 'T00:00:00')
    const endDate = new Date(insightEndDate.value + 'T00:00:00')
    const totalDays = Math.max(1, Math.round((endDate - startDate) / 86400000) + 1)
    const totalWeeks = Math.max(1, totalDays / 7)

    const dayMap = {}
    for (const day of data) {
      dayMap[day.date] = (Array.isArray(day.workouts) ? day.workouts : []).length
    }

    const sundayStart = new Date(startDate)
    sundayStart.setDate(sundayStart.getDate() - sundayStart.getDay())
    let weeksOnTarget = 0
    const cursor = new Date(sundayStart)
    while (cursor.getTime() <= endDate.getTime()) {
      let sessions = 0
      for (let i = 0; i < 7; i++) {
        sessions += dayMap[toLocalDateStr(cursor)] || 0
        cursor.setDate(cursor.getDate() + 1)
      }
      if (sessions >= targetPerWeek.value) weeksOnTarget++
    }

    insightStats.value = {
      totalWorkouts,
      totalMinutes,
      avgDuration: totalWorkouts > 0 ? totalMinutes / totalWorkouts : 0,
      topType,
      longestSession,
      avgPerWeek: (totalWorkouts / totalWeeks).toFixed(1),
      consistency: Math.round((activeDays / totalDays) * 100),
      weeksOnTarget,
      totalWeeks: Math.round(totalWeeks)
    }
  } catch {
    insightStats.value = { totalWorkouts: 0, totalMinutes: 0, avgDuration: 0, topType: '', longestSession: 0, avgPerWeek: 0, consistency: 0, weeksOnTarget: 0, totalWeeks: 0 }
  }
}

const editingLogId = ref(null)
const editLogForm = ref({
  logDate: '',
  workoutTypeId: null,
  durationMinutes: null
})

async function loadTypes() {
  try {
    const res = await getWorkoutTypes()
    types.value = res.data
  } catch {
    types.value = []
  }
}

async function loadLogs(p = page.value) {
  try {
    const res = await getWorkoutLogs({ page: p, size: 20 })
    logs.value = res.data.content || []
    totalPages.value = res.data.totalPages || 0
    page.value = p
  } catch {
    logs.value = []
    totalPages.value = 0
  }
}

onMounted(async () => {
  await Promise.all([loadTypes(), loadLogs(), loadOverview(), loadInsightStats()])
  loading.value = false
})

async function loadOverview() {
  try {
    const today = new Date()
    const sunday = new Date(today)
    sunday.setDate(sunday.getDate() - sunday.getDay())
    const nextSunday = new Date(sunday)
    nextSunday.setDate(nextSunday.getDate() + 7)

    const [settingsRes, heatmapRes, streaksRes] = await Promise.all([
      getSettings(),
      getHeatmap(toLocalDateStr(sunday), toLocalDateStr(nextSunday)),
      getStreaks()
    ])
    targetPerWeek.value = settingsRes.data.targetWorkoutsPerWeek ?? 4
    thisWeekSessions.value = heatmapRes.data.reduce((sum, d) => sum + d.workouts.length, 0)
    currentStreak.value = streaksRes.data.currentStreak
    longestStreak.value = streaksRes.data.longestStreak
  } catch {
    targetPerWeek.value = 4
    thisWeekSessions.value = 0
    currentStreak.value = 0
    longestStreak.value = 0
  }
}

function onLogged() {
  refreshKey.value++
  loadLogs(0)
  loadOverview()
  loadInsightStats()
}

async function deleteLog(log) {
  if (!confirm(`Delete ${log.workoutType?.name} log from ${formatDateBr(log.logDate)}?`)) return
  try {
    await deleteWorkoutLog(log.id)
    refreshKey.value++
    await loadLogs()
    toast.success('Workout deleted')
  } catch (err) {
    toast.error('Failed to delete workout')
  }
}

function startEditLog(log) {
  editingLogId.value = log.id
  editLogForm.value = {
    logDate: log.logDate,
    workoutTypeId: log.workoutType?.id,
    durationMinutes: log.durationMinutes
  }
}

function cancelEditLog() {
  editingLogId.value = null
}

async function saveEditLog() {
  const id = editingLogId.value
  try {
    await updateWorkoutLog(id, {
      logDate: editLogForm.value.logDate,
      workoutType: { id: editLogForm.value.workoutTypeId },
      durationMinutes: editLogForm.value.durationMinutes
    })
    editingLogId.value = null
    refreshKey.value++
    await loadLogs()
    toast.success('Workout updated')
  } catch {
    toast.error('Failed to update workout')
  }
}
</script>

<style scoped>
.collapsible-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  color: var(--text-muted);
}
.collapsible-header:hover { color: var(--text); }
.collapse-icon { flex-shrink: 0; }

.overview-row {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}
.overview-card {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 14px;
  color: var(--text-muted);
}
.overview-value {
  font-family: var(--font-data);
  font-weight: 600;
  color: var(--text);
}
.overview-label {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}
.chart-card h2 {
  font-size: 1rem;
  margin: 0 0 12px;
}
.table-card {
  overflow-x: auto;
}
.table-card .type-dot {
  margin-right: 6px;
}
.type-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  vertical-align: middle;
}
tbody tr:hover {
  background: rgba(237, 239, 244, 0.03);
}
.empty {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.skeleton-overview {
  padding: 10px 14px;
  min-width: 140px;
}

.week-progress-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}
.week-progress {
  flex: 1;
  min-width: 220px;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px 16px;
}
.progress-label {
  font-size: 0.85rem;
  color: var(--text-muted);
  white-space: nowrap;
}
.progress-bar-track {
  flex: 1;
  height: 8px;
  background: var(--bg);
  border-radius: 4px;
  overflow: hidden;
}
.progress-bar-fill {
  height: 100%;
  background: var(--green);
  border-radius: 4px;
  transition: width 0.4s ease;
}
.progress-ratio {
  font-size: 0.85rem;
  color: var(--text);
  white-space: nowrap;
}

.insight-stats-row {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
}

.insights-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.insight-range-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  flex-wrap: wrap;
}
.range-preset {
  font-size: 0.8rem;
  padding: 5px 12px;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-muted);
  border-radius: 6px;
  cursor: pointer;
}
.range-preset:hover { border-color: var(--border-hover); color: var(--text); }
.range-preset.active {
  background: var(--blue);
  border-color: var(--blue);
  color: var(--bg);
}
.edit-row {
  background: rgba(79, 141, 255, 0.06);
}
.edit-row td {
  padding: 4px 8px;
}
.edit-input {
  width: 75px;
  padding: 5px 6px;
  font-size: 0.8rem;
}
.edit-select {
  width: 120px;
  padding: 6px 8px;
  font-size: 0.85rem;
  border-radius: 6px;
}
.edit-actions {
  display: flex;
  gap: 6px;
}
.btn-small {
  padding: 6px 12px;
  font-size: 0.8rem;
  border-radius: 6px;
  border: none;
  cursor: pointer;
}
.btn-small.save {
  background: var(--green);
  color: var(--bg);
}
.btn-small.save:hover { filter: brightness(1.1); }
.btn-small.cancel {
  background: transparent;
  color: var(--text-muted);
  border: 1px solid var(--border);
}
.btn-small.cancel:hover {
  color: var(--text);
  border-color: var(--border-hover);
}
.actions-cell {
  display: flex;
  gap: 4px;
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
  color: var(--orange);
  background: rgba(251, 146, 60, 0.12);
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}
.pagination button {
  font-size: 0.85rem;
}
.pagination button:disabled {
  opacity: 0.3;
  cursor: default;
}
.page-indicator {
  font-size: 0.85rem;
  color: var(--text-muted);
}
</style>
