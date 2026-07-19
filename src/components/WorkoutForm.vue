<template>
  <form @submit.prevent="handleSubmit">
    <div>
      <label>Date</label>
      <input type="date" v-model="form.logDate" required />
    </div>
    <div>
      <label>Workout type</label>
      <select v-model="form.workoutTypeId" required>
        <option v-for="type in workoutTypes" :key="type.id" :value="type.id">
          {{ type.name }}
        </option>
      </select>
    </div>
    <div>
      <label>Duration (minutes)</label>
      <input type="number" v-model="form.durationMinutes" required min="1" />
    </div>
    <div>
      <label>Calories (optional)</label>
      <input type="number" v-model="form.calories" min="0" />
    </div>
    <button type="submit">Log workout</button>
    <p v-if="success">Workout logged!</p>
    <p v-if="error">Something went wrong.</p>
  </form>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getWorkoutTypes, logWorkout } from '../services/workoutService'

const workoutTypes = ref([])
const success = ref(false)
const error = ref(false)

const form = ref({
  logDate: new Date().toISOString().split('T')[0],
  workoutTypeId: null,
  durationMinutes: null,
  calories: null
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
  success.value = false
  error.value = false
  try {
    await logWorkout({
      logDate: form.value.logDate,
      workoutType: { id: form.value.workoutTypeId },
      durationMinutes: form.value.durationMinutes,
      calories: form.value.calories || null
    })
    success.value = true
    emit('logged')
  } catch (e) {
    error.value = true
  }
}
</script>