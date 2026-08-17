<template>
  <!-- Outside every branch on purpose. These notices explain why a dashboard is empty, so
       they have to survive the case where the rest of it failed to load — which is exactly
       the situation a user with an unrecognised device is in. -->
  <SyncNotices v-if="!loading" class="page notices-only" />

  <div v-if="loading" class="page">
    <section>
      <div class="insights-row">
        <div class="card">
          <SkeletonLoader height="0.9rem" width="60%" />
          <SkeletonLoader height="0.85rem" />
          <SkeletonLoader height="0.85rem" width="92%" />
          <SkeletonLoader height="0.85rem" width="75%" />
          <SkeletonLoader height="0.85rem" width="48%" />
        </div>
        <div class="side-cols">
          <div class="stat-col">
            <div class="card" v-for="i in 3" :key="'stat-'+i">
              <SkeletonLoader height="0.75rem" width="60%" />
              <SkeletonLoader height="1.2rem" width="80%" />
            </div>
          </div>
          <div class="body-comp-col">
            <div class="card" v-for="i in 3" :key="'comp-'+i">
              <SkeletonLoader height="0.75rem" width="50%" />
              <SkeletonLoader height="1.3rem" width="40%" />
            </div>
          </div>
        </div>
      </div>
    </section>
    <section>
      <div class="activity-row">
        <div class="card">
          <SkeletonLoader height="0.85rem" width="25%" />
          <SkeletonLoader height="180px" />
        </div>
        <div class="card">
          <SkeletonLoader height="0.85rem" width="30%" />
          <SkeletonLoader height="180px" />
        </div>
      </div>
    </section>
  </div>
  <div v-else-if="stats" class="page">
    <section v-if="isNewUser">
      <div class="card welcome-card">
        <h2>Welcome to Atlas</h2>
        <p class="welcome-desc">Start tracking your fitness journey:</p>
        <div class="welcome-actions">
          <RouterLink to="/workouts" class="welcome-btn"><Dumbbell :size="16" /> Log your first workout</RouterLink>
          <RouterLink to="/body-metrics" class="welcome-btn"><Scale :size="16" /> Add your first measurement</RouterLink>
        </div>
      </div>
    </section>

    <section>
      <div class="insights-row">
        <InsightCard
          v-if="insightText !== undefined"
          :insight="insightCardData"
          :loading="insightLoading"
          @regenerate="refreshInsight"
        >
          <template #cta>
            <RouterLink to="/body-metrics">Add a new measurement</RouterLink> to generate one.
          </template>
        </InsightCard>
        <div class="side-cols" :class="{ 'with-note': stats.bodyCompositionStats.totalMeasurements >= 2, full: insightText === undefined }">
          <p v-if="stats.bodyCompositionStats.totalMeasurements >= 2" class="body-comp-note">All-time, since first measurement</p>
          <div class="stat-col">
            <StatCard
              label="Workouts this week"
              :value="`${thisWeekSessions} / ${targetPerWeek}`"
              color="purple"
              :icon="CalendarCheck"
              :subtitle="weekSubtitle"
            />
            <StatCard label="Current streak" :value="`${stats.streakStats.currentStreak} weeks`" color="purple" :icon="Flame" />
            <StatCard label="Longest streak" :value="`${stats.streakStats.longestStreak} weeks`" color="purple" :icon="Flame" />
          </div>
          <div class="body-comp-col">
            <template v-if="stats.bodyCompositionStats.totalMeasurements >= 2">
              <div class="body-comp-card" :class="compColor(stats.bodyCompositionStats.weightChangeKg, null)">
                <div class="body-comp-header">
                  <Scale :size="16" />
                  <span>Weight</span>
                </div>
                <p class="body-comp-value">{{ formatChange(stats.bodyCompositionStats.weightChangeKg, 'WEIGHT') }}<span class="unit"> {{ label('WEIGHT') }}</span></p>
              </div>
              <div class="body-comp-card" :class="compColor(stats.bodyCompositionStats.muscleMassChangeKg, 'up')">
                <div class="body-comp-header">
                  <TrendingUp :size="16" />
                  <span>Muscle mass</span>
                </div>
                <p class="body-comp-value">{{ formatChange(stats.bodyCompositionStats.muscleMassChangeKg, 'MUSCLE_MASS') }}<span class="unit"> {{ label('MUSCLE_MASS') }}</span></p>
              </div>
              <div class="body-comp-card" :class="compColor(stats.bodyCompositionStats.bodyFatPctChange, 'down')">
                <div class="body-comp-header">
                  <TrendingDown :size="16" />
                  <span>Body fat</span>
                </div>
                <p class="body-comp-value">{{ formatChange(stats.bodyCompositionStats.bodyFatPctChange, 'BODY_FAT_PCT') }}<span class="unit"> {{ label('BODY_FAT_PCT') }}</span></p>
              </div>
            </template>
            <div v-else class="card body-comp-empty">
              <p class="empty-hint">Add at least 2 measurements to see composition changes.</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="bodyMetrics.length >= 2">
      <LatestMeasurementStats :metrics="bodyMetrics" />
    </section>

    <section>
      <div class="activity-row">
        <div class="card">
          <WorkoutHeatmap :refresh="0" />
        </div>
        <div class="card chart-card">
          <WeeklyWorkoutsChart :refresh="0" />
        </div>
      </div>
    </section>
  </div>
  <div v-else>Could not load stats.</div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { getStats } from '../services/statsService'
import { useSettingsStore } from '../stores/settings'
import { getHeatmap } from '../services/workoutService'
import { getBodyMetrics } from '../services/bodyMetricsService'
import { toLocalDateStr, formatDateBr } from '../utils/date'
import { getInsights, regenerateInsights } from '../services/insightService'
import StatCard from '../components/StatCard.vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import WorkoutHeatmap from '../components/WorkoutHeatmap.vue'
import WeeklyWorkoutsChart from '../components/WeeklyWorkoutsChart.vue'
import LatestMeasurementStats from '../components/LatestMeasurementStats.vue'
import InsightCard from '../components/InsightCard.vue'
import SyncNotices from '../components/SyncNotices.vue'
import { useToastStore } from '../stores/toast'
import { useUnits } from '../composables/useUnits'
import { openInsightGate } from '../demo/insightGate'
import { Flame, Scale, TrendingUp, TrendingDown, CalendarCheck, Dumbbell } from 'lucide-vue-next'

const toast = useToastStore()

const settingsStore = useSettingsStore()
const { toDisplay, label } = useUnits()

const stats = ref(null)
const loading = ref(true)
const targetPerWeek = computed(() => settingsStore.targetWorkoutsPerWeek)
const thisWeekSessions = ref(0)
const insight = ref(null)
const insightLoading = ref(false)
const bodyMetrics = ref([])

// Drives the layout: undefined means the insight request returned nothing at all
// (no measurements yet), which is different from having a card with a message in it.
const insightText = computed(() => insight.value?.text)

const insightCardData = computed(() => ({
  text: insight.value?.text,
  verdict: insight.value?.verdict || null,
  date: insight.value?.generatedAt ? formatInsightDate(insight.value.generatedAt) : null,
  state: insight.value?.state ?? 'OK'
}))

onMounted(async () => {
  try {
    const today = new Date()
    const sunday = new Date(today)
    sunday.setDate(sunday.getDate() - sunday.getDay())
    const nextSunday = new Date(sunday)
    nextSunday.setDate(nextSunday.getDate() + 7)

    const [statsRes, , heatmapRes] = await Promise.all([
      getStats(),
      settingsStore.load(),
      getHeatmap(toLocalDateStr(sunday), toLocalDateStr(nextSunday))
    ])

    stats.value = statsRes.data
    thisWeekSessions.value = heatmapRes.data.reduce((sum, d) => sum + d.workouts.length, 0)
  } catch {
    stats.value = null
  } finally {
    loading.value = false
  }

  try {
    const insightRes = await getInsights()
    insight.value = insightRes.data
  } catch {
    insight.value = null
  }

  try {
    const metricsRes = await getBodyMetrics()
    bodyMetrics.value = Array.isArray(metricsRes.data) ? metricsRes.data : (metricsRes.data.content || [])
  } catch {
    bodyMetrics.value = []
  }
})

const weekSubtitle = computed(() =>
  thisWeekSessions.value >= targetPerWeek.value ? 'Weekly target met!' : ''
)

const isNewUser = computed(() =>
  thisWeekSessions.value === 0 && stats.value?.bodyCompositionStats?.totalMeasurements === 0
)

// These are all-time changes, so they convert with the same factor a value does. The DTO's
// `...ChangeKg` field names stay accurate: they describe the canonical unit, not the display.
function formatChange(value, metricType) {
  if (value == null) return '—'
  const converted = Number(toDisplay(value, metricType))
  return converted > 0 ? `+${converted.toFixed(1)}` : `${converted.toFixed(1)}`
}

function compColor(value, good) {
  if (value == null || value === 0) return ''
  const isUp = value > 0
  const isGood = good == null ? null : (good === 'up' ? isUp : !isUp)
  if (isGood == null) return isUp ? 'change-up' : 'change-down'
  return isGood ? 'positive' : 'negative'
}

async function refreshInsight() {
  if (import.meta.env.MODE === 'demo') {
    openInsightGate()
    return
  }
  insightLoading.value = true
  try {
    const res = await regenerateInsights()
    insight.value = res.data
    toast.success('Insight regenerated')
  } catch {
    toast.error('Failed to regenerate insight')
  }
  insightLoading.value = false
}

function formatInsightDate(dateStr) {
  if (!dateStr) return ''
  const [datePart] = dateStr.split('T')
  return formatDateBr(datePart)
}
</script>

<style scoped>
/* The notices render above the page proper, so they carry no vertical padding of their own. */
.notices-only {
  padding-bottom: 0;
}

.activity-row,
.insights-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-4);
}
.insights-row {
  align-items: start;
}
.side-cols.full {
  grid-column: 1 / -1;
}
.chart-card {
  display: flex;
  flex-direction: column;
}
.activity-row h2 {
  font-size: 1rem;
  margin: 0 0 12px;
}
.chart-card > div {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.chart-card :deep(.chart-box) {
  flex: 1;
  min-height: 150px;
}
.side-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr;
  gap: var(--space-3);
}
.side-cols.with-note {
  grid-template-rows: auto 1fr;
}
.body-comp-col {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.body-comp-col .body-comp-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.body-comp-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.body-comp-note {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 0.7rem;
  color: var(--text-muted);
  text-align: center;
}

.stat-col {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.stat-col :deep(.stat-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 8px 12px;
}
.stat-col :deep(.value) {
  font-size: 1.1rem;
}

section {
  margin-bottom: var(--space-4);
}

@media (max-width: 1100px) {
  .activity-row,
  .insights-row { grid-template-columns: 1fr; }
}

.body-comp-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: var(--space-3) var(--space-4);
  text-align: center;
}

.body-comp-card.positive {
  border-color: rgba(61, 214, 140, 0.25);
  background: linear-gradient(180deg, rgba(61, 214, 140, 0.06) 0%, transparent 60%);
}

.body-comp-card.negative {
  border-color: rgba(251, 146, 60, 0.25);
  background: linear-gradient(180deg, rgba(251, 146, 60, 0.06) 0%, transparent 60%);
}

.body-comp-card.change-up {
  border-color: rgba(79, 141, 255, 0.2);
}

.body-comp-card.change-down {
  border-color: rgba(251, 146, 60, 0.2);
}

.body-comp-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--text-muted);
  font-size: 0.8rem;
  margin-bottom: 4px;
}

.body-comp-value {
  font-family: var(--font-data);
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
  line-height: 1.2;
}

.body-comp-card.positive .body-comp-value { color: var(--green); }
.body-comp-card.negative .body-comp-value { color: var(--orange); }

.body-comp-value .unit {
  font-family: var(--font-body);
  font-size: 0.85rem;
  font-weight: 400;
  color: var(--text-muted);
  margin-left: 2px;
}

.empty-hint {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0;
}

.welcome-card {
  text-align: center;
  padding: var(--space-6) var(--space-4);
}

.welcome-card h2 {
  font-family: var(--font-display);
  font-size: 1.3rem;
  margin: 0 0 8px;
}

.welcome-desc {
  color: var(--text-muted);
  font-size: 0.9rem;
  margin: 0 0 var(--space-4);
}

.welcome-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.welcome-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--blue);
  color: var(--bg);
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 500;
  text-decoration: none;
}

.welcome-btn:hover {
  filter: brightness(1.1);
}
</style>
