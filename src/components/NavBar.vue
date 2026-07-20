<template>
  <nav class="sidebar">
    <div class="brand">AIO Fitness</div>
    <router-link to="/" class="nav-item">Dashboard</router-link>
    <router-link to="/workouts" class="nav-item">Workouts</router-link>
    <router-link to="/body-metrics" class="nav-item">Body Metrics</router-link>
    <router-link to="/goals" class="nav-item">Goals</router-link>
    <button class="logout" @click="handleLogout">Logout</button>
  </nav>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  padding: 20px 12px;
  gap: 4px;
  min-height: 100vh;
}
.brand {
  font-family: var(--font-display);
  font-size: 1.1rem;
  padding: 0 8px 20px;
}
.nav-item {
  text-decoration: none;
  color: var(--text-muted);
  padding: 10px 8px;
  border-radius: 6px;
  font-weight: 500;
}
.nav-item.router-link-active {
  color: var(--text);
  background: var(--bg);
}
.logout {
  margin-top: auto;
  background: transparent;
}
</style>