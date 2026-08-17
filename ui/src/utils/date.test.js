import { describe, it, expect } from 'vitest'
import { formatDateBr, toLocalDateStr } from './date'

describe('formatDateBr', () => {
  it('formats a plain date', () => {
    expect(formatDateBr('2026-07-14')).toBe('14/07/2026')
  })

  it('formats a timestamp, dropping the time', () => {
    // goal.createdAt is a LocalDateTime. Splitting on '-' alone left the time attached to
    // the day and rendered "Created 18T00:00:00/01/2026" on every past goal.
    expect(formatDateBr('2026-01-18T00:00:00')).toBe('18/01/2026')
    expect(formatDateBr('2026-01-18T23:59:59.123')).toBe('18/01/2026')
  })

  it('returns empty for a missing value, since the template renders it inline', () => {
    expect(formatDateBr(null)).toBe('')
    expect(formatDateBr(undefined)).toBe('')
    expect(formatDateBr('')).toBe('')
  })

  it('does not construct a Date, so an evening timestamp never shifts a day at UTC-3', () => {
    expect(formatDateBr('2026-03-01T22:00:00')).toBe('01/03/2026')
  })
})

describe('toLocalDateStr', () => {
  it('uses local components rather than the UTC ones toISOString would give', () => {
    // 22:00 local on 1 March is already 2 March in UTC.
    expect(toLocalDateStr(new Date(2026, 2, 1, 22, 0, 0))).toBe('2026-03-01')
  })
})
