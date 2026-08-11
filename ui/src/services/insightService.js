import api from '@/services/api'

export function getInsights() {
  return api.get('/insights')
}

export function regenerateInsights() {
  return api.post('/insights/regenerate')
}
