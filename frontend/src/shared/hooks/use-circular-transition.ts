import { useCallback, type MouseEvent } from 'react'
import { flushSync } from 'react-dom'

import { useTheme } from '@/shared/lib/theme'

/** A document that may support the View Transitions API. */
interface TransitionDocument {
  startViewTransition?: (update: () => void) => unknown
}

/**
 * Switches between light and dark with a circle revealed from where the user clicked.
 *
 * @returns whether the dark theme is showing, and the click handler that toggles it
 */
export function useCircularTransition() {
  const { resolvedTheme, setTheme } = useTheme()
  const isDark = resolvedTheme === 'dark'

  const toggleTheme = useCallback(
    (event: MouseEvent) => {
      // The origin of the reveal is read by the keyframes in index.css, as in the shadcn dashboard template.
      const root = document.documentElement
      root.style.setProperty('--x', `${(event.clientX / window.innerWidth) * 100}%`)
      root.style.setProperty('--y', `${(event.clientY / window.innerHeight) * 100}%`)
      const next = isDark ? 'light' : 'dark'
      const transitions = document as unknown as TransitionDocument
      if (transitions.startViewTransition) {
        // Synchronously, class included: the browser snapshots the new page as soon as this returns (#30).
        transitions.startViewTransition(() => flushSync(() => setTheme(next)))
      } else {
        setTheme(next)
      }
    },
    [isDark, setTheme],
  )

  return { isDark, toggleTheme }
}
