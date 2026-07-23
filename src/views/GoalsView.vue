<template>
  <div v-if="loading" class="loading"></div>
  <div v-else class="page">
    <h1>Goals</h1>

    <section>
      <h2>New goal</h2>
      <div class="card card-fit">
        <GoalForm @created="load" />
      </div>
    </section>

    <section>
      <h2>Active goals</h2>
      <div v-if="activeGoals.length" class="goals-grid">
        <div v-for="goal in activeGoals" :key="goal.id" class="goal-card">
          <p class="metric-label">{{ metricLabel(goal.metricType) }}</p>
          <p class="data-value">
            {{ currentValue(goal.metricType) }}{{ metricUnit(goal.metricType) }}
            <span class="arrow">→</span>
            {{ goal.targetValue }}{{ metricUnit(goal.metricType) }}
          </p>

          <div v-if="goal.startValue != null" class="progress-bar">
            <div class="progress-fill" :style="{ width: (goal.progressPercent || 0) + '%' }"></div>
          </div>

          <p v-if="goalProgress(goal)" class="remaining" :class="goalProgress(goal).cls">
            {{ goalProgress(goal).text }}
          </p>

          <p v-if="goal.eta" class="eta">On pace for {{ formatDateBr(goal.eta) }}</p>

          <p v-if="goal.paceStatus" class="pace" :class="goal.paceStatus">
            {{ goal.paceStatus === 'on_track' ? 'On track' : 'Behind pace' }}
          </p>

          <p v-if="goal.targetDate" class="target-date">Target date: {{ formatDateBr(goal.targetDate) }}</p>
          <div class="actions">
            <button class="achieve" @click="updateStatus(goal.id, 'ACHIEVED')">Mark achieved</button>
            <button class="abandon" @click="updateStatus(goal.id, 'ABANDONED')">Abandon</button>
            <button class="btn-icon delete-goal" title="Delete goal" @click="deleteGoalHandler(goal.id)"><Trash2 :size="14" /></button>
          </div>
        </div>
      </div>
      <p v-else class="empty-state">
        <span class="empty-icon"><Target :size="28" /></span>
        <span class="empty-title">No active goals</span>
        <span class="empty-desc">Set a goal above to track your progress toward a target.</span>
      </p>
    </section>

    <section>
      <h2>Past goals</h2>
      <div v-if="pastGoals.length" class="goals-grid">
        <div v-for="goal in pastGoals" :key="goal.id" class="goal-card past">
          <p class="metric-label">{{ metricLabel(goal.metricType) }}</p>
          <p class="data-value">
            {{ goal.targetValue }}{{ metricUnit(goal.metricType) }}
            <span class="status-tag" :class="goal.status.toLowerCase()">{{ goal.status.toLowerCase() }}</span>
            <button class="btn-icon delete-goal" title="Delete goal" @click="deleteGoalHandler(goal.id)"><Trash2 :size="12" /></button>
          </p>
        </div>
      </div>
      <p v-else class="empty">No past goals.</p>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getGoals, updateGoalStatus, deleteGoal } from '../services/goalsService'
import GoalForm from '../components/GoalForm.vue'
import { getBodyMetrics } from '../services/bodyMetricsService'
import { formatDateBr } from '../utils/date'
import { Trash2 } from 'lucide-vue-next'

const loading = ref(true)
const latestMetrics = ref(null)

const goals = ref([])

async function load() {
  const [goalsRes, metricsRes] = await Promise.all([getGoals(), getBodyMetrics()])
  goals.value = goalsRes.data
  const entries = Array.isArray(metricsRes.data) ? metricsRes.data : (metricsRes.data.content || [])
  if (entries.length > 0) {
    latestMetrics.value = entries[entries.length - 1]
  }
}

const metricFields = {
  WEIGHT: 'weightKg', MUSCLE_MASS: 'muscleMassKg',
  WATER: 'waterLiters', BODY_FAT_KG: 'bodyFatKg', BODY_FAT_PCT: 'bodyFatPct'
}

async function initialLoad() {
  loading.value = true
  await load()
  loading.value = false
}

onMounted(initialLoad)

const activeGoals = computed(() => goals.value.filter(g => g.status === 'ACTIVE'))
const pastGoals = computed(() => goals.value.filter(g => g.status !== 'ACTIVE'))

async function updateStatus(id, status) {
  await updateGoalStatus(id, status)
  loading.value = true
  await load()
  loading.value = false
}

async function deleteGoalHandler(id) {
  if (!confirm('Delete this goal permanently?')) return
  try {
    await deleteGoal(id)
    await load()
  } catch (err) {
    console.error('Delete goal error:', err.response?.status, err.message)
  }
}

const labels = {
  WEIGHT: 'Weight', MUSCLE_MASS: 'Muscle mass',
  WATER: 'Water', BODY_FAT_KG: 'Body fat', BODY_FAT_PCT: 'Body fat'
}
const units = {
  WEIGHT: ' kg', MUSCLE_MASS: ' kg',
  WATER: ' L', BODY_FAT_KG: ' kg', BODY_FAT_PCT: '%'
}

function metricLabel(type) { return labels[type] || type }
function metricUnit(type) { return units[type] || '' }
function currentValue(metricType) {
  if (!latestMetrics.value) return '—'
  const field = metricFields[metricType]
  return latestMetrics.value[field] ?? '—'
}

function goalProgress(goal) {
  if (!latestMetrics.value) return null
  const field = metricFields[goal.metricType]
  const current = latestMetrics.value[field]
  if (current == null) return null

  const EPS = 0.05
  // Goal direction: body fat goals are down-goals, muscle mass is an up-goal,
  // weight/water infer direction from where the target sits relative to now
  let isDownGoal
  if (['BODY_FAT_KG', 'BODY_FAT_PCT'].includes(goal.metricType)) isDownGoal = true
  else if (goal.metricType === 'MUSCLE_MASS') isDownGoal = false
  else isDownGoal = goal.targetValue < current

  const reached =
    Math.abs(current - goal.targetValue) < EPS ||
    (isDownGoal ? current <= goal.targetValue : current >= goal.targetValue)

  if (reached) return { text: 'Target reached ✓', cls: 'reached' }

  const distance = Math.abs(current - goal.targetValue).toFixed(1)
  return { text: `${distance}${metricUnit(goal.metricType)} to go`, cls: 'pending' }
}
</script>

<style scoped>
.goals-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-3);
}
.goal-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-left: 3px solid var(--purple);
  border-radius: 8px;
  padding: 14px 18px;
}
.goal-card.past {
  border-left-color: var(--border);
}
.metric-label {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0 0 4px;
}
.arrow {
  color: var(--text-muted);
  margin: 0 6px;
}
.progress-bar {
  height: 4px;
  background: var(--bg);
  border-radius: 2px;
  margin: 6px 0;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--green);
  border-radius: 2px;
  transition: width 0.3s ease;
}
.remaining {
  font-size: 0.85rem;
  margin: 6px 0 0;
}
.remaining.reached {
  color: var(--green);
}
.remaining.pending {
  color: var(--orange);
}
.eta {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 6px 0 0;
}
.pace {
  font-size: 0.8rem;
  margin: 4px 0 0;
}
.pace.on_track { color: var(--green); }
.pace.behind { color: var(--orange); }
.target-date {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin: 4px 0 0;
}
.actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.achieve {
  background: var(--green);
  color: var(--bg);
  border: none;
  font-size: 0.8rem;
}
.achieve:hover { filter: brightness(1.1); }
.abandon {
  background: transparent;
  color: var(--text-muted);
  font-size: 0.8rem;
}
.abandon:hover { color: var(--text); background: var(--bg); }
.status-tag {
  font-family: var(--font-body);
  font-size: 0.7rem;
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 4px;
}
.status-tag.achieved { background: rgba(61, 214, 140, 0.15); color: var(--green); }
.status-tag.abandoned { background: rgba(140, 147, 166, 0.15); color: var(--text-muted); }
.empty { color: var(--text-muted); font-size: 0.85rem; }
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
.btn-icon {
  display: inline-flex;
  align-items: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
  cursor: pointer;
}
.delete-goal:hover {
  color: var(--orange);
  background: rgba(251, 146, 60, 0.12);
}
</style>
