<template>
  <div class="app-shell">
    <NavBar v-if="auth.isAuthenticated" />
    <main class="content"><router-view /></main>
  </div>
</template>

<script setup>
import NavBar from './components/NavBar.vue'
import { useAuthStore } from './stores/auth'
const auth = useAuthStore()
</script>

<style scoped>
.app-shell { display: flex; }
.content { flex: 1; padding: 24px 32px; }
</style>