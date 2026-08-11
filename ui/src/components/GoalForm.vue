<template>
  <form class="form-row" @submit.prevent="handleSubmit">
    <div class="form-field">
      <label><Target :size="14" /> Metric</label>
      <select v-model="form.metricType" required>
        <option value="WEIGHT">Weight</option>
        <option value="MUSCLE_MASS">Muscle mass</option>
        <option value="WATER">Water</option>
        <option value="BODY_FAT_KG">Body fat (kg)</option>
        <option value="BODY_FAT_PCT">Body fat (%)</option>
      </select>
    </div>
    <div class="form-field">
      <label><Crosshair :size="14" /> Target value</label>
      <input type="number" step="0.1" v-model="form.targetValue" required placeholder="75.0" />
    </div>
    <div class="form-field">
      <label><CalendarDays :size="14" /> Target date (optional)</label>
      <DatePicker v-model="form.targetDate" clearable />
    </div>
    <div class="form-actions">
      <button type="submit" class="btn-primary">Add goal</button>
    </div>
  </form>
</template>

<script setup>
import { ref } from 'vue'
import { createGoal } from '../services/goalsService'
import { useToastStore } from '../stores/toast'
import DatePicker from './DatePicker.vue'
import { Target, Crosshair, CalendarDays } from 'lucide-vue-next'

const toast = useToastStore()
const emit = defineEmits(['created'])

const form = ref({
  metricType: 'WEIGHT',
  targetValue: null,
  targetDate: null,
  status: 'ACTIVE'
})

async function handleSubmit() {
  try {
    await createGoal(form.value)
    toast.success('Goal created')
    emit('created')
  } catch {
    toast.error('Failed to create goal')
  }
}
</script>
