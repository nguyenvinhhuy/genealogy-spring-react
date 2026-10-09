import { useSyncExternalStore } from 'react'

/** How large the app's text is drawn. */
export type TextSize = 'normal' | 'large'

// The same key the pre-paint script in index.html reads, so a large-text reader never sees one small frame.
const TEXT_SIZE_KEY ='genealogy.text-size'

// The class on <html> that index.css scales the root font size by; every rem follows it.
const LARGE_CLASS = 'text-large'

const listeners = new Set<() => void>()

/**
 * Reads the size this browser last chose.
 *
 * @returns the stored size, or `normal`
 */
function readStored(): TextSize {
  // Storage can throw in a private window or with site data blocked; normal text is always safe.
  try {
    return localStorage.getItem(TEXT_SIZE_KEY) === 'large' ? 'large' : 'normal'
  } catch {
    return 'normal'
  }
}

let current: TextSize = readStored()
// Applied again here, for a page where the pre-paint script in index.html did not run.
document.documentElement.classList.toggle(LARGE_CLASS, current === 'large')

/**
 * Subscribes a component to changes of the text size.
 *
 * @param onChange called when the size changes
 * @returns the unsubscribe function
 */
function subscribe(onChange: () => void): () => void {
  listeners.add(onChange)
  return () => listeners.delete(onChange)
}

/**
 * Sets the text size, remembers it for this browser and applies it at once.
 *
 * @param next the size to use
 */
export function setTextSize(next: TextSize): void {
  try {
    localStorage.setItem(TEXT_SIZE_KEY, next)
  } catch {
    // A choice that cannot be stored still applies for this visit.
  }
  current = next
  document.documentElement.classList.toggle(LARGE_CLASS, next === 'large')
  listeners.forEach((listener) => listener())
}

/**
 * Reads the current text size and re-renders when it changes.
 *
 * @returns the size in use
 */
export function useTextSize(): TextSize {
  return useSyncExternalStore(subscribe, () => current)
}
