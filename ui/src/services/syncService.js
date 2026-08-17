import api from '@/services/api'

export function getMappings() {
  return api.get('/sync/mappings')
}

export function addMapping(data) {
  return api.post('/sync/mappings', data)
}

export function deleteMapping(healthConnectType) {
  return api.delete(`/sync/mappings/${healthConnectType}`)
}

/** The static Health Connect vocabulary, so the mapping form can offer names, not codes. */
export function getExerciseTypes() {
  return api.get('/sync/exercise-types')
}

export function getSyncSources() {
  return api.get('/sync/sources')
}

// Origins are package names, so both path segments have to be encoded.
export function setSyncSourceAllowed(origin, method, allowed) {
  return api.put(`/sync/sources/${encodeURIComponent(origin)}/${encodeURIComponent(method)}`, { allowed })
}

/** The workouts currently held for a source, so the user can see before deciding. */
export function getHeldEntries(origin, method) {
  return api.get(`/sync/sources/${encodeURIComponent(origin)}/${encodeURIComponent(method)}/quarantine`)
}

export function dismissQuarantine(origin, method) {
  return api.delete(`/sync/sources/${encodeURIComponent(origin)}/${encodeURIComponent(method)}/quarantine`)
}
