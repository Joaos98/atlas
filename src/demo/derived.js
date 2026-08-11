// JS port of the backend's derived logic (streaks, stats, heatmap, goals).
// Mirrors WorkoutLogService/StatsService/GoalService; the fixture test asserts
// this matches expected-derived.json recorded from the Java API.

// --- date helpers (pure string math, no timezone drift) ---

function pad(n) {
  return String(n).padStart(2, '0')
}

export function addDays(dateStr, days) {
  const [y, m, d] = dateStr.split('-').map(Number)
  const ms = Date.UTC(y, m - 1, d) + days * 86400000
  const dt = new Date(ms)
  return `${dt.getUTCFullYear()}-${pad(dt.getUTCMonth() + 1)}-${pad(dt.getUTCDate())}`
}

export function daysBetween(a, b) {
  const [ay, am, ad] = a.split('-').map(Number)
  const [by, bm, bd] = b.split('-').map(Number)
  return Math.round((Date.UTC(by, bm - 1, bd) - Date.UTC(ay, am - 1, ad)) / 86400000)
}

export function previousOrSameSunday(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number)
  const day = new Date(Date.UTC(y, m - 1, d)).getUTCDay()
  return addDays(dateStr, -day)
}

function lastDayOfMonth(year, month) {
  return new Date(Date.UTC(year, month, 0)).getUTCDate()
}

// --- streaks (WorkoutLogService.calculateStreaks) ---

// dates: distinct workout dates (already deduplicated), 'YYYY-MM-DD'
export function calculateStreaks(distinctDates, target, today) {
  const dates = distinctDates.filter((d) => d <= today)
  if (dates.length === 0) return { currentStreak: 0, longestStreak: 0 }

  const countByWeek = new Map()
  for (const d of dates) {
    const sunday = previousOrSameSunday(d)
    countByWeek.set(sunday, (countByWeek.get(sunday) || 0) + 1)
  }

  const qualifyingWeeks = [...countByWeek.entries()]
    .filter(([, count]) => count >= target)
    .map(([sunday]) => sunday)
    .sort()

  if (qualifyingWeeks.length === 0) return { currentStreak: 0, longestStreak: 0 }

  let longest = 1
  let current = 1
  for (let i = 1; i < qualifyingWeeks.length; i++) {
    if (addDays(qualifyingWeeks[i - 1], 7) === qualifyingWeeks[i]) {
      current++
      longest = Math.max(longest, current)
    } else {
      current = 1
    }
  }

  const thisWeekSunday = previousOrSameSunday(today)
  const lastWeekSunday = addDays(thisWeekSunday, -7)
  const mostRecentQualifying = qualifyingWeeks[qualifyingWeeks.length - 1]

  let currentStreak = 0
  if (mostRecentQualifying === thisWeekSunday || mostRecentQualifying === lastWeekSunday) {
    currentStreak = 1
    for (let i = qualifyingWeeks.length - 2; i >= 0; i--) {
      if (addDays(qualifyingWeeks[i], 7) === qualifyingWeeks[i + 1]) {
        currentStreak++
      } else {
        break
      }
    }
  }

  return { currentStreak, longestStreak: longest }
}

// --- heatmap (WorkoutLogService.getHeatmapData) ---

// logs: [{ logDate, durationMinutes, workoutType: { name, colorHex } }]
export function getHeatmapData(logs, startDate, endDate) {
  const inRange = logs
    .filter((l) => l.logDate >= startDate && l.logDate <= endDate)
    .sort((a, b) => (a.logDate < b.logDate ? -1 : a.logDate > b.logDate ? 1 : 0))

  const byDate = new Map()
  for (const log of inRange) {
    if (!byDate.has(log.logDate)) byDate.set(log.logDate, [])
    byDate.get(log.logDate).push({
      type: log.workoutType.name,
      colorHex: log.workoutType.colorHex,
      durationMinutes: log.durationMinutes
    })
  }

  return [...byDate.entries()]
    .sort((a, b) => (a[0] < b[0] ? -1 : 1))
    .map(([date, workouts]) => ({ date, workouts }))
}

// --- stats (StatsService.getStats) ---

// logs: [{ logDate, durationMinutes, workoutType: { name } }]
// metrics: [{ measuredOn, weightKg, muscleMassKg, bodyFatPct }]
export function getStats(logs, metrics, year, month, today, target) {
  const monthStart = `${year}-${pad(month)}-01`
  const monthEnd = `${year}-${pad(month)}-${pad(lastDayOfMonth(year, month))}`
  const yearStart = `${year}-01-01`
  const yearEnd = `${year}-12-31`

  const inRange = (d, s, e) => d >= s && d <= e
  const monthLogs = logs.filter((l) => inRange(l.logDate, monthStart, monthEnd))
  const yearLogs = logs.filter((l) => inRange(l.logDate, yearStart, yearEnd))

  const count = (arr) => arr.length
  const maxDuration = (arr) => (arr.length ? Math.max(...arr.map((l) => l.durationMinutes)) : null)
  const averageDuration = (arr) =>
    arr.length ? arr.reduce((s, l) => s + l.durationMinutes, 0) / arr.length : null
  const mostFrequentType = (arr) => {
    if (!arr.length) return null
    const counts = new Map()
    for (const l of arr) counts.set(l.workoutType.name, (counts.get(l.workoutType.name) || 0) + 1)
    let best = null
    let bestCount = -1
    for (const [name, c] of counts) {
      if (c > bestCount) {
        bestCount = c
        best = name
      }
    }
    return best
  }
  const sumDuration = (arr) => (arr.length ? arr.reduce((s, l) => s + l.durationMinutes, 0) : 0)

  const workoutStats = {
    totalWorkoutsThisMonth: count(monthLogs),
    totalWorkoutsThisYear: count(yearLogs),
    longestSessionEver: maxDuration(yearLogs),
    longestSessionThisMonth: maxDuration(monthLogs),
    averageDurationThisMonth: averageDuration(monthLogs),
    averageDurationThisYear: averageDuration(yearLogs),
    mostFrequentTypeThisMonth: mostFrequentType(monthLogs),
    mostFrequentTypeThisYear: mostFrequentType(yearLogs),
    totalMinutesThisMonth: sumDuration(monthLogs),
    totalMinutesThisYear: sumDuration(yearLogs)
  }

  const sorted = [...metrics].sort((a, b) => (a.measuredOn < b.measuredOn ? -1 : 1))
  let bodyCompositionStats
  if (sorted.length < 2) {
    bodyCompositionStats = { weightChangeKg: null, muscleMassChangeKg: null, bodyFatPctChange: null, totalMeasurements: sorted.length }
  } else {
    const first = sorted[0]
    const latest = sorted[sorted.length - 1]
    bodyCompositionStats = {
      weightChangeKg: latest.weightKg - first.weightKg,
      muscleMassChangeKg: latest.muscleMassKg - first.muscleMassKg,
      bodyFatPctChange: latest.bodyFatPct - first.bodyFatPct,
      totalMeasurements: sorted.length
    }
  }

  const distinctDates = [...new Set(logs.map((l) => l.logDate))]
  const streakStats = calculateStreaks(distinctDates, target, today)

  return { workoutStats, bodyCompositionStats, streakStats }
}

// --- goals (GoalService.findAllWithProgress) ---

// goals: [{ id, metricType, targetValue, targetDate, status, createdAt, startValue }]
// metrics: [{ measuredOn, weightKg, muscleMassKg, waterLiters, bodyFatKg, bodyFatPct }]
export function getGoalsProgress(goals, metrics, today) {
  const sorted = [...metrics].sort((a, b) => (a.measuredOn < b.measuredOn ? -1 : 1))
  const latest = sorted.length ? sorted[sorted.length - 1] : null

  const latestValues = {}
  if (latest) {
    latestValues.WEIGHT = latest.weightKg
    latestValues.MUSCLE_MASS = latest.muscleMassKg
    latestValues.WATER = latest.waterLiters
    latestValues.BODY_FAT_KG = latest.bodyFatKg
    latestValues.BODY_FAT_PCT = latest.bodyFatPct
  }

  return goals.map((g) => {
    const current = g.metricType in latestValues ? latestValues[g.metricType] : null

    let progressPercent = null
    let eta = null
    let paceStatus = null

    if (g.status === 'ACTIVE' && g.startValue != null && current != null) {
      const start = g.startValue
      const target = g.targetValue
      const isDown = isDownGoal(g.metricType, start, target)

      if (Math.abs(target - start) > 0.001) {
        const pct = ((current - start) / (target - start)) * 100
        progressPercent = Math.max(0, Math.min(100, pct))
      }

      const reached =
        Math.abs(current - target) < 0.05 || (isDown ? current <= target : current >= target)

      if (!reached) {
        const createdAtDate = g.createdAt.slice(0, 10)
        if (g.targetDate == null) {
          eta = computeEta(createdAtDate, start, current, target, today)
        } else {
          paceStatus = computePace(g.targetDate, createdAtDate, start, current, target, today)
        }
      }
    }

    return {
      id: g.id,
      metricType: g.metricType,
      targetValue: g.targetValue,
      targetDate: g.targetDate,
      status: g.status,
      createdAt: g.createdAt,
      startValue: g.startValue,
      currentValue: current,
      progressPercent,
      eta,
      paceStatus
    }
  })
}

function isDownGoal(metricType, start, target) {
  switch (metricType) {
    case 'BODY_FAT_KG':
    case 'BODY_FAT_PCT':
      return true
    case 'MUSCLE_MASS':
      return false
    case 'WEIGHT':
    case 'WATER':
      return target < start
    default:
      return false
  }
}

function computeEta(createdAtDate, start, current, target, today) {
  if (createdAtDate == null) return null
  const daysElapsed = daysBetween(createdAtDate, today)
  if (daysElapsed < 1) return null

  const ratePerDay = (current - start) / daysElapsed
  const remaining = target - current

  if (Math.abs(ratePerDay) < 0.0001) return null
  if (Math.sign(ratePerDay) !== Math.sign(remaining)) return null

  const daysRemaining = Math.round(remaining / ratePerDay)
  if (daysRemaining <= 0 || daysRemaining > 365 * 5) return null

  return addDays(today, daysRemaining)
}

function computePace(targetDate, createdAtDate, start, current, target, today) {
  if (createdAtDate == null || targetDate == null) return null
  const daysElapsed = daysBetween(createdAtDate, today)
  const daysRemaining = daysBetween(today, targetDate)

  if (daysElapsed < 1 || daysRemaining < 1) return null
  if (Math.abs(target - current) < 0.05) return null

  const requiredRatePerDay = (target - current) / daysRemaining
  const actualRatePerDay = (current - start) / daysElapsed
  const neededDirection = Math.sign(target - start)

  if (Math.abs(actualRatePerDay) < 0.0001) return 'behind'
  const onTrack =
    Math.abs(actualRatePerDay) >= Math.abs(requiredRatePerDay) &&
    Math.sign(actualRatePerDay) === neededDirection

  return onTrack ? 'on_track' : 'behind'
}

// --- insight text parsing (InsightService.parseRawText) ---

export function parseInsightText(raw) {
  if (raw == null) return { verdict: null, text: null }
  if (raw.startsWith('VERDICT:')) {
    const verdictEnd = raw.indexOf('INSIGHT:')
    if (verdictEnd > 0) {
      const verdict = raw.slice('VERDICT:'.length, verdictEnd).trim()
      const text = raw.slice(verdictEnd + 'INSIGHT:'.length).trim()
      return { verdict, text }
    }
  }
  return { verdict: null, text: raw }
}
