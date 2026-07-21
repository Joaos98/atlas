<template>
  <form @submit.prevent="handleSubmit">
    <div>
      <label>Date</label>
      <input type="date" v-model="form.measuredOn" required />
    </div>
    
    <div>
      <label>Weight</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.weightKg" required />
        <span class="unit">Kg</span>
      </div>
    </div>
    
    <div>
      <label>Body Water</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.waterLiters" required />
        <span class="unit">L</span>
      </div>
    </div>

    <div>
      <label>Muscle Mass</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.muscleMassKg" required />
        <span class="unit">Kg</span>
      </div>
    </div>
    
    <div>
      <label>Body Fat Mass</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.bodyFatKg" required />
        <span class="unit">Kg</span>
      </div>
    </div>
    
    <div>
      <label>Body Fat Percentage</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.bodyFatPct" required />
        <span class="unit">%</span>
      </div>
    </div>
    
    <div class="actions">
      <button type="submit">Add</button>
      <p v-if="success" class="success">Measurement added!</p>
      <p v-if="error" class="error">Something went wrong.</p>
    </div>
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
  gap: 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
  align-items: flex-end;
}

label {
  display: block;
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-bottom: 4px;
}

input {
  max-width: 170px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper input {
  padding-right: 48px;
  box-sizing: border-box;
}

.input-wrapper .unit {
  position: absolute;
  right: 12px;
  font-size: 0.85rem;
  color: var(--text-muted);
  pointer-events: none;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

button[type="submit"] {
  background: var(--blue);
  color: var(--bg);
  border: none;
  padding: 8px 16px;
  cursor: pointer;
}

.success { color: var(--green); margin: 0; font-size: 0.85rem; }
.error { color: var(--orange); margin: 0; font-size: 0.85rem; }

/* -----------------------------------------
   Hide Number Input Arrows (Spinners)
----------------------------------------- */
/* Chrome, Safari, Edge, Opera */
input[type="number"]::-webkit-outer-spin-button,
input[type="number"]::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

/* Firefox */
input[type="number"] {
  -moz-appearance: textfield;
  appearance: textfield;
}
</style>