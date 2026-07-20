<template>
  <NavBar v-if="auth.isAuthenticated" />
  <router-view />
</template>

<script setup>
import NavBar from './components/NavBar.vue';
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
</script>