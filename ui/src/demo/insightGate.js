// Demo-only: gates the insight regenerate action with a warning modal, so the
// seeded insight is never replaced and no success toast fires. The message
// explains that generation requires self-hosting.

import { ref } from 'vue'

export const insightGateOpen = ref(false)

export function openInsightGate() {
  insightGateOpen.value = true
}

export function closeInsightGate() {
  insightGateOpen.value = false
}
