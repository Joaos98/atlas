<template>
  <div v-if="loading" class="page">
    <h1>Settings</h1>
    <section>
      <h2>Workout target</h2>
      <div class="card card-fit">
        <SkeletonLoader height="2.5rem" width="280px" />
      </div>
    </section>
    <section>
      <h2>Workout types</h2>
      <div class="card card-fit">
        <SkeletonLoader height="2.5rem" width="320px" />
      </div>
    </section>
    <section>
      <h2>Health Connect mappings</h2>
      <div class="card card-fit">
        <SkeletonLoader height="2rem" width="300px" />
        <SkeletonLoader height="2rem" width="220px" />
      </div>
    </section>
  </div>
  <div v-else class="page">
    <h1>Settings</h1>

    <section>
      <h2>Workout target</h2>
      <div class="card card-fit">
        <form class="form-row" @submit.prevent="saveTarget">
          <div class="form-field">
            <label><Target :size="14" /> Target workouts per week</label>
            <input v-model.number="target" type="number" min="1" max="7" required />
          </div>
          <div class="form-actions">
            <button type="submit" class="btn-primary">Save</button>
          </div>
        </form>
        <p v-if="targetMessage" class="form-success">{{ targetMessage }}</p>
        <p v-if="targetError" class="form-error">{{ targetError }}</p>
      </div>
    </section>

    <section>
      <h2>Workout types</h2>
      <div class="card card-fit">
        <div class="types-list">
          <span v-for="type in types" :key="type.id" class="type-tag">
            <span class="type-dot" :style="{ backgroundColor: type.colorHex }"></span>
            {{ type.name }}
            <button class="btn-icon" title="Delete type" @click="deleteType(type)"><X :size="12" /></button>
          </span>
          <span v-if="types.length === 0" class="empty-line">No types yet.</span>
        </div>
        <div v-if="types.length < PALETTE.length" class="type-add">
          <div class="color-picker">
            <button
              v-for="(hex, i) in PALETTE"
              :key="i"
              type="button"
              class="color-dot"
              :class="{ selected: newTypeColor === hex }"
              :style="{ backgroundColor: hex }"
              @click="newTypeColor = hex"
            ></button>
          </div>
          <input v-model="newTypeName" placeholder="Type name" class="type-input" />
          <button class="btn-small" @click="addType">Add</button>
        </div>
        <p v-else class="muted-note">All 5 types in use.</p>
        <p v-if="typeError" class="form-error">{{ typeError }}</p>
      </div>
    </section>

    <section>
      <h2>Health Connect mappings</h2>
      <div class="card card-fit">
        <p class="section-desc">
          Map Health Connect exercise type codes to your workout types so auto-synced workouts are logged correctly.
          Unmapped types will be skipped.
        </p>
        <table v-if="mappings.length" class="mappings-table">
          <thead>
            <tr>
              <th>Health Connect type</th>
              <th>Workout type</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in mappings" :key="m.healthConnectType">
              <td class="data-value">{{ m.healthConnectType }}</td>
              <td>
                <span v-if="m.workoutType" class="type-dot" :style="{ backgroundColor: m.workoutType.colorHex }"></span>
                {{ m.workoutType?.name }}
              </td>
              <td>
                <button class="btn-icon" title="Delete mapping" @click="deleteMappingHandler(m.healthConnectType)"><X :size="12" /></button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-line">No mappings yet.</p>

        <div class="mapping-add">
          <input v-model.number="newMappingType" type="number" min="0" placeholder="HC type code" class="type-input" />
          <select v-model="newMappingWorkoutTypeId" class="type-input">
            <option :value="null" disabled>Select workout type</option>
            <option v-for="type in types" :key="type.id" :value="type.id">{{ type.name }}</option>
          </select>
          <button class="btn-small" @click="addMappingHandler" :disabled="newMappingType === null || newMappingType === '' || !newMappingWorkoutTypeId">Add</button>
        </div>
        <p v-if="mappingError" class="form-error">{{ mappingError }}</p>
      </div>
    </section>
    <section v-if="isDemo">
      <h2>Demo data</h2>
      <div class="card card-fit">
        <p class="section-desc">
          This is a demo — your changes are stored in this browser only. Reset to restore the seeded data.
        </p>
        <button class="btn-small btn-danger" @click="resetDemo">Reset demo data</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import { getSettings, updateSettings } from '../services/settingsService'
import { useToastStore } from '../stores/toast'
import { getWorkoutTypes, createWorkoutType, deleteWorkoutType } from '../services/workoutService'
import { getMappings, addMapping, deleteMapping } from '../services/syncService'
import { Target, X } from 'lucide-vue-next'

