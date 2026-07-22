<template>
  <form class="form-row compact" @submit.prevent="handleSubmit">
    <div class="form-field">
      <label><Calendar :size="14" /> Date</label>
      <DatePicker v-model="form.measuredOn" />
    </div>

    <div class="form-field">
      <label><Scale :size="14" /> Weight</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.weightKg" required placeholder="0" />
        <span class="unit">Kg</span>
      </div>
    </div>

    <div class="form-field">
      <label><Droplets :size="14" /> Body Water</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.waterLiters" required placeholder="0" />
        <span class="unit">L</span>
      </div>
    </div>

    <div class="form-field">
      <label><Dumbbell :size="14" /> Muscle Mass</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.muscleMassKg" required placeholder="0" />
        <span class="unit">Kg</span>
      </div>
    </div>

    <div class="form-field">
      <label><ChartPie :size="14" /> Body Fat Mass</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.bodyFatKg" required placeholder="0" />
        <span class="unit">Kg</span>
      </div>
    </div>

    <div class="form-field">
      <label><Percent :size="14" /> Body Fat Percentage</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.bodyFatPct" required placeholder="0" />
        <span class="unit">%</span>
      </div>
    </div>

    <div class="form-actions">
      <button type="submit" class="btn-primary">Add</button>
    </div>
  </form>
  <p v-if="success" class="form-success">Measurement added!</p>
  <p v-if="error" class="form-error">Something went wrong.</p>
</template>

<script setup>
import { ref } from 'vue'
import { logBodyMetrics } from '../services/bodyMetricsService'
import { todayLocal } from '../utils/date'
import DatePicker from './DatePicker.vue'
import { Calendar, Scale, Droplets, Dumbbell, ChartPie, Percent } from 'lucide-vue-next'

const success = ref(false)
const error = ref(false)
const emit = defineEmits(['logged'])

const form = ref({
  measuredOn: todayLocal(),
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
  } catch {
    error.value = true
  }
}
</script>
