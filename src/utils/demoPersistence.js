const STORAGE_PREFIX = 'xiyu-demo:'

export function loadDemoState(key, fallback) {
  if (typeof window === 'undefined') {
    return fallback
  }

  try {
    const raw = window.localStorage.getItem(`${STORAGE_PREFIX}${key}`)
    return raw ? JSON.parse(raw) : fallback
  } catch (error) {
    console.warn(`Failed to load demo state for ${key}:`, error)
    return fallback
  }
}

export function saveDemoState(key, value) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.setItem(`${STORAGE_PREFIX}${key}`, JSON.stringify(value))
  } catch (error) {
    console.warn(`Failed to save demo state for ${key}:`, error)
  }
}