const isDemo = import.meta.env.MODE === 'demo'

async function resetDemo() {
  if (!confirm('Reset all demo data to the seeded state?')) return
  const { resetDemoData } = await import('../demo/demoApi')
  resetDemoData()
}

const toast = useToastStore()

const PALETTE = ['#4F8DFF', '#8B5CF6', '#2DD4BF', '#F472B6', '#FACC15']

const loading = ref(true)
const types = ref([])
const mappings = ref([])

const target = ref(4)
const targetMessage = ref('')
const targetError = ref('')

const newTypeName = ref('')
const newTypeColor = ref(PALETTE[0])
const typeError = ref('')

const newMappingType = ref(null)
const newMappingWorkoutTypeId = ref(null)
const mappingError = ref('')

async function load() {
  try {
    const [settingsRes, typesRes, mappingsRes] = await Promise.all([
      getSettings(),
      getWorkoutTypes(),
      getMappings()
    ])
    target.value = settingsRes.data.targetWorkoutsPerWeek
    types.value = typesRes.data
    mappings.value = mappingsRes.data
  } catch {
    targetError.value = 'Failed to load settings'
  }
}

async function saveTarget() {
  targetMessage.value = ''
  targetError.value = ''
  try {
    await updateSettings({ targetWorkoutsPerWeek: target.value })
    targetMessage.value = 'Saved'
    setTimeout(() => { targetMessage.value = '' }, 3000)
  } catch {
    targetError.value = 'Failed to save'
  }
}

async function addType() {
  const name = newTypeName.value.trim()
  if (!name) return
  typeError.value = ''
  try {
    await createWorkoutType({ name, colorHex: newTypeColor.value })
    newTypeName.value = ''
    await load()
    toast.success('Workout type added')
  } catch {
    typeError.value = 'Could not create type.'
  }
}

async function deleteType(type) {
  if (!confirm(`Delete "${type.name}"? This will fail if it has existing workout logs or mappings.`)) return
  typeError.value = ''
  try {
    await deleteWorkoutType(type.id)
    await load()
    toast.success('Workout type deleted')
  } catch {
    typeError.value = `Cannot delete "${type.name}" — it has existing workout logs or mappings.`
  }
}

async function addMappingHandler() {
  mappingError.value = ''
  try {
    await addMapping({ healthConnectType: newMappingType.value, workoutTypeId: newMappingWorkoutTypeId.value })
    newMappingType.value = null
    newMappingWorkoutTypeId.value = null
    await load()
    toast.success('Mapping added')
  } catch {
    mappingError.value = 'Could not add mapping.'
  }
}

async function deleteMappingHandler(healthConnectType) {
  if (!confirm(`Delete mapping for type ${healthConnectType}?`)) return
  mappingError.value = ''
  try {
    await deleteMapping(healthConnectType)
    await load()
    toast.success('Mapping deleted')
  } catch {
    mappingError.value = 'Could not delete mapping.'
  }
}

onMounted(async () => {
  await load()
  loading.value = false
})
</script>

<style scoped>
.form-row {
  align-items: flex-end;
}

.types-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: var(--space-3);
}
.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 5px 8px 5px 12px;
  font-size: 0.85rem;
}
.type-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  vertical-align: middle;
}
.type-add {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.color-picker {
  display: flex;
  gap: 6px;
}
.color-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  transition: border-color 0.15s;
}
.color-dot.selected {
  border-color: var(--text);
}
.color-dot:hover {
  border-color: var(--text-muted);
}
.color-dot.selected:hover {
  border-color: var(--text);
}
.type-input {
  width: 160px;
}

.section-desc {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0 0 var(--space-3);
  max-width: 520px;
}

.mappings-table {
  margin-bottom: var(--space-3);
}
.mappings-table .type-dot {
  margin-right: 6px;
}

.mapping-add {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.btn-small {
  background: var(--blue);
  color: var(--bg);
  border: none;
  padding: 9px 14px;
  font-size: 0.85rem;
  border-radius: 8px;
  cursor: pointer;
}
.btn-small:hover {
  filter: brightness(1.1);
}
.btn-small:disabled {
  opacity: 0.3;
  cursor: default;
}
.btn-icon {
  display: flex;
  align-items: center;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 4px;
  border-radius: 4px;
  cursor: pointer;
}
.btn-icon:hover {
  color: var(--orange);
  background: rgba(251, 146, 60, 0.12);
}
.muted-note {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin: 0;
}
.empty-line {
  color: var(--text-muted);
  font-size: 0.85rem;
}
</style>
