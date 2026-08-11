import api from '@/services/api'

export function getStats(year, month) {
  return api.get('/stats', { params: { year, month } })
}