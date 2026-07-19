<template>
  <form @submit.prevent="handleLogin">
    <input v-model="username" placeholder="Username" />
    <input v-model="password" type="password" placeholder="Password" />
    <button type="submit">Log in</button>
    <p v-if="error">{{ error }}</p>
  </form>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../services/api'

const username = ref('')
const password = ref('')
const error = ref('')
const router = useRouter()
const auth = useAuthStore()

async function handleLogin() {
  try {
    await api.get('/auth/me', {
        auth: { username: username.value, password: password.value }
    })
    auth.login(username.value, password.value)
    router.push('/')
  } catch (e) {
    error.value = 'Invalid username or password'
  }
}
</script>