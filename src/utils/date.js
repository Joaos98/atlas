// Date helpers that avoid UTC-parsing off-by-one shifts (user is UTC-3).
// Always derive local date strings instead of using Date#toISOString().

export function toLocalDateStr(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function todayLocal() {
  return toLocalDateStr(new Date())
}

// "2026-07-14" -> "14/07/2026" (no Date construction, so no UTC shift)
export function formatDateBr(dateString) {
  if (!dateString) return ''
  const [year, month, day] = dateString.split('-')
  return `${day}/${month}/${year}`
}
