<template>
  <div v-if="quarantineCount || pendingTypes.length" class="sync-notices">
    <!-- The user this serves is looking at a dashboard with nothing on it and has no reason
         to suspect a settings page exists. -->
    <div v-if="quarantineCount && !quarantineDismissed" class="notice">
      <Inbox :size="16" class="notice-icon" />
      <p class="notice-text">
        Atlas received <strong>{{ quarantineCount }}</strong>
        {{ quarantineCount === 1 ? 'workout' : 'workouts' }} from a source that isn't enabled.
      </p>
      <RouterLink to="/settings" class="notice-action">Review sources</RouterLink>
      <button class="notice-dismiss" title="Hide" @click="quarantineDismissed = true"><X :size="14" /></button>
    </div>

    <!-- Taxonomy drift has to be visible the day it starts, not discovered months later as an
         unexplained extra slice on a chart. -->
    <div v-for="type in pendingTypes" :key="type.id" class="notice">
      <Sparkles :size="16" class="notice-icon" />
      <p class="notice-text">
        New activity type added:
        <span class="type-dot" :style="{ backgroundColor: type.colorHex }"></span>
        <strong>{{ type.name }}</strong>
        <span class="notice-count">
          ({{ type.logCount }} {{ type.logCount === 1 ? 'workout' : 'workouts' }})
        </span>
      </p>
      <select class="notice-select" :value="''" @change="merge(type, $event)">
        <option value="" disabled>Merge into…</option>
        <option v-for="other in mergeTargets(type)" :key="other.id" :value="other.id">{{ other.name }}</option>
      </select>
      <button class="notice-dismiss" title="Dismiss" @click="dismiss(type)"><X :size="14" /></button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { Inbox, Sparkles, X } from 'lucide-vue-next'
import { getSyncSources } from '../services/syncService'
import {
  getPendingReviewTypes, getWorkoutTypes, mergeWorkoutType, dismissTypeReview
} from '../services/workoutService'
import { useToastStore } from '../stores/toast'

const toast = useToastStore()

const quarantineCount = ref(0)
const quarantineDismissed = ref(false)
const pendingTypes = ref([])
const allTypes = ref([])

async function load() {
  try {
    const [sourcesRes, pendingRes, typesRes] = await Promise.all([
      getSyncSources(), getPendingReviewTypes(), getWorkoutTypes()
    ])
    quarantineCount.value = sourcesRes.data.reduce((sum, s) => sum + (s.quarantinedCount || 0), 0)
    pendingTypes.value = pendingRes.data
    allTypes.value = typesRes.data
  } catch {
    // A dashboard notice is never worth breaking the dashboard for.
    quarantineCount.value = 0
    pendingTypes.value = []
  }
}

function mergeTargets(type) {
  return allTypes.value.filter(t => t.id !== type.id)
}

async function merge(type, event) {
  const targetId = Number(event.target.value)
  event.target.value = ''
  if (!targetId) return

  const target = allTypes.value.find(t => t.id === targetId)
  if (!confirm(`Merge "${type.name}" into "${target.name}"? Its workouts move across and "${type.name}" is removed.`)) return

  try {
    await mergeWorkoutType(type.id, targetId)
    await load()
    toast.success(`Merged into ${target.name}`)
  } catch {
    toast.error('Could not merge types')
  }
}

async function dismiss(type) {
  try {
    await dismissTypeReview(type.id)
    pendingTypes.value = pendingTypes.value.filter(t => t.id !== type.id)
  } catch {
    toast.error('Could not dismiss')
  }
}

onMounted(load)
</script>

<style scoped>
.sync-notices {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}
.notice {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  background: var(--surface);
  border: 1px solid var(--border);
  border-left: 3px solid var(--blue);
  border-radius: 8px;
  padding: 10px var(--space-3);
}
.notice-icon { color: var(--blue); flex-shrink: 0; }
.notice-text {
  margin: 0;
  font-size: 0.85rem;
  color: var(--text);
  flex: 1;
}
.notice-count { color: var(--text-muted); }
.type-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin: 0 2px;
}
.notice-action {
  font-size: 0.8rem;
  color: var(--blue);
  text-decoration: none;
  font-weight: 500;
  white-space: nowrap;
}
.notice-action:hover { text-decoration: underline; }
.notice-select {
  font-size: 0.75rem;
  padding: 3px 6px;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-muted);
  border-radius: 6px;
}
.notice-dismiss {
  display: flex;
  align-items: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
  cursor: pointer;
}
.notice-dismiss:hover { color: var(--text); background: var(--bg); }
</style>
