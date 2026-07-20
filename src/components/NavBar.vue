<template>
  <nav>
    <router-link to="/">Dashboard</router-link>
    <router-link to="/workouts">Workouts</router-link>
    <router-link to="/body-metrics">Body Metrics</router-link>
    <router-link to="/goals">Goals</router-link>
    <button @click="handleLogout">Logout</button>
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
nav {
  display: flex;
  gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid #ddd;
  align-items: center;
}
a {
  text-decoration: none;
  color: #555;
}
a.router-link-active {
  font-weight: bold;
  color: #378ADD;
}
button {
  margin-left: auto;
  cursor: pointer;
}
</style>