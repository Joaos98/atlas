import api from './api'

export function getSettings() {
  return api.get('/settings')
}

export function updateSettings(data) {
  return api.put('/settings', data)
}
