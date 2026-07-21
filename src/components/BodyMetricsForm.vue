<template>
  <form @submit.prevent="handleSubmit">
    <div>
      <label>Date</label>
      <input type="date" v-model="form.measuredOn" required />
    </div>
    <div>
      <label>Weight (kg)</label>
      <input type="number" step="0.1" v-model="form.weightKg" required />
    </div>
    <div>
      <label>Muscle mass (kg)</label>
      <input type="number" step="0.1" v-model="form.muscleMassKg" required />
    </div>
    <div>
      <label>Water (liters)</label>
      <input type="number" step="0.1" v-model="form.waterLiters" required />
    </div>
    <div>
      <label>Body fat (kg)</label>
      <input type="number" step="0.1" v-model="form.bodyFatKg" required />
    </div>
    <div>
      <label>Body fat (%)</label>
      <input type="number" step="0.1" v-model="form.bodyFatPct" required />
    </div>
    <button type="submit">Log measurement</button>
    <p v-if="success" class="success">Measurement logged!</p>
    <p v-if="error" class="error">Something went wrong.</p>
  </form>
</template>

<script setup>
import { ref } from 'vue'
import { logBodyMetrics } from '../services/bodyMetricsService'

const success = ref(false)
const error = ref(false)
const emit = defineEmits(['logged'])

const form = ref({
  measuredOn: new Date().toISOString().split('T')[0],
  weightKg: null,
  muscleMassKg: null,
  waterLiters: null,
  bodyFatKg: null,
  bodyFatPct: null
})

async function handleSubmit() {
  success.value = false
  error.value = false
  try {
    await logBodyMetrics(form.value)
    success.value = true
    emit('logged')
  } catch (e) {
    error.value = true
  }
}
</script>
<style scoped>
form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 320px;
  margin-bottom: 24px;
}
label {
  display: block;
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-bottom: 4px;
}
button[type="submit"] {
  background: var(--blue);
  color: var(--bg);
  border: none;
}
.success { color: var(--green); }
.error { color: var(--orange); }
</style>