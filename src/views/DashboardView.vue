<template>
  <div v-if="loading" class="loading"></div>
  <div v-else-if="stats" class="page">
    <section>
      <h2>Workout activity</h2>
      <div class="activity-row">
        <div class="card heatmap-col">
          <WorkoutHeatmap :refresh="0" />
        </div>
        <div class="stats-col">
          <StatCard
            label="Workouts this week"
            :value="`${thisWeekSessions} / ${targetPerWeek}`"
            color="purple"
            :icon="CalendarCheck"
            :subtitle="weekSubtitle"
          />
          <StatCard label="Current workout streak" :value="`${stats.streakStats.currentStreak} weeks`" color="purple" :icon="Flame" />
          <StatCard label="Longest streak ever" :value="`${stats.streakStats.longestStreak} weeks`" color="purple" :icon="Flame" />
          <StatCard v-if="yearStats" label="Workouts this year" :value="yearStats.totalWorkoutsThisYear" color="blue" :icon="Dumbbell" />
          <StatCard
            v-if="yearStats?.averageCaloriesBurnedThisYear"
            label="Avg kcal/session this year"
            :value="`${Math.round(yearStats.averageCaloriesBurnedThisYear)} Kcal`"
            color="blue"
            :icon="Zap"
          />
        </div>
      </div>
    </section>

    <section v-if="insightText !== undefined">
      <h2><Sparkles :size="18" class="section-icon" /> Insights</h2>
      <div class="card insight-card" :class="{ loading: insightLoading }">
        <p class="insight-text" :class="{ dimmed: insightLoading }">{{ insightText || 'No insight yet — add a new measurement to generate one.' }}</p>
        <div class="insight-footer">
          <span v-if="insightDate && !insightLoading" class="insight-date">Updated after your {{ insightDate }} measurement</span>
          <span v-if="insightFallback" class="insight-fallback">(auto-generated)</span>
          <span v-if="insightLoading" class="insight-loading">Regenerating...</span>
          <button class="btn-icon" title="Regenerate" @click="refreshInsight" :disabled="insightLoading">
            <RefreshCw :size="14" />
          </button>
        </div>
      </div>
    </section>

    <section>
      <h2>This month</h2>
      <div class="stat-grid">
        <StatCard label="Workouts" :value="stats.workoutStats.totalWorkoutsThisMonth" color="blue" :icon="Dumbbell" />
        <StatCard label="Longest session" :value="formatDuration(stats.workoutStats.longestSessionThisMonth)" color="blue" :icon="Clock" />
        <StatCard label="Avg duration" :value="formatDuration(stats.workoutStats.averageDurationThisMonth)" color="blue" :icon="Clock" />
        <StatCard label="Most frequent type" :value="stats.workoutStats.mostFrequentTypeThisMonth || '—'" color="blue" :icon="Tag" />
        <StatCard
          v-if="stats.workoutStats.averageCaloriesBurnedThisMonth"
          label="Avg calories/session"
          :value="`${Math.round(stats.workoutStats.averageCaloriesBurnedThisMonth)} Kcal`"
          color="blue"
          :icon="Zap"
        />
      </div>
    </section>

    <section>
      <h2>Body composition since first measurement</h2>
      <div class="stat-grid">
        <StatCard label="Weight" :value="formatChange(stats.bodyCompositionStats.weightChangeKg, 'kg')" color="blue" :icon="Scale" />
        <StatCard label="Muscle mass" :value="formatChange(stats.bodyCompositionStats.muscleMassChangeKg, 'kg')" :color="directionColor(stats.bodyCompositionStats.muscleMassChangeKg, false)" :icon="TrendingUp" />
        <StatCard label="Body fat %" :value="formatChange(stats.bodyCompositionStats.bodyFatPctChange, '%')" :color="directionColor(stats.bodyCompositionStats.bodyFatPctChange, true)" :icon="TrendingDown" />
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
import { toLocalDateStr, formatDateBr } from '../utils/date'
import { getInsights, regenerateInsights } from '../services/insightService'
import StatCard from '../components/StatCard.vue'
import WorkoutHeatmap from '../components/WorkoutHeatmap.vue'
import { Flame, Dumbbell, Clock, Tag, Zap, Scale, TrendingUp, TrendingDown, CalendarCheck, RefreshCw, Sparkles } from 'lucide-vue-next'

const stats = ref(null)
const loading = ref(true)
const targetPerWeek = ref(4)
const thisWeekSessions = ref(0)
const insight = ref(null)
const insightLoading = ref(false)

const insightText = computed(() => insight.value?.text)
const insightDate = computed(() => insight.value?.generatedAt ? formatInsightDate(insight.value.generatedAt) : null)
const insightFallback = computed(() => insight.value?.fallback ?? false)

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
})

const yearStats = computed(() => stats.value?.workoutStats?.totalWorkoutsThisYear != null ? stats.value.workoutStats : null)

const weekSubtitle = computed(() => {
  if (thisWeekSessions.value >= targetPerWeek.value) return 'Weekly target met!'
  const remaining = targetPerWeek.value - thisWeekSessions.value
  return `${remaining} more to meet target`
})

function formatDuration(minutes) {
if (minutes == null) return '—';
if (minutes < 60) {
  return `${Math.floor(minutes)} min`;
}
let h = Math.floor(minutes / 60);
let m = Math.floor((minutes % 60));
return m > 0 ? `${h}h ${m}min` : `${h}h`;
}
function formatChange(value, unit) {
  if (value == null) return '—'
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)} ${unit}`
}
function directionColor(value, lowerIsBetter) {
  if (value == null) return 'blue'
  const isGood = lowerIsBetter ? value < 0 : value > 0
  return isGood ? 'green' : 'orange'
}

async function refreshInsight() {
  insightLoading.value = true
  try {
    const res = await regenerateInsights()
    insight.value = res.data
  } catch {
    // keep current insight on failure
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
.activity-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-5);
  align-items: start;
}
.stats-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
  align-content: start;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-4);
}

@media (max-width: 1100px) {
  .activity-row { grid-template-columns: 1fr; }
  .stats-col { grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); }
}

.insight-card {
  border-left: 3px solid var(--purple);
}
.insight-text {
  font-size: 0.92rem;
  line-height: 1.7;
  margin: 0;
  white-space: pre-line;
}
.insight-footer {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
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
.section-icon { vertical-align: -3px; margin-right: 4px; color: var(--purple); }
</style>
