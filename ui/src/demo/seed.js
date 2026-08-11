// Materializes demo-seed.json's day offsets against the visitor's most recent
// Sunday and persists to localStorage, versioned so improved seeds reach
// returning visitors.

import { addDays } from './derived.js'

export const STORAGE_KEY = 'atlas-demo-db'

export function materialize(seed, anchor) {
  const settings = { id: 1, targetWorkoutsPerWeek: seed.appSettings.targetWorkoutsPerWeek }

  const workoutTypes = seed.workoutTypes.map((t) => ({
    id: t.id,
    name: t.name,
    colorHex: t.colorHex
  }))

  const typeById = new Map(workoutTypes.map((t) => [t.id, t]))

  const workoutLogs = seed.workoutLogs.map((l) => ({
    id: l.id,
    workoutTypeId: l.workoutTypeId,
    workoutType: typeById.get(l.workoutTypeId),
    logDate: addDays(anchor, l.dateOffsetDays),
    durationMinutes: l.durationMinutes,
    syncSignature: null
  }))

  const bodyMetrics = seed.bodyMetrics.map((m, i) => ({
    id: i + 1,
    measuredOn: addDays(anchor, m.dateOffsetDays),
    weightKg: m.weightKg,
    muscleMassKg: m.muscleMassKg,
    waterLiters: m.waterLiters,
    bodyFatKg: m.bodyFatKg,
    bodyFatPct: m.bodyFatPct,
    insightText: m.insightText ?? null,
    insightGeneratedAt: m.insightGeneratedAt ?? null
  }))

  const goals = seed.goals.map((g) => ({
    id: g.id,
    metricType: g.metricType,
    targetValue: g.targetValue,
    targetDate: g.targetDateOffsetDays == null ? null : addDays(anchor, g.targetDateOffsetDays),
    status: g.status,
    createdAt: addDays(anchor, g.createdAtOffsetDays) + 'T00:00:00',
    startValue: g.startValue
  }))

  return {
    seedVersion: seed.version,
    anchor,
    settings,
    workoutTypes,
    workoutLogs,
    bodyMetrics,
    goals,
    mappings: []
  }
}

export function loadOrInit(seed) {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored) {
    try {
      const db = JSON.parse(stored)
      if (db.seedVersion === seed.version) return db
    } catch {
      // corrupt storage — fall through to reseed
    }
  }
  const db = materialize(seed, mostRecentSunday())
  persist(db)
  return db
}

export function persist(db) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(db))
}

export function resetDemo() {
  localStorage.removeItem(STORAGE_KEY)
}

export function mostRecentSunday() {
  const today = new Date()
  const sunday = new Date(today)
  sunday.setDate(sunday.getDate() - sunday.getDay())
  const y = sunday.getFullYear()
  const m = String(sunday.getMonth() + 1).padStart(2, '0')
  const d = String(sunday.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function todayLocal() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function nextId(rows) {
  return rows.reduce((max, r) => Math.max(max, r.id), 0) + 1
}
