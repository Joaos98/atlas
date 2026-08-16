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

// Mirrors the backend's settings DTO. The key fields are hardcoded to "not configured"
// rather than read from the seed — the demo seed must never carry a key value.
function demoSettings(db) {
  return {
    targetWorkoutsPerWeek: db.settings.targetWorkoutsPerWeek,
    insightBaseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    insightModel: 'gemini-3.5-flash',
    insightApiKeyConfigured: false,
    insightApiKeyLast4: null
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
    return ok(getStats(db.workoutLogs, db.bodyMetrics, year, month, todayLocal(), db.settings.targetWorkoutsPerWeek))
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
      if (!db.workoutTypes.some((t) => t.id === typeId)) return fail(400, 'Workout type not found')
      db.mappings.push({ healthConnectType: body.healthConnectType, workoutTypeId: typeId })
      persist(db)
      return ok({ healthConnectType: body.healthConnectType, workoutType: workoutTypeRef(db.workoutTypes.find((t) => t.id === typeId)) })
    }
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
