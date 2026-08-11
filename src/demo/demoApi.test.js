// Smoke test: exercises the demo adapter's full endpoint surface with a
// localStorage shim, mirroring exactly how the service layer calls it.
// Not part of the fixture contract — just proves the wiring works.

import { describe, it, expect, beforeEach, vi } from 'vitest'

const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k)
}
vi.stubGlobal('location', { reload: vi.fn() })

const { default: api, resetDemoData } = await import('./demoApi.js')

const ok = async (p) => {
  const res = await p
  return res.data
}

describe('demo adapter endpoint surface', () => {
  beforeEach(() => {
    store.clear()
    // fresh module state via reload of the api instance
  })

  it('seeds on first load and serves workout types', async () => {
    const types = await ok(api.get('/workout-types'))
    expect(types.length).toBe(4)
    expect(types[0]).toMatchObject({ name: 'Run', colorHex: '#e63946' })
  })

  it('materialized dates are relative to the anchor Sunday', async () => {
    const logs = await ok(api.get('/workout-logs'))
    expect(logs.totalElements).toBeGreaterThan(200)
    const last = logs.content[0]
    expect(last.logDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(last.workoutType).toHaveProperty('name')
  })

  it('serves streaks, stats, heatmap from the ported logic', async () => {
    const streaks = await ok(api.get('/workout-logs/streaks'))
    expect(streaks).toHaveProperty('currentStreak')
    expect(streaks).toHaveProperty('longestStreak')

    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1
    const stats = await ok(api.get('/stats', { params: { year, month } }))
    expect(stats.workoutStats).toHaveProperty('totalWorkoutsThisMonth')
    expect(stats.streakStats).toEqual(streaks)

    const sunday = new Date(now)
    sunday.setDate(sunday.getDate() - sunday.getDay())
    const nextSunday = new Date(sunday)
    nextSunday.setDate(nextSunday.getDate() + 7)
    const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const heatmap = await ok(api.get('/workout-logs/heatmap', { params: { startDate: fmt(sunday), endDate: fmt(nextSunday) } }))
    expect(Array.isArray(heatmap)).toBe(true)
  })

  it('serves goals with progress', async () => {
    const goals = await ok(api.get('/goals'))
    expect(goals.length).toBe(4)
    expect(goals[0].status).toBe('ACHIEVED')
    expect(goals[1]).toHaveProperty('currentValue')
  })

  it('serves the seeded insight', async () => {
    const insight = await ok(api.get('/insights'))
    expect(insight.verdict).toBeTruthy()
    expect(insight.text).toBeTruthy()
    expect(insight.fallback).toBe(false)
  })

  it('gates insight regeneration', async () => {
    await expect(api.post('/insights/regenerate')).rejects.toMatchObject({
      response: { status: 403 }
    })
  })

  it('CRUD: create and update a workout log', async () => {
    const created = await ok(api.post('/workout-logs', {
      logDate: '2026-08-05',
      workoutType: { id: 1 },
      durationMinutes: 42
    }))
    expect(created.id).toBeTruthy()
    const updated = await ok(api.put(`/workout-logs/${created.id}`, {
      logDate: '2026-08-06',
      workoutType: { id: 2 },
      durationMinutes: 55
    }))
    expect(updated.durationMinutes).toBe(55)
    expect(updated.workoutType.name).toBe('Strength')
    await ok(api.delete(`/workout-logs/${created.id}`))
    await expect(api.get('/workout-logs')).resolves.toBeTruthy()
  })

  it('CRUD: body metrics and goals', async () => {
    const m = await ok(api.post('/body-metrics', {
      measuredOn: '2026-08-05', weightKg: 78.5, muscleMassKg: 39.0,
      waterLiters: 41.0, bodyFatKg: 16.0, bodyFatPct: 20.4
    }))
    expect(m.id).toBeTruthy()
    await ok(api.put(`/body-metrics/${m.id}`, { measuredOn: '2026-08-05', weightKg: 78.3 }))
    await ok(api.delete(`/body-metrics/${m.id}`))

    const g = await ok(api.post('/goals', { metricType: 'WEIGHT', targetValue: 77.0, status: 'ACTIVE' }))
    expect(g.startValue).toBeTruthy()
    await ok(api.patch(`/goals/${g.id}/status`, null, { params: { status: 'ABANDONED' } }))
    const goals = await ok(api.get('/goals'))
    expect(goals.find((x) => x.id === g.id).status).toBe('ABANDONED')
    await ok(api.delete(`/goals/${g.id}`))
  })

  it('settings and mappings', async () => {
    const settings = await ok(api.get('/settings'))
    expect(settings.targetWorkoutsPerWeek).toBe(3)
    await ok(api.put('/settings', { targetWorkoutsPerWeek: 4 }))
    expect((await ok(api.get('/settings'))).targetWorkoutsPerWeek).toBe(4)

    const mappings = await ok(api.get('/sync/mappings'))
    expect(mappings).toEqual([])
  })

  it('type delete conflicts when logs exist', async () => {
    await expect(api.delete('/workout-types/1')).rejects.toMatchObject({ response: { status: 409 } })
  })

  it('resetDemoData clears storage', () => {
    resetDemoData()
    expect(store.has('atlas-demo-db')).toBe(false)
  })
})
