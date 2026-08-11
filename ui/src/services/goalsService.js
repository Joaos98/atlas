import api from '@/services/api'

export function getGoals() {
  return api.get('/goals')
}

export function createGoal(goal) {
  return api.post('/goals', goal)
}

export function updateGoalStatus(id, status) {
  return api.patch(`/goals/${id}/status`, null, { params: { status } })
}

export function deleteGoal(id) {
  return api.delete(`/goals/${id}`)
}