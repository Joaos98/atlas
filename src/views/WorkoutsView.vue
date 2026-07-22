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
      <h2>Workout activity</h2>
      <div class="card">
        <div class="heatmap-cap">
          <WorkoutHeatmap :refresh="refreshKey" />
        </div>
      </div>
    </section>

    <section>
      <div class="charts-row">
        <div class="card chart-card">
          <h2>Sessions per week</h2>
          <WeeklySessionsChart :refresh="refreshKey" />
        </div>
        <div class="card chart-card">
          <h2>Type breakdown</h2>
          <WorkoutTypeDonut :refresh="refreshKey" />
        </div>
        <div class="card chart-card">
          <h2>Workout types</h2>
          <div class="types-list">
            <span
              v-for="type in types"
              :key="type.id"
              class="type-tag"
            >
              <span class="type-dot" :style="{ backgroundColor: type.colorHex }"></span>
              {{ type.name }}
              <button class="btn-icon" title="Delete type" @click="deleteType(type)"><X :size="12" /></button>
            </span>
            <span v-if="types.length === 0" class="empty-line">No types yet.</span>
          </div>
          <div v-if="types.length < PALETTE.length" class="type-add">
            <div class="color-picker">
              <button
                v-for="(hex, i) in PALETTE"
                :key="i"
                type="button"
                class="color-dot"
                :class="{ selected: newTypeColor === hex }"
                :style="{ backgroundColor: hex }"
                @click="newTypeColor = hex"
              ></button>
            </div>
            <input v-model="newTypeName" placeholder="Type name" class="type-input" />
            <button class="btn-small" @click="addType">Add</button>
          </div>
          <p v-else class="muted-note">All 5 types in use.</p>
          <p v-if="typeError" class="form-error">{{ typeError }}</p>
        </div>
      </div>
    </section>

    <section>
      <h2>Log history</h2>
      <div v-if="logs.length" class="card table-card">
        <table>
          <thead>
            <tr>
              <th>Date</th><th>Type</th><th>Duration</th><th>Calories</th><th></th>
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
                <td class="data-value">{{ log.calories ? log.calories + ' Kcal' : '—' }}</td>
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
                <td><input type="number" v-model="editLogForm.calories" min="0" class="edit-input" /></td>
                <td class="edit-actions">
                  <button class="btn-small save" @click="saveEditLog">Save</button>
                  <button class="btn-small cancel" @click="cancelEditLog">Cancel</button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <p v-else class="empty">No workout logs yet.</p>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import WorkoutForm from '../components/WorkoutForm.vue'
import WorkoutHeatmap from '../components/WorkoutHeatmap.vue'
import WeeklySessionsChart from '../components/WeeklySessionsChart.vue'
import WorkoutTypeDonut from '../components/WorkoutTypeDonut.vue'
import { getWorkoutTypes, createWorkoutType, deleteWorkoutType, getWorkoutLogs, deleteWorkoutLog, updateWorkoutLog } from '../services/workoutService'
import { formatDateBr } from '../utils/date'
import DatePicker from '../components/DatePicker.vue'
import { X, Trash2, Pencil } from 'lucide-vue-next'

const PALETTE = ['#4F8DFF', '#8B5CF6', '#2DD4BF', '#F472B6', '#FACC15']

const refreshKey = ref(0)
const types = ref([])
const logs = ref([])
const newTypeName = ref('')
const newTypeColor = ref(PALETTE[0])
const typeError = ref('')

const editingLogId = ref(null)
const editLogForm = ref({
  logDate: '',
  workoutTypeId: null,
  durationMinutes: null,
  calories: null
})

async function loadTypes() {
  try {
    const res = await getWorkoutTypes()
    types.value = res.data
  } catch {
    types.value = []
  }
}

async function loadLogs() {
  try {
    const res = await getWorkoutLogs()
    const data = Array.isArray(res.data) ? res.data : (res.data.content || [])
    logs.value = data.sort((a, b) => b.logDate.localeCompare(a.logDate))
  } catch {
    logs.value = []
  }
}

onMounted(() => {
  loadTypes()
  loadLogs()
})

function onLogged() {
  refreshKey.value++
  loadLogs()
}

async function addType() {
  const name = newTypeName.value.trim()
  if (!name) return
  typeError.value = ''
  try {
    await createWorkoutType({ name, colorHex: newTypeColor.value })
    newTypeName.value = ''
    await loadTypes()
  } catch {
    typeError.value = 'Could not create type.'
  }
}

async function deleteType(type) {
  if (!confirm(`Delete workout type "${type.name}"?`)) return
  typeError.value = ''
  try {
    await deleteWorkoutType(type.id)
    await loadTypes()
    refreshKey.value++
  } catch {
    typeError.value = `Cannot delete "${type.name}" — it has existing workout logs.`
  }
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
    durationMinutes: log.durationMinutes,
    calories: log.calories
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
      durationMinutes: editLogForm.value.durationMinutes,
      calories: editLogForm.value.calories || null
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
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: var(--space-4);
}
.chart-card h2 {
  font-size: 1rem;
  margin: 0 0 12px;
}
.types-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: var(--space-3);
}
.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 5px 8px 5px 12px;
  font-size: 0.85rem;
}
.type-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  vertical-align: middle;
}
.type-add {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.color-picker {
  display: flex;
  gap: 6px;
}
.color-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  transition: border-color 0.15s;
}
.color-dot.selected {
  border-color: var(--text);
}
.color-dot:hover {
  border-color: var(--text-muted);
}
.color-dot.selected:hover {
  border-color: var(--text);
}
.type-input {
  width: 160px;
}
.btn-small {
  background: var(--blue);
  color: var(--bg);
  border: none;
  padding: 9px 14px;
  font-size: 0.85rem;
}
.btn-small:hover {
  filter: brightness(1.1);
}
.btn-icon {
  display: flex;
  align-items: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
}
.btn-icon:hover {
  color: var(--orange);
  background: rgba(251, 146, 60, 0.12);
}
.table-card {
  overflow-x: auto;
}
.table-card .type-dot {
  margin-right: 6px;
}
tbody tr:hover {
  background: rgba(237, 239, 244, 0.03);
}
.muted-note {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin: 0;
}
.empty-line {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.form-error {
  color: var(--orange);
  font-size: 0.85rem;
  margin: 8px 0 0;
}
.empty {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.heatmap-cap {
  max-width: 1000px;
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
</style>
