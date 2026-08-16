import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getSettings, updateSettings } from '@/services/settingsService'

/**
 * One shared copy of `/api/settings`.
 *
 * Before this existed, four components each fetched it for themselves — DashboardView,
 * WorkoutsView, SettingsView and WeeklyWorkoutsChart — so a single page could issue the same
 * request three times, and saving in one view left the others showing stale values until a
 * reload. That was survivable while the only setting was a workout target read once on mount.
 * It stops being survivable with a unit preference, which every view renders through and which
 * has to re-render the moment it changes.
 */
export const useSettingsStore = defineStore('settings', () => {
  const settings = ref(null)
  let inFlight = null

  /** Cached. Concurrent callers share one request rather than racing several. */
  async function load({ force = false } = {}) {
    if (settings.value && !force) return settings.value
    if (!inFlight) {
      inFlight = getSettings()
        .then(({ data }) => {
          settings.value = data
          return data
        })
        .finally(() => { inFlight = null })
    }
    return inFlight
  }

  /**
   * Saves a partial patch and adopts the response as the new state. The API treats an absent
   * field as unchanged, so callers send only what they edited.
   */
  async function save(patch) {
    const { data } = await updateSettings(patch)
    settings.value = data
    return data
  }

  // `?? 4` preserves what the views did individually before they shared this store.
  const targetWorkoutsPerWeek = computed(() => settings.value?.targetWorkoutsPerWeek ?? 4)
  const unitSystem = computed(() => settings.value?.unitSystem ?? 'METRIC')

  return { settings, load, save, targetWorkoutsPerWeek, unitSystem }
})
