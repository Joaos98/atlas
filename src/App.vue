<template>
  <div class="app-column">
    <DemoBanner v-if="isDemo" />
    <div class="app-shell">
      <NavBar />
      <main class="content"><router-view /></main>
    </div>
    <InsightGateModal v-if="isDemo" />
    <ToastContainer />
  </div>
</template>

<script setup>
import NavBar from './components/NavBar.vue'
import ToastContainer from './components/ToastContainer.vue'
import DemoBanner from './demo/DemoBanner.vue'
import InsightGateModal from './demo/InsightGateModal.vue'

const isDemo = import.meta.env.MODE === 'demo'
</script>

<style scoped>
.app-column { min-height: 100vh; display: flex; flex-direction: column; }
.app-shell { display: flex; flex: 1; }
.content { flex: 1; padding: 24px 32px; }
</style>
