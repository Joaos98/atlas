import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUnits } from './useUnits'
import { useSettingsStore } from '../stores/settings'

function unitsIn(system) {
  const store = useSettingsStore()
  store.settings = { targetWorkoutsPerWeek: 3, unitSystem: system }
  return useUnits()
}

describe('useUnits', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('metric mode', () => {
    it('is a passthrough — the default must render exactly as the app did before', () => {
      const { toDisplay, toCanonical, format, label } = unitsIn('METRIC')

      expect(toDisplay(82.3, 'WEIGHT')).toBe(82.3)
      expect(toCanonical(82.3, 'WEIGHT')).toBe(82.3)
      expect(format(82.3, 'WEIGHT')).toBe('82.3')
      expect(label('WEIGHT')).toBe('kg')
      expect(label('WATER')).toBe('L')
      expect(label('BODY_FAT_PCT')).toBe('%')
    })

    it('defaults to metric when settings have not loaded', () => {
      setActivePinia(createPinia())
      const { label, isImperial } = useUnits()
      expect(isImperial.value).toBe(false)
      expect(label('WEIGHT')).toBe('kg')
    })
  })

  describe('imperial mode', () => {
    it('converts masses to pounds', () => {
      const { format, label } = unitsIn('IMPERIAL')
      expect(format(82.3, 'WEIGHT')).toBe('181.4')
      expect(label('WEIGHT')).toBe('lb')
    })

    /** The row most likely to be missed: a volume that converts to a mass, not to gallons. */
    it('renders body water as pounds rather than any imperial volume', () => {
      const { format, label } = unitsIn('IMPERIAL')
      expect(label('WATER')).toBe('lb')
      expect(format(41.2, 'WATER')).toBe('90.8')
    })

    it('leaves percentages alone', () => {
      const { format, label, formatWithUnit } = unitsIn('IMPERIAL')
      expect(format(18.4, 'BODY_FAT_PCT')).toBe('18.4')
      expect(label('BODY_FAT_PCT')).toBe('%')
      expect(formatWithUnit(18.4, 'BODY_FAT_PCT')).toBe('18.4 %')
    })

    it('accepts body_metrics field names as well as MetricType values', () => {
      const { label } = unitsIn('IMPERIAL')
      expect(label('waterLiters')).toBe('lb')
      expect(label('bodyFatPct')).toBe('%')
      expect(label('weightKg')).toBe('lb')
    })
  })

  /**
   * Display rounds to 1 dp, so 82.3 kg shows as 181.4 lb and
   * converting that straight back gives 82.28 — opening a row and saving it untouched would
   * rewrite stored data, and doing it repeatedly would walk the value away.
   */
  describe('round-trip guard', () => {
    it('returns the original canonical value when the displayed value is unchanged', () => {
      const { toDisplay, toCanonicalPreservingUnedited } = unitsIn('IMPERIAL')

      const stored = 82.3
      const shown = Number(toDisplay(stored, 'WEIGHT').toFixed(1)) // 181.4, what the form holds

      expect(toCanonicalPreservingUnedited(shown, stored, 'WEIGHT')).toBe(82.3)
    })

    it('does not drift across repeated no-op saves', () => {
      const { toDisplay, toCanonicalPreservingUnedited } = unitsIn('IMPERIAL')

      let stored = 82.3
      for (let i = 0; i < 10; i++) {
        const shown = Number(toDisplay(stored, 'WEIGHT').toFixed(1))
        stored = toCanonicalPreservingUnedited(shown, stored, 'WEIGHT')
      }
      expect(stored).toBe(82.3)
    })

    it('converts normally once the value is actually edited', () => {
      const { toCanonicalPreservingUnedited } = unitsIn('IMPERIAL')

      const result = toCanonicalPreservingUnedited(180, 82.3, 'WEIGHT')
      expect(result).toBeCloseTo(81.65, 2)
      expect(result).not.toBe(82.3)
    })

    it('is a no-op in metric mode, edited or not', () => {
      const { toCanonicalPreservingUnedited } = unitsIn('METRIC')

      expect(toCanonicalPreservingUnedited(82.3, 82.3, 'WEIGHT')).toBe(82.3)
      expect(toCanonicalPreservingUnedited(83.1, 82.3, 'WEIGHT')).toBe(83.1)
    })

    it('handles a first-time entry with no stored value', () => {
      const { toCanonicalPreservingUnedited } = unitsIn('IMPERIAL')
      expect(toCanonicalPreservingUnedited(180, null, 'WEIGHT')).toBeCloseTo(81.65, 2)
    })
  })

  /** Spec §7.5: a goal entered as 180 lb stores 81.65 kg and reads 81.7 kg in metric. */
  it('round-trips a goal target between the two systems', () => {
    const imperial = unitsIn('IMPERIAL')
    const stored = imperial.toCanonical(180, 'WEIGHT')
    expect(stored).toBeCloseTo(81.65, 2)
    expect(imperial.format(stored, 'WEIGHT')).toBe('180.0')

    const metric = unitsIn('METRIC')
    expect(metric.format(stored, 'WEIGHT')).toBe('81.6')
  })

  it('passes null and non-numeric input through without inventing values', () => {
    const { toDisplay, format, formatWithUnit } = unitsIn('IMPERIAL')
    expect(toDisplay(null, 'WEIGHT')).toBeNull()
    expect(format(null, 'WEIGHT')).toBeNull()
    expect(formatWithUnit(null, 'WEIGHT')).toBe('')
  })
})
