import api from './api'

export function getMappings() {
  return api.get('/sync/mappings')
}

export function addMapping(data) {
  return api.post('/sync/mappings', data)
}

export function deleteMapping(healthConnectType) {
  return api.delete(`/sync/mappings/${healthConnectType}`)
}
