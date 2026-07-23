<template>
  <form class="form-row" @submit.prevent="handleSubmit">
    <div class="form-field">
      <label><Calendar :size="14" /> Date</label>
      <DatePicker v-model="form.logDate" />
    </div>

    <div class="form-field">
      <label><Dumbbell :size="14" /> Workout type</label>
      <select v-model="form.workoutTypeId" required>
        <option v-for="type in workoutTypes" :key="type.id" :value="type.id">
          {{ type.name }}
        </option>
      </select>
    </div>

    <div class="form-field">
      <label><Clock :size="14" /> Duration</label>
      <div class="input-wrapper wide">
        <input type="number" v-model="form.durationMinutes" required min="1" placeholder="45" />
        <span class="unit">minutes</span>
      </div>
    </div>

    <div class="form-actions">
      <button type="submit" class="btn-primary">Add</button>
    </div>
  </form>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getWorkoutTypes, logWorkout } from '../services/workoutService'
import { todayLocal } from '../utils/date'
import { useToastStore } from '../stores/toast'
import DatePicker from './DatePicker.vue'
import { Calendar, Dumbbell, Clock } from 'lucide-vue-next'

const toast = useToastStore()
const workoutTypes = ref([])

const form = ref({
  logDate: todayLocal(),
  workoutTypeId: null,
  durationMinutes: null
})

const emit = defineEmits(['logged'])

onMounted(async () => {
  const response = await getWorkoutTypes()
  workoutTypes.value = response.data
  if (workoutTypes.value.length > 0) {
    form.value.workoutTypeId = workoutTypes.value[0].id
  }
})

async function handleSubmit() {
  try {
    await logWorkout({
      logDate: form.value.logDate,
      workoutType: { id: form.value.workoutTypeId },
      durationMinutes: form.value.durationMinutes
    })
    toast.success('Workout logged')
    emit('logged')
  } catch {
    toast.error('Failed to log workout')
  }
}
</script>
