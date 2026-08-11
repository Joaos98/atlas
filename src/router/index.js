import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import WorkoutsView from '../views/WorkoutsView.vue'
import BodyMetricsView from '@/views/BodyMetricsView.vue'
import GoalsView from '@/views/GoalsView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', component: DashboardView },
    { path: '/workouts', component: WorkoutsView },
    { path: '/body-metrics', component: BodyMetricsView },
    { path: '/goals', component: GoalsView },
    { path: '/settings', component: () => import('../views/SettingsView.vue') }
  ]
})

export default router
