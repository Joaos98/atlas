import { defineStore } from 'pinia'
import { ref } from 'vue'

let id = 0

export const useToastStore = defineStore('toast', () => {
  const toasts = ref([])

  function add(message, type = 'success', duration = 4000) {
    const toast = { id: ++id, message, type }
    toasts.value.push(toast)
    if (duration > 0) {
      setTimeout(() => remove(toast.id), duration)
    }
    return toast.id
  }

  function remove(id) {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }

  function success(msg, duration) { return add(msg, 'success', duration) }
  function error(msg, duration) { return add(msg, 'error', duration) }

  return { toasts, add, remove, success, error }
})
