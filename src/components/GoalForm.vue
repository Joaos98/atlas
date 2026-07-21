<template>
  <form @submit.prevent="handleSubmit">
    <div>
      <label>Metric</label>
      <select v-model="form.metricType" required>
        <option value="WEIGHT">Weight</option>
        <option value="MUSCLE_MASS">Muscle mass</option>
        <option value="WATER">Water</option>
        <option value="BODY_FAT_KG">Body fat (kg)</option>
        <option value="BODY_FAT_PCT">Body fat (%)</option>
      </select>
    </div>
    <div>
      <label>Target value</label>
      <input type="number" step="0.1" v-model="form.targetValue" required />
    </div>
    <div>
      <label>Target date (optional)</label>
      <input type="date" v-model="form.targetDate" />
    </div>
    <button type="submit">Add goal</button>
    <p v-if="success" class="success">Goal added!</p>
    <p v-if="error" class="error">Something went wrong.</p>
  </form>
</template>

<script setup>
import { ref } from 'vue'
import { createGoal } from '../services/goalsService'

const emit = defineEmits(['created'])
const success = ref(false)
const error = ref(false)

const form = ref({
  metricType: 'WEIGHT',
  targetValue: null,
  targetDate: null,
  status: 'ACTIVE'
})

async function handleSubmit() {
  success.value = false
  error.value = false
  try {
    await createGoal(form.value)
    success.value = true
    emit('created')
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