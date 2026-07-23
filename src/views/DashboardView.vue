<template>
  <div v-if="loading" class="loading"></div>
  <div v-else-if="stats" class="page">
    <section>
      <div class="insights-row">
        <div v-if="insightText !== undefined" class="card insight-card" :class="{ loading: insightLoading, unavailable: insightUnavailable }">
          <p v-if="insightVerdict" class="insight-verdict"><Sparkles :size="16" class="verdict-icon" /> {{ insightVerdict }}</p>
          <p class="insight-text" :class="{ dimmed: insightLoading }">{{ insightText || 'No insight yet — add a new measurement to generate one.' }}</p>
          <div class="insight-footer">
            <span v-if="insightDate && !insightLoading" class="insight-date">Updated after your {{ insightDate }} measurement</span>
            <span v-if="insightFallback && !insightUnavailable" class="insight-fallback">(auto-generated)</span>
            <span v-if="insightLoading" class="insight-loading">Regenerating...</span>
            <button class="btn-icon" title="Regenerate" @click="refreshInsight" :disabled="insightLoading">
              <RefreshCw :size="14" />
            </button>
          </div>
        </div>
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
                <p class="body-comp-value">{{ formatChange(stats.bodyCompositionStats.weightChangeKg) }}<span class="unit"> kg</span></p>
              </div>
              <div class="body-comp-card" :class="compColor(stats.bodyCompositionStats.muscleMassChangeKg, 'up')">
                <div class="body-comp-header">
                  <TrendingUp :size="16" />
                  <span>Muscle mass</span>
                </div>
                <p class="body-comp-value">{{ formatChange(stats.bodyCompositionStats.muscleMassChangeKg) }}<span class="unit"> kg</span></p>
              </div>
              <div class="body-comp-card" :class="compColor(stats.bodyCompositionStats.bodyFatPctChange, 'down')">
                <div class="body-comp-header">
                  <TrendingDown :size="16" />
                  <span>Body fat</span>
                </div>
                <p class="body-comp-value">{{ formatChange(stats.bodyCompositionStats.bodyFatPctChange) }}<span class="unit"> %</span></p>
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
          <h2>Workout history</h2>
          <WorkoutHeatmap :refresh="0" />
        </div>
        <div class="card chart-card">
          <h2>Workouts per week</h2>
          <WeeklySessionsChart :refresh="0" />
        </div>
      </div>
    </section>
  </div>
  <div v-else>Could not load stats.</div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getStats } from '../services/statsService'
import { getSettings } from '../services/settingsService'
import { getHeatmap } from '../services/workoutService'
import { getBodyMetrics } from '../services/bodyMetricsService'
import { toLocalDateStr, formatDateBr } from '../utils/date'
import { getInsights, regenerateInsights } from '../services/insightService'
import StatCard from '../components/StatCard.vue'
import WorkoutHeatmap from '../components/WorkoutHeatmap.vue'
import WeeklySessionsChart from '../components/WeeklySessionsChart.vue'
import LatestMeasurementStats from '../components/LatestMeasurementStats.vue'
import { Flame, Scale, TrendingUp, TrendingDown, CalendarCheck, RefreshCw, Sparkles } from 'lucide-vue-next'

const stats = ref(null)
const loading = ref(true)
const targetPerWeek = ref(4)
const thisWeekSessions = ref(0)
const insight = ref(null)
const insightLoading = ref(false)
const bodyMetrics = ref([])

const insightText = computed(() => insight.value?.text)
const insightVerdict = computed(() => insight.value?.verdict || null)
const insightDate = computed(() => insight.value?.generatedAt ? formatInsightDate(insight.value.generatedAt) : null)
const insightFallback = computed(() => insight.value?.fallback ?? false)
const insightUnavailable = computed(() => insightText.value?.includes('could not be generated') ?? false)

onMounted(async () => {
  try {
    const today = new Date()
    const sunday = new Date(today)
    sunday.setDate(sunday.getDate() - sunday.getDay())
    const nextSunday = new Date(sunday)
    nextSunday.setDate(nextSunday.getDate() + 7)

    const [statsRes, settingsRes, heatmapRes] = await Promise.all([
      getStats(),
      getSettings(),
      getHeatmap(toLocalDateStr(sunday), toLocalDateStr(nextSunday))
    ])

    stats.value = statsRes.data
    targetPerWeek.value = settingsRes.data.targetWorkoutsPerWeek ?? 4
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

function formatChange(value) {
  if (value == null) return '—'
  return value > 0 ? `+${value.toFixed(1)}` : `${value.toFixed(1)}`
}

function getChangeDirection(value) {
  if (value == null || value === 0) return null
  return value > 0 ? 'up' : 'down'
}

function compColor(value, good) {
  if (value == null || value === 0) return ''
  const isUp = value > 0
  const isGood = good == null ? null : (good === 'up' ? isUp : !isUp)
  if (isGood == null) return isUp ? 'change-up' : 'change-down'
  return isGood ? 'positive' : 'negative'
}

async function refreshInsight() {
  insightLoading.value = true
  try {
    const res = await regenerateInsights()
    insight.value = res.data
  } catch {
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

.insight-card {
  border-left: 3px solid var(--purple);
}
.insight-card.unavailable {
  border-left-color: var(--orange);
}
.insight-verdict {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 1.05rem;
  color: var(--text);
  background: rgba(139, 92, 246, 0.12);
  border: 1px solid rgba(139, 92, 246, 0.25);
  border-radius: 6px;
  padding: 4px 10px;
  margin: 0 0 10px;
}
.verdict-icon {
  color: var(--purple);
  flex-shrink: 0;
}
.insight-card.unavailable .insight-verdict {
  background: rgba(251, 146, 60, 0.12);
  border-color: rgba(251, 146, 60, 0.25);
}
.insight-card.unavailable .verdict-icon {
  color: var(--orange);
}
.insight-text {
  font-size: 0.88rem;
  line-height: 1.6;
  margin: 0;
  white-space: pre-line;
}
.insight-footer {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--border);
}
.insight-date {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.insight-fallback {
  font-size: 0.7rem;
  color: var(--orange);
}
.insight-text.dimmed { opacity: 0.4; }
.insight-loading {
  font-size: 0.8rem;
  color: var(--text-muted);
  animation: pulse 1.5s ease-in-out infinite;
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
  margin-left: auto;
}
.btn-icon:hover { color: var(--text); background: var(--bg); }
.btn-icon:disabled { opacity: 0.4; cursor: default; }

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
</style>
