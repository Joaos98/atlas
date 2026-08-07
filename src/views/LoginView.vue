<template>
  <div class="login-page">
    <div class="login-card">
      <h1><img src="/atlas-logo.svg" class="logo-icon" alt="Atlas" /> Atlas</h1>
      <p class="subtitle">Log in to continue</p>
      <form @submit.prevent="handleLogin">
        <div>
          <label>Username</label>
          <input v-model="username" required />
        </div>
        <div>
          <label>Password</label>
          <input v-model="password" type="password" required />
        </div>
        <button type="submit">Log in</button>
        <p v-if="error" class="error">{{ error }}</p>
      </form>
    </div>
  </div>
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
  } catch {
    error.value = 'Invalid username or password'
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 40px 36px;
  width: 320px;
}
h1 {
  font-size: 1.4rem;
  margin: 0 0 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.logo-icon { height: 28px; }
.subtitle {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0 0 24px;
}
form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
label {
  display: block;
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-bottom: 4px;
}
input {
  width: 100%;
}
button[type="submit"] {
  background: var(--blue);
  color: var(--bg);
  border: none;
  margin-top: 6px;
}
button[type="submit"]:hover { filter: brightness(1.1); }
.error {
  color: var(--orange);
  font-size: 0.85rem;
  margin: 0;
}
</style>