import api from '@/services/api'

export function getBodyMetrics() {
  return api.get('/body-metrics')
}

export function logBodyMetrics(entry) {
  return api.post('/body-metrics', entry)
}

export function deleteBodyMetrics(id) {
  return api.delete(`/body-metrics/${id}`)
}

export function updateBodyMetrics(id, data) {
  return api.put(`/body-metrics/${id}`, data)
}