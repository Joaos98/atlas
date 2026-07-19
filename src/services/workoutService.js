import api from './api'

export function getWorkoutTypes() {
  return api.get('/workout-types')
}

export function logWorkout(workout) {
  return api.post('/workout-logs', workout)
}

export function getHeatmap(startDate, endDate) {
  return api.get('/workout-logs/heatmap', { params: { startDate, endDate } })
}