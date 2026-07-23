import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import WorkoutsView from '../views/WorkoutsView.vue'
import BodyMetricsView from '@/views/BodyMetricsView.vue'
import GoalsView from '@/views/GoalsView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/', component: DashboardView },
    { path: '/workouts', component: WorkoutsView },
    { path: '/body-metrics', component: BodyMetricsView },
    { path: '/goals', component: GoalsView },
    { path: '/settings', component: () => import('../views/SettingsView.vue') }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.isAuthenticated) {
    return '/login'
  }
})

export default router