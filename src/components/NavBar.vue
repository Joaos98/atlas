<template>
  <nav class="sidebar">
    <div class="brand"><Dumbbell :size="18" class="brand-icon" /> Atlas</div>
    <router-link to="/" class="nav-item"><LayoutDashboard :size="16" /> Dashboard</router-link>
    <router-link to="/workouts" class="nav-item"><Dumbbell :size="16" /> Workouts</router-link>
    <router-link to="/body-metrics" class="nav-item"><Activity :size="16" /> Body Metrics</router-link>
    <router-link to="/goals" class="nav-item"><Target :size="16" /> Goals</router-link>
    <router-link to="/settings" class="nav-item"><Settings :size="16" /> Settings</router-link>
    <button class="logout" @click="handleLogout"><LogOut :size="16" /> Logout</button>
  </nav>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { LayoutDashboard, Dumbbell, Activity, Target, LogOut, Settings } from 'lucide-vue-next'

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
  height: 100vh;
  position: sticky;
  top: 0;
}
.brand { font-family: var(--font-display); font-size: 1.1rem; padding: 0 8px 20px; display: flex; align-items: center; gap: 8px; }
.brand-icon { color: var(--blue); }
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--text-muted);
  padding: 10px 8px;
  border-radius: 6px;
  font-weight: 500;
}
.nav-item:hover { background: var(--bg); color: var(--text); }
.nav-item.router-link-active { color: var(--text); background: var(--bg); }
.logout { display: flex; align-items: center; gap: 10px; margin-top: auto; background: transparent; }
.logout:hover { color: var(--text); background: var(--bg); }
</style>