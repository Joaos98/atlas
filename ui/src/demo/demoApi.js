// Demo adapter: implements the backend's HTTP API surface against browser
// storage. Swapped in for services/api.js at build time via a Vite alias, so
// the demo is entirely static — no network, nothing to go down.

import seedJson from './demo-seed.json'
import { loadOrInit, persist, resetDemo, mostRecentSunday, todayLocal, nextId } from './seed.js'
import { calculateStreaks, getHeatmapData, getStats, getGoalsProgress, parseInsightText } from './derived.js'

let db = loadOrInit(seedJson)
const anchor = db.anchor

function page(content, total, number, size) {
  const totalPages = size > 0 ? Math.ceil(total / size) : 0
  return {
    content,
    totalElements: total,
    totalPages,
    number,
    size,
    first: number === 0,
    last: number >= totalPages - 1,
    empty: content.length === 0
  }
}

function workoutTypeRef(type) {
  return type ? { id: type.id, name: type.name, colorHex: type.colorHex } : null
}

// A subset of the backend's ExerciseTypeCatalog — enough for the demo's mapping dropdown to
// show activity names instead of raw codes. Kept short on purpose; the demo is a shop window,
// not a mirror of the catalog.
const EXERCISE_TYPES = [
  { code: 0, name: 'Other workout' },
  { code: 8, name: 'Biking' },
  { code: 9, name: 'Biking (stationary)' },
  { code: 25, name: 'Elliptical' },
  { code: 37, name: 'Hiking' },
  { code: 56, name: 'Running' },
  { code: 57, name: 'Running (treadmill)' },
  { code: 70, name: 'Strength training' },
  { code: 74, name: 'Swimming (pool)' },
  { code: 79, name: 'Walking' },
  { code: 83, name: 'Yoga' }
]

const PALETTE = ['#4F8DFF', '#2A9D8F', '#E9C46A', '#E63946', '#8B5CF6', '#457B9D']

/** Resolves workoutTypeId to the type object that derived.js expects on every log. */
function withType(log) {
  return { ...log, workoutType: log.workoutType ?? db.workoutTypes.find((t) => t.id === log.workoutTypeId) }
}

// Mirrors the backend's settings DTO. The key fields are hardcoded to "not configured"
// rather than read from the seed — the demo seed must never carry a key value.
function demoSettings(db) {
  return {
    targetWorkoutsPerWeek: db.settings.targetWorkoutsPerWeek,
    insightBaseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    insightModel: 'gemini-3.5-flash',
    insightApiKeyConfigured: false,
    insightApiKeyLast4: null,
    // Unlike the key, this one is live in the demo: it is a display preference over seeded
    // data, so switching it is safe and is worth showing off.
    unitSystem: db.settings.unitSystem ?? 'METRIC'
  }
}

function logEntity(log) {
  const type = db.workoutTypes.find((t) => t.id === log.workoutTypeId)
  return {
    id: log.id,
    workoutType: workoutTypeRef(type),
    logDate: log.logDate,
    durationMinutes: log.durationMinutes,
    syncSignature: log.syncSignature
  }
}

function metricEntity(m) {
  return {
    id: m.id,
    measuredOn: m.measuredOn,
    weightKg: m.weightKg,
    muscleMassKg: m.muscleMassKg,
    waterLiters: m.waterLiters,
    bodyFatKg: m.bodyFatKg,
    bodyFatPct: m.bodyFatPct,
    insightText: m.insightText,
    insightGeneratedAt: m.insightGeneratedAt
  }
}

function goalEntity(g) {
  return {
    id: g.id,
    metricType: g.metricType,
    targetValue: g.targetValue,
    targetDate: g.targetDate,
    status: g.status,
    createdAt: g.createdAt,
    startValue: g.startValue
  }
}

function metricValue(metric, metricType) {
  switch (metricType) {
    case 'WEIGHT': return metric.weightKg
    case 'MUSCLE_MASS': return metric.muscleMassKg
    case 'WATER': return metric.waterLiters
    case 'BODY_FAT_KG': return metric.bodyFatKg
    case 'BODY_FAT_PCT': return metric.bodyFatPct
    default: return null
  }
}

function ok(data) {
  return Promise.resolve({ data, status: 200 })
}

function fail(status, message) {
  const error = new Error(message)
  error.response = { status, data: { message } }
  return Promise.reject(error)
}

function handle(url, method, body, params) {
  // workout-types
  if (url === '/workout-types') {
    if (method === 'GET') return ok(db.workoutTypes)
    if (method === 'POST') {
      const type = { id: nextId(db.workoutTypes), name: body.name, colorHex: body.colorHex }
      db.workoutTypes.push(type)
      persist(db)
      return ok(type)
    }
  }
  const typeMatch = url.match(/^\/workout-types\/(\d+)$/)
  if (typeMatch && method === 'DELETE') {
    const id = Number(typeMatch[1])
    const type = db.workoutTypes.find((t) => t.id === id)
    if (!type) return fail(404, 'Workout type not found')
    if (db.workoutLogs.some((l) => l.workoutTypeId === id)) {
      return fail(409, 'Cannot delete: this type has existing workout logs')
    }
    db.workoutTypes = db.workoutTypes.filter((t) => t.id !== id)
    persist(db)
    return ok(null)
  }

  if (url === '/workout-types/pending-review' && method === 'GET') {
    // Mirrors PendingReviewTypeDto: the count is what tells you whether a grouping has
    // quietly started fragmenting, so it is part of the contract, not decoration.
    return ok(db.workoutTypes.filter((t) => t.pendingReview).map((t) => ({
      id: t.id,
      name: t.name,
      colorHex: t.colorHex,
      logCount: db.workoutLogs.filter((l) => l.workoutTypeId === t.id).length
    })))
  }

  const mergeMatch = url.match(/^\/workout-types\/(\d+)\/merge-into\/(\d+)$/)
  if (mergeMatch && method === 'POST') {
    const sourceId = Number(mergeMatch[1])
    const targetId = Number(mergeMatch[2])
    if (sourceId === targetId) return fail(400, 'Cannot merge a type into itself')

    const target = db.workoutTypes.find((t) => t.id === targetId)
    if (!target || !db.workoutTypes.some((t) => t.id === sourceId)) return fail(404, 'Workout type not found')

    // Same three steps as the backend, and the same reason to be careful: nothing here
    // enforces referential integrity either.
    db.workoutLogs.forEach((l) => { if (l.workoutTypeId === sourceId) l.workoutTypeId = targetId })
    db.mappings.forEach((m) => { if (m.workoutTypeId === sourceId) m.workoutTypeId = targetId })
    db.workoutTypes = db.workoutTypes.filter((t) => t.id !== sourceId)
    target.pendingReview = false
    persist(db)
    return ok(target)
  }

  const reviewMatch = url.match(/^\/workout-types\/(\d+)\/dismiss-review$/)
  if (reviewMatch && method === 'POST') {
    const type = db.workoutTypes.find((t) => t.id === Number(reviewMatch[1]))
    if (type) { type.pendingReview = false; persist(db) }
    return ok(null)
  }

  if (typeMatch && method === 'PATCH') {
    const type = db.workoutTypes.find((t) => t.id === Number(typeMatch[1]))
    if (!type) return fail(404, 'Workout type not found')
    type.name = body.name
    type.pendingReview = false
    persist(db)
    return ok(type)
  }

  // workout-logs
  if (url === '/workout-logs') {
    if (method === 'GET') {
      const size = params?.size || 20
      const number = params?.page || 0
      const sorted = [...db.workoutLogs].sort((a, b) => (a.logDate < b.logDate ? 1 : a.logDate > b.logDate ? -1 : 0))
      const content = sorted.slice(number * size, number * size + size).map(logEntity)
      return ok(page(content, db.workoutLogs.length, number, size))
    }
    if (method === 'POST') {
      const typeId = body.workoutType?.id
      const type = db.workoutTypes.find((t) => t.id === typeId)
      if (!type) return fail(400, 'Workout type not found')
      const log = {
        id: nextId(db.workoutLogs),
        workoutTypeId: typeId,
        logDate: body.logDate,
        durationMinutes: body.durationMinutes,
        syncSignature: body.syncSignature ?? null
      }
      db.workoutLogs.push(log)
      persist(db)
      return ok(logEntity(log))
    }
  }
  const logMatch = url.match(/^\/workout-logs\/(\d+)$/)
  if (logMatch) {
    const id = Number(logMatch[1])
    const log = db.workoutLogs.find((l) => l.id === id)
    if (!log) return fail(404, 'Workout log not found')
    if (method === 'DELETE') {
      db.workoutLogs = db.workoutLogs.filter((l) => l.id !== id)
      persist(db)
      return ok(null)
    }
    if (method === 'PUT') {
      const typeId = body.workoutType?.id
      if (typeId && !db.workoutTypes.some((t) => t.id === typeId)) return fail(400, 'Workout type not found')
      log.logDate = body.logDate
      log.durationMinutes = body.durationMinutes
      if (typeId) log.workoutTypeId = typeId
      persist(db)
      return ok(logEntity(log))
    }
  }
  if (url === '/workout-logs/heatmap' && method === 'GET') {
    return ok(getHeatmapData(db.workoutLogs, params.startDate, params.endDate))
  }
  if (url === '/workout-logs/streaks' && method === 'GET') {
    const dates = [...new Set(db.workoutLogs.map((l) => l.logDate))]
    return ok(calculateStreaks(dates, db.settings.targetWorkoutsPerWeek, todayLocal()))
  }
  if (url === '/stats' && method === 'GET') {
    const now = new Date()
    const year = params?.year ?? now.getFullYear()
    const month = params?.month ?? now.getMonth() + 1
    // Hydrated here rather than trusted from storage. Seeded logs carry a denormalized
    // workoutType object, but nothing that *adds* a log had been setting it — so a workout
    // logged in the demo, or backfilled from quarantine, made getStats throw on
    // `l.workoutType.name` and the dashboard showed "Could not load stats".
    return ok(getStats(db.workoutLogs.map(withType), db.bodyMetrics, year, month, todayLocal(), db.settings.targetWorkoutsPerWeek))
  }

  // body-metrics
  if (url === '/body-metrics') {
    if (method === 'GET') {
      const sorted = [...db.bodyMetrics].sort((a, b) => (a.measuredOn < b.measuredOn ? -1 : 1))
      return ok(page(sorted.map(metricEntity), sorted.length, 0, sorted.length || 1))
    }
    if (method === 'POST') {
      const metric = {
        id: nextId(db.bodyMetrics),
        measuredOn: body.measuredOn,
        weightKg: body.weightKg,
        muscleMassKg: body.muscleMassKg,
        waterLiters: body.waterLiters,
        bodyFatKg: body.bodyFatKg,
        bodyFatPct: body.bodyFatPct,
        insightText: null,
        insightGeneratedAt: null
      }
      db.bodyMetrics.push(metric)
      persist(db)
      return ok(metricEntity(metric))
    }
  }
  const metricMatch = url.match(/^\/body-metrics\/(\d+)$/)
  if (metricMatch) {
    const id = Number(metricMatch[1])
    const metric = db.bodyMetrics.find((m) => m.id === id)
    if (!metric) return fail(404, 'Measurement not found')
    if (method === 'DELETE') {
      db.bodyMetrics = db.bodyMetrics.filter((m) => m.id !== id)
      persist(db)
      return ok(null)
    }
    if (method === 'PUT') {
      metric.measuredOn = body.measuredOn
      metric.weightKg = body.weightKg
      metric.muscleMassKg = body.muscleMassKg
      metric.waterLiters = body.waterLiters
      metric.bodyFatKg = body.bodyFatKg
      metric.bodyFatPct = body.bodyFatPct
      persist(db)
      return ok(metricEntity(metric))
    }
  }

  // goals
  if (url === '/goals') {
    if (method === 'GET') return ok(getGoalsProgress(db.goals, db.bodyMetrics, todayLocal()))
    if (method === 'POST') {
      const latest = [...db.bodyMetrics].sort((a, b) => (a.measuredOn < b.measuredOn ? -1 : 1)).at(-1)
      const goal = {
        id: nextId(db.goals),
        metricType: body.metricType,
        targetValue: body.targetValue,
        targetDate: body.targetDate ?? null,
        status: body.status ?? 'ACTIVE',
        createdAt: todayLocal() + 'T00:00:00',
        startValue: latest ? metricValue(latest, body.metricType) : null
      }
      db.goals.push(goal)
      persist(db)
      return ok(goalEntity(goal))
    }
  }
  const goalStatusMatch = url.match(/^\/goals\/(\d+)\/status$/)
  if (goalStatusMatch && method === 'PATCH') {
    const id = Number(goalStatusMatch[1])
    const goal = db.goals.find((g) => g.id === id)
    if (!goal) return fail(404, 'Goal not found')
    goal.status = params.status
    persist(db)
    return ok(goalEntity(goal))
  }
  const goalMatch = url.match(/^\/goals\/(\d+)$/)
  if (goalMatch && method === 'DELETE') {
    const id = Number(goalMatch[1])
    db.goals = db.goals.filter((g) => g.id !== id)
    persist(db)
    return ok(null)
  }

  // insights
  if (url === '/insights' && method === 'GET') {
    const latest = [...db.bodyMetrics].sort((a, b) => (a.measuredOn < b.measuredOn ? -1 : 1)).at(-1)
    if (!latest) return ok(null)
    const parsed = parseInsightText(latest.insightText)
    return ok({
      verdict: parsed.verdict,
      text: parsed.text,
      generatedAt: latest.insightGeneratedAt,
      state: 'OK',
      fallback: false
    })
  }
  if (url === '/insights/regenerate' && method === 'POST') {
    return fail(403, 'Insight generation requires self-hosting with your own provider API key.')
  }

  // settings
  // The demo never holds an API key, so it reports the provider defaults and a key that
  // is permanently unconfigured; SettingsView renders those fields read-only here.
  if (url === '/settings') {
    if (method === 'GET') return ok(demoSettings(db))
    if (method === 'PUT') {
      if (body.targetWorkoutsPerWeek !== undefined) {
        db.settings.targetWorkoutsPerWeek = body.targetWorkoutsPerWeek
      }
      if (body.unitSystem !== undefined) {
        db.settings.unitSystem = body.unitSystem
      }
      persist(db)
      return ok(demoSettings(db))
    }
  }

  // sync mappings (stored locally; no phone in the demo)
  if (url === '/sync/mappings') {
    if (method === 'GET') {
      return ok(db.mappings.map((m) => ({
        healthConnectType: m.healthConnectType,
        workoutType: workoutTypeRef(db.workoutTypes.find((t) => t.id === m.workoutTypeId))
      })))
    }
    if (method === 'POST') {
      const typeId = body.workoutTypeId
      // null is the "ignore this activity" mapping, not a missing value.
      if (typeId != null && !db.workoutTypes.some((t) => t.id === typeId)) return fail(400, 'Workout type not found')
      db.mappings = db.mappings.filter((m) => m.healthConnectType !== body.healthConnectType)
      db.mappings.push({ healthConnectType: body.healthConnectType, workoutTypeId: typeId ?? null })
      persist(db)
      return ok({ healthConnectType: body.healthConnectType, workoutType: workoutTypeRef(db.workoutTypes.find((t) => t.id === typeId)) })
    }
  }

  if (url === '/sync/exercise-types' && method === 'GET') {
    return ok(EXERCISE_TYPES)
  }

  // Sync sources. The demo ships one enabled source and one holding entries, so a visitor can
  // click Enable and watch the backfill land — the whole point of quarantining rather than
  // dropping is only visible if there is something to recover.
  if (url === '/sync/sources' && method === 'GET') {
    return ok(db.syncSources.map((s) => ({
      ...s,
      quarantinedCount: db.quarantined.filter(
        (q) => q.dataOrigin === s.dataOrigin && q.recordingMethod === s.recordingMethod).length
    })))
  }

  const sourceMatch = url.match(/^\/sync\/sources\/([^/]+)\/([^/]+)$/)
  if (sourceMatch && method === 'PUT') {
    const origin = decodeURIComponent(sourceMatch[1])
    const recording = decodeURIComponent(sourceMatch[2])
    const source = db.syncSources.find((s) => s.dataOrigin === origin && s.recordingMethod === recording)
    if (!source) return fail(404, 'Unknown sync source')

    source.allowed = !!body.allowed
    let created = 0
    if (source.allowed) {
      const held = db.quarantined.filter((q) => q.dataOrigin === origin && q.recordingMethod === recording)
      for (const entry of held) {
        const mapping = db.mappings.find((m) => m.healthConnectType === Number(entry.type))
        let typeId = mapping ? mapping.workoutTypeId : null
        if (!mapping) {
          // Mirrors auto-create: an unmapped code makes its own type from the catalog.
          const name = EXERCISE_TYPES.find((t) => t.code === Number(entry.type))?.name ?? `Activity ${entry.type}`
          const existing = db.workoutTypes.find((t) => t.name === name)
          const type = existing ?? {
            id: Math.max(0, ...db.workoutTypes.map((t) => t.id)) + 1,
            name,
            colorHex: PALETTE[db.workoutTypes.length % PALETTE.length],
            pendingReview: true
          }
          if (!existing) db.workoutTypes.push(type)
          db.mappings.push({ healthConnectType: Number(entry.type), workoutTypeId: type.id })
          typeId = type.id
        }
        if (typeId != null) {
          db.workoutLogs.push({
            id: Math.max(0, ...db.workoutLogs.map((l) => l.id)) + 1,
            workoutTypeId: typeId,
            logDate: entry.startTime.slice(0, 10),
            durationMinutes: Math.ceil(entry.durationSeconds / 60)
          })
          created++
        }
      }
      db.quarantined = db.quarantined.filter(
        (q) => !(q.dataOrigin === origin && q.recordingMethod === recording))
    }
    persist(db)
    return ok({ created, alreadyPresent: 0, unusable: 0 })
  }

  const quarantineMatch = url.match(/^\/sync\/sources\/([^/]+)\/([^/]+)\/quarantine$/)
  if (quarantineMatch && method === 'GET') {
    const origin = decodeURIComponent(quarantineMatch[1])
    const recording = decodeURIComponent(quarantineMatch[2])
    // Mirrors HeldEntryDto: the code is resolved to a name, and duration rounded to minutes.
    return ok(db.quarantined
      .filter((q) => q.dataOrigin === origin && q.recordingMethod === recording)
      .sort((a, b) => (a.startTime < b.startTime ? -1 : 1))
      .map((q) => ({
        id: q.id,
        activity: EXERCISE_TYPES.find((t) => t.code === Number(q.type))?.name ?? 'Unrecognised activity',
        type: q.type,
        startTime: q.startTime,
        durationMinutes: Math.ceil(q.durationSeconds / 60),
        receivedAt: q.receivedAt ?? null
      })))
  }
  if (quarantineMatch && method === 'DELETE') {
    const origin = decodeURIComponent(quarantineMatch[1])
    const recording = decodeURIComponent(quarantineMatch[2])
    db.quarantined = db.quarantined.filter(
      (q) => !(q.dataOrigin === origin && q.recordingMethod === recording))
    persist(db)
    return ok(null)
  }
  const mappingMatch = url.match(/^\/sync\/mappings\/(\d+)$/)
  if (mappingMatch && method === 'DELETE') {
    const hcType = Number(mappingMatch[1])
    db.mappings = db.mappings.filter((m) => m.healthConnectType !== hcType)
    persist(db)
    return ok(null)
  }

  return fail(404, `Not found: ${method} ${url}`)
}

const api = {
  get: (url, config) => handle(url, 'GET', null, config?.params),
  post: (url, body) => handle(url, 'POST', body, null),
  put: (url, body) => handle(url, 'PUT', body, null),
  patch: (url, body, config) => handle(url, 'PATCH', body, config?.params),
  delete: (url) => handle(url, 'DELETE', null, null)
}

export default api

export function isDemoBuild() {
  return import.meta.env.MODE === 'demo'
}

export function resetDemoData() {
  resetDemo()
  if (typeof window !== 'undefined') {
    window.location.reload()
  }
}
