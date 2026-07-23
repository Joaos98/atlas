<template>
  <div class="page">
    <h1>Workouts</h1>

    <section>
      <h2>Add new workout</h2>
      <div class="card">
        <WorkoutForm @logged="onLogged" />
      </div>
    </section>

    <section>
      <div class="overview-row">
        <div class="overview-card">
          <CalendarCheck :size="16" />
          <span class="overview-value">{{ thisWeekSessions }} / {{ targetPerWeek }}</span>
          <span class="overview-label">this week</span>
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
      <h2>Workout activity</h2>
      <div class="card">
        <WorkoutHeatmap :refresh="refreshKey" />
      </div>
    </section>

    <section>
      <div class="charts-row">
        <div class="card chart-card">
          <h2>Workouts per week</h2>
          <WeeklySessionsChart :refresh="refreshKey" />
        </div>
        <div class="card chart-card">
          <h2>Type breakdown</h2>
          <WorkoutTypeDonut :refresh="refreshKey" />
        </div>
      </div>
    </section>

    <section>
      <h2>Log history</h2>
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
      <p v-else class="empty-state">
        <span class="empty-icon"><Dumbbell :size="28" /></span>
        <span class="empty-title">No workout logs yet</span>
        <span class="empty-desc">Log your first workout above to start tracking.</span>
      </p>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import WorkoutForm from '../components/WorkoutForm.vue'
import WorkoutHeatmap from '../components/WorkoutHeatmap.vue'
import WeeklySessionsChart from '../components/WeeklySessionsChart.vue'
import WorkoutTypeDonut from '../components/WorkoutTypeDonut.vue'
import { getWorkoutTypes, getWorkoutLogs, deleteWorkoutLog, updateWorkoutLog, getHeatmap, getStreaks } from '../services/workoutService'
import { getSettings } from '../services/settingsService'
import { toLocalDateStr } from '../utils/date'
import { formatDateBr } from '../utils/date'
import DatePicker from '../components/DatePicker.vue'
import { Trash2, Pencil, Dumbbell, Flame, CalendarCheck } from 'lucide-vue-next'

const refreshKey = ref(0)
const types = ref([])
const logs = ref([])
const page = ref(0)
const totalPages = ref(0)
const targetPerWeek = ref(4)
const thisWeekSessions = ref(0)
const currentStreak = ref(0)
const longestStreak = ref(0)

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

onMounted(() => {
  loadTypes()
  loadLogs()
  loadOverview()
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
}

async function deleteLog(log) {
  if (!confirm(`Delete ${log.workoutType?.name} log from ${formatDateBr(log.logDate)}?`)) return
  try {
    await deleteWorkoutLog(log.id)
    refreshKey.value++
    await loadLogs()
  } catch (err) {
    console.error('Delete log error:', err.response?.status, err.message)
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
  } catch (err) {
    console.error('Update log error:', err.response?.status, err.message)
  }
}
</script>

<style scoped>
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
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-6) 0;
  color: var(--text-muted);
}
.empty-icon {
  color: var(--border);
  margin-bottom: var(--space-1);
}
.empty-title {
  font-weight: 600;
  color: var(--text-muted);
}
.empty-desc {
  font-size: 0.85rem;
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
