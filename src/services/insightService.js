import api from './api'

export function getInsights() {
  return api.get('/insights')
}

export function regenerateInsights() {
  return api.post('/insights/regenerate')
}
