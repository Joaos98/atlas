<template>
  <div>
    <h1>Workouts</h1>
    <WorkoutForm @logged="onLogged" />
    <h2>Last 3 months</h2>
    <WorkoutHeatmap :refresh="refreshKey" />
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