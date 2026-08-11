// Fixture test: loads the committed demo-seed.json, materializes it against
// the seed's reference date, runs the ported derived logic, and asserts it
// equals expected-derived.json — the responses recorded from the real Java
// API. If the port or the seed drifts, this fails loudly.

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import seedData from './demo-seed.json'
import { materialize } from './seed.js'
import { calculateStreaks, getHeatmapData, getStats, getGoalsProgress, addDays } from './derived.js'

const expected = JSON.parse(
  readFileSync(fileURLToPath(new URL('./expected-derived.json', import.meta.url)), 'utf8')
)

const db = materialize(seedData, seedData.referenceDate)
const today = seedData.referenceDate

describe('ported derived logic matches recorded Java fixture', () => {
  it('streaks match the recorded response', () => {
    const dates = [...new Set(db.workoutLogs.map((l) => l.logDate))]
    const result = calculateStreaks(dates, db.settings.targetWorkoutsPerWeek, today)
    expect(result).toEqual(expected.streaks.response)
  })

  it('heatmap matches the recorded response', () => {
    const startDate = addDays(today, expected.heatmap.startOffsetDays)
    const endDate = addDays(today, expected.heatmap.endOffsetDays)
    const result = getHeatmapData(db.workoutLogs, startDate, endDate)
    expect(result).toEqual(expected.heatmap.response)
  })
  it('stats match the recorded response', () => {
    const result = getStats(
      db.workoutLogs,
      db.bodyMetrics,
      expected.stats.year,
      expected.stats.month,
      today,
      db.settings.targetWorkoutsPerWeek
    )
    expect(result).toEqual(expected.stats.response)
  })

  it('goals match the recorded response', () => {
    const result = getGoalsProgress(db.goals, db.bodyMetrics, today)
    expect(result).toEqual(expected.goals.response)
  })
})
