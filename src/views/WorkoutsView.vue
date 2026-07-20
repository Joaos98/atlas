<template>
  <div>
    <h1>Workouts</h1>
    <WorkoutForm @logged="onLogged" />
    <div class="card">
      <h2>Last 3 months</h2>
      <WorkoutHeatmap :refresh="refreshKey" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import WorkoutForm from '../components/WorkoutForm.vue'
import WorkoutHeatmap from '../components/WorkoutHeatmap.vue'

const refreshKey = ref(0)

function onLogged() {
  refreshKey.value++
}
</script>
<style scoped>
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px 24px;
  display: inline-block;
}
</style>