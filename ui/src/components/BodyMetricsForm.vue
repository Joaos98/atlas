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
        <span class="unit">{{ label('weightKg') }}</span>
      </div>
    </div>

    <div class="form-field">
      <label><Droplets :size="14" /> Body Water</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.waterLiters" required placeholder="0" />
        <span class="unit">{{ label('waterLiters') }}</span>
      </div>
    </div>

    <div class="form-field">
      <label><Dumbbell :size="14" /> Muscle Mass</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.muscleMassKg" required placeholder="0" />
        <span class="unit">{{ label('muscleMassKg') }}</span>
      </div>
    </div>

    <div class="form-field">
      <label><ChartPie :size="14" /> Body Fat Mass</label>
      <div class="input-wrapper">
        <input type="number" step="0.1" v-model="form.bodyFatKg" required placeholder="0" />
        <span class="unit">{{ label('bodyFatKg') }}</span>
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
</template>

<script setup>
import { ref } from 'vue'
import { logBodyMetrics } from '../services/bodyMetricsService'
import { todayLocal } from '../utils/date'
import { useToastStore } from '../stores/toast'
import { useUnits } from '../composables/useUnits'
import DatePicker from './DatePicker.vue'
import { Calendar, Scale, Droplets, Dumbbell, ChartPie, Percent } from 'lucide-vue-next'

const toast = useToastStore()
const { label, toCanonical } = useUnits()
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
  try {
    // Typed in whatever the user is seeing; the API stores canonical metric. No round-trip
    // guard needed here — this form always starts empty, so every value is genuinely entered.
    const payload = { measuredOn: form.value.measuredOn }
    for (const field of ['weightKg', 'waterLiters', 'muscleMassKg', 'bodyFatKg', 'bodyFatPct']) {
      payload[field] = toCanonical(form.value[field], field)
    }

    await logBodyMetrics(payload)
    toast.success('Measurement logged')
    emit('logged')
  } catch {
    toast.error('Failed to log measurement')
  }
}
</script>
