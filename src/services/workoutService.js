import api from '@/services/api'

export function getWorkoutTypes() {
  return api.get('/workout-types')
}

export function createWorkoutType(type) {
  return api.post('/workout-types', type)
}

export function deleteWorkoutType(id) {
  return api.delete(`/workout-types/${id}`)
}

export function getWorkoutLogs({ page = 0, size = 20 } = {}) {
  return api.get('/workout-logs', { params: { page, size } })
}

export function logWorkout(workout) {
  return api.post('/workout-logs', workout)
}

export function deleteWorkoutLog(id) {
  return api.delete(`/workout-logs/${id}`)
}

export function updateWorkoutLog(id, data) {
  return api.put(`/workout-logs/${id}`, data)
}

export function getHeatmap(startDate, endDate) {
  return api.get('/workout-logs/heatmap', { params: { startDate, endDate } })
}

export function getStreaks() {
  return api.get('/workout-logs/streaks')
}