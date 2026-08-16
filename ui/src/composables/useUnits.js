import { computed } from 'vue'
import { useSettingsStore } from '@/stores/settings'

const KG_TO_LB = 2.20462

/**
 * Per metric, because body water is the odd one out: it is a volume, not a mass, and there is
 * no imperial volume anyone wants to read — nobody says "10.9 gal of body water". Smart scales
 * in imperial markets report it as pounds, so WATER converts L to lb rather than to gallons.
 * That single row is why this is a table and not one global factor.
 */
const UNITS = {
  WEIGHT: { metric: 'kg', imperial: 'lb', factor: KG_TO_LB },
  MUSCLE_MASS: { metric: 'kg', imperial: 'lb', factor: KG_TO_LB },
  BODY_FAT_KG: { metric: 'kg', imperial: 'lb', factor: KG_TO_LB },
  WATER: { metric: 'L', imperial: 'lb', factor: KG_TO_LB },
  BODY_FAT_PCT: { metric: '%', imperial: '%', factor: 1 }
}

/** The five `body_metrics` field names, so callers can pass either a field or a MetricType. */
export const METRIC_BY_FIELD = {
  weightKg: 'WEIGHT',
  muscleMassKg: 'MUSCLE_MASS',
  waterLiters: 'WATER',
  bodyFatKg: 'BODY_FAT_KG',
  bodyFatPct: 'BODY_FAT_PCT'
}

function resolve(metricOrField) {
  return UNITS[metricOrField] ? metricOrField : METRIC_BY_FIELD[metricOrField]
}

function round(value, digits) {
  if (!Number.isFinite(value)) return value
  const scale = 10 ** digits
  return Math.round(value * scale) / scale
}

export function useUnits() {
  const settingsStore = useSettingsStore()
  const isImperial = computed(() => settingsStore.unitSystem === 'IMPERIAL')

  /** Unit label alone, e.g. "kg" or "lb". Percentages carry no leading space anywhere. */
  function label(metricOrField) {
    const spec = UNITS[resolve(metricOrField)]
    if (!spec) return ''
    return isImperial.value ? spec.imperial : spec.metric
  }

  /** Canonical (what the API stores) to displayed. Identity in metric mode. */
  function toDisplay(value, metricOrField) {
    const spec = UNITS[resolve(metricOrField)]
    if (spec == null || value == null || !Number.isFinite(Number(value))) return value
    return isImperial.value ? Number(value) * spec.factor : Number(value)
  }

  /** Displayed back to canonical, for anything the user typed. */
  function toCanonical(value, metricOrField) {
    const spec = UNITS[resolve(metricOrField)]
    if (spec == null || value == null || !Number.isFinite(Number(value))) return value
    return isImperial.value ? Number(value) / spec.factor : Number(value)
  }

  /**
   * The round-trip guard, and the reason this file has tests.
   *
   * Display rounds to 1 dp: 82.3 kg shows as 181.4 lb, and converting 181.4 back gives
   * 82.28 kg. Since the edit forms prefill from current values, opening a row in imperial and
   * saving it untouched would silently rewrite the stored number — and repeating it walks the
   * value further each time. So: if what the user is submitting rounds to the same thing they
   * were shown, they did not edit it, and the original canonical value goes back untouched.
   *
   * Compares rounded numbers rather than the strings the spec suggested, which is the same
   * test without treating "181.40" as a different value from "181.4".
   */
  function toCanonicalPreservingUnedited(displayValue, originalCanonical, metricOrField, digits = 1) {
    if (originalCanonical == null) return toCanonical(displayValue, metricOrField)

    const shown = toDisplay(originalCanonical, metricOrField)
    if (!Number.isFinite(Number(shown)) || !Number.isFinite(Number(displayValue))) {
      return toCanonical(displayValue, metricOrField)
    }
    if (round(Number(displayValue), digits) === round(Number(shown), digits)) {
      return originalCanonical
    }
    return toCanonical(displayValue, metricOrField)
  }

  /**
   * Number only, rounded for display. Pair with `label()` when a unit is wanted.
   *
   * The null check has to come before any coercion: `Number(null)` is 0, which is finite, so
   * a missing value would otherwise render as a confident "0.0" — which for a goal with no
   * baseline reads as a real measurement.
   */
  function format(value, metricOrField, digits = 1) {
    if (value == null) return value
    const converted = toDisplay(value, metricOrField)
    if (converted == null || !Number.isFinite(Number(converted))) return converted
    return round(Number(converted), digits).toFixed(digits)
  }

  /**
   * Number and unit, e.g. "181.4 lb".
   *
   * Always space-separated, percent included. The app was inconsistent about this before —
   * the body-composition tiles rendered "21.1 %" while GoalsView rendered "80%", and weight
   * appeared as both "Kg" and "kg" within a single card. One spelling, one spacing.
   */
  function formatWithUnit(value, metricOrField, digits = 1) {
    const number = format(value, metricOrField, digits)
    if (number == null || number === '') return ''
    return `${number} ${label(metricOrField)}`
  }

  return {
    isImperial,
    label,
    toDisplay,
    toCanonical,
    toCanonicalPreservingUnedited,
    format,
    formatWithUnit
  }
}
