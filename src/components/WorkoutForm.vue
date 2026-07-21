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
      <label>Duration</label>
      <div class="input-wrapper">
        <input type="number" v-model="form.durationMinutes" required min="1" />
        <span class="unit">minutes</span>
      </div>
    </div>
    
    <div>
      <label>Calories (optional)</label>
      <div class="input-wrapper">
        <input type="number" v-model="form.calories" min="0" />
        <span class="unit">kcal</span>
      </div>
    </div>
    
    <div class="actions">
      <button type="submit">Add</button>
      <p v-if="success" class="success">Workout added!</p>
      <p v-if="error" class="error">Something went wrong.</p>
    </div>
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
  padding-right: 64px; /* Slightly wider here to accommodate "minutes" */
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
  color: #1B2F52;
  border: none;
  padding: 8px 16px;
  cursor: pointer;
}

p { font-size: 0.85rem; margin: 0; }
.success { color: var(--green); }
.error { color: var(--orange); }

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