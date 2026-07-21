<template>
  <div>
    <h1>Workouts</h1>
    <h2>Add New Workout</h2>
    <WorkoutForm @logged="onLogged" />
    <h2>Workout activity</h2>
    <div class="card heatmap-card">
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
.heatmap-card {
  max-width: 1000px;
  display: block;
}
</style>