<template>
  <div v-if="loading" class="page">
    <h1>Goals</h1>
    <section>
      <div class="card card-fit">
        <GoalForm @created="load" />
      </div>
    </section>
    <section>
      <div class="goals-grid">
        <div v-for="i in 2" :key="i" class="goal-card">
          <SkeletonLoader height="0.8rem" width="50%" />
          <SkeletonLoader height="1.1rem" width="70%" />
          <SkeletonLoader height="1.5rem" />
          <div class="actions">
            <SkeletonLoader height="2rem" width="42%" />
            <SkeletonLoader height="2rem" width="30%" />
          </div>
        </div>
      </div>
    </section>
  </div>
  <div v-else class="page">
    <h1>Goals</h1>

    <section>
      <div class="card card-fit">
        <GoalForm @created="load" />
      </div>
    </section>

    <section>
      <div v-if="activeGoals.length" class="goals-grid">
        <div v-for="goal in activeGoals" :key="goal.id" class="goal-card" :class="{ 'on-track': goal.paceStatus === 'on_track', 'behind': goal.paceStatus === 'behind' }">
          <p class="metric-label">{{ metricLabel(goal.metricType) }}</p>
          <p class="data-value">
            {{ currentValue(goal.metricType) }}
            <span class="arrow">→</span>
            {{ formatWithUnit(goal.targetValue, goal.metricType) }}
          </p>

          <div class="progress-wrapper">
            <div class="progress-bg">
              <div class="progress-fill" :style="{ width: (goal.progressPercent || 0) + '%' }"></div>
            </div>
            <span class="progress-pct">{{ Math.min(100, Math.round(goal.progressPercent || 0)) }}%</span>
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
            <button class="achieve" @click="updateStatus(goal.id, 'ACHIEVED')">Achieve</button>
            <button class="abandon" @click="updateStatus(goal.id, 'ABANDONED')">Abandon</button>
            <button class="btn-icon delete-goal" title="Delete goal" @click="deleteGoalHandler(goal.id)"><Trash2 :size="14" /></button>
          </div>
        </div>
      </div>
      <EmptyState
        v-else
        :icon="Target"
        title="No active goals"
        description="Set a goal above to track your progress toward a target."
      />
    </section>

    <section v-if="pastGoals.length">
      <div class="goals-grid">
        <div v-for="goal in pastGoals" :key="goal.id" class="goal-card past">
          <p class="metric-label">{{ metricLabel(goal.metricType) }}</p>
          <p class="data-value past-range">
            {{ goal.startValue != null ? formatWithUnit(goal.startValue, goal.metricType) : '—' }}
            <span class="arrow">→</span>
            {{ formatWithUnit(goal.targetValue, goal.metricType) }}
            <span class="status-tag" :class="goal.status.toLowerCase()">{{ goal.status.toLowerCase() }}</span>
          </p>
          <p class="past-meta">
            <span>Created {{ formatDateBr(goal.createdAt) }}</span>
            <span v-if="goal.targetDate"> · Target: {{ formatDateBr(goal.targetDate) }}</span>
          </p>
          <button class="btn-icon delete-goal" title="Delete goal" @click="deleteGoalHandler(goal.id)"><Trash2 :size="12" /></button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getGoals, updateGoalStatus, deleteGoal } from '../services/goalsService'
import GoalForm from '../components/GoalForm.vue'
import EmptyState from '../components/EmptyState.vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import { getBodyMetrics } from '../services/bodyMetricsService'
import { useToastStore } from '../stores/toast'
import { useUnits } from '../composables/useUnits'
import { formatDateBr } from '../utils/date'
import { Trash2, Target } from 'lucide-vue-next'

const toast = useToastStore()
const { formatWithUnit } = useUnits()

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
  try {
    await updateGoalStatus(id, status)
    await load()
    toast.success(status === 'ACHIEVED' ? 'Goal marked as achieved' : 'Goal abandoned')
  } catch {
    toast.error('Failed to update goal')
  }
}

async function deleteGoalHandler(id) {
  if (!confirm('Delete this goal permanently?')) return
  try {
    await deleteGoal(id)
    await load()
    toast.success('Goal deleted')
  } catch {
    toast.error('Failed to delete goal')
  }
}

const labels = {
  WEIGHT: 'Weight', MUSCLE_MASS: 'Muscle mass',
  WATER: 'Water', BODY_FAT_KG: 'Body fat', BODY_FAT_PCT: 'Body fat'
}
function metricLabel(type) { return labels[type] || type }

function currentValue(metricType) {
  if (!latestMetrics.value) return '—'
  const value = latestMetrics.value[metricFields[metricType]]
  return value == null ? '—' : formatWithUnit(value, metricType)
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

  // Compared in canonical units above; only the distance being shown gets converted.
  const distance = formatWithUnit(Math.abs(current - goal.targetValue), goal.metricType)
  return { text: `${distance} to go`, cls: 'pending' }
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
  position: relative;
}
.goal-card.on-track { border-left-color: var(--green); }
.goal-card.behind { border-left-color: var(--orange); }
.goal-card.past { border-left-color: var(--border); }
.metric-label {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0 0 4px;
}
.arrow {
  color: var(--text-muted);
  margin: 0 6px;
}

.progress-wrapper {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 8px 0;
}
.progress-bg {
  flex: 1;
  height: 14px;
  background: var(--bg);
  border-radius: 7px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--green);
  border-radius: 7px;
  transition: width 0.4s ease;
  min-width: 0;
}
.goal-card.behind .progress-fill { background: var(--orange); }
.progress-pct {
  font-family: var(--font-data);
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text);
  min-width: 38px;
  text-align: right;
}

.remaining {
  font-size: 0.85rem;
  margin: 6px 0 0;
}
.remaining.reached { color: var(--green); }
.remaining.pending { color: var(--orange); }
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

.past-range {
  display: flex;
  align-items: center;
  gap: 0;
  flex-wrap: wrap;
}
.past-meta {
  font-size: 0.75rem;
  color: var(--text-muted);
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
.delete-goal {
  position: absolute;
  top: 8px;
  right: 8px;
}
</style>
