import api from './api'

export function getBodyMetrics() {
  return api.get('/body-metrics')
}

export function logBodyMetrics(entry) {
  return api.post('/body-metrics', entry)
}