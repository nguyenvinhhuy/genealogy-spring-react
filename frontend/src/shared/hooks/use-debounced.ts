import { useEffect, useState } from 'react'

// Long enough to skip the keystrokes of a name being typed, short enough to feel like it answers the typing.
export const TYPING_DEBOUNCE_MS = 300

/**
 * Returns a value that only catches up after it has stopped changing for a while.
 *
 * @param value the value to follow
 * @param delayMs how long it must hold still first
 * @returns the settled value
 */
export function useDebounced<T>(value: T, delayMs: number): T {
  // Typing "Nguyễn Văn An" fired thirteen uncancelled searches, so an early reply could land last.
  const [settled, setSettled] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return settled
}
