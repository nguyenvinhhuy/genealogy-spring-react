import { useSyncExternalStore } from 'react'

// Below this width the sidebar becomes an off-canvas sheet, as in the shadcn template.
const MOBILE_BREAKPOINT = 768
const QUERY = `(max-width: ${MOBILE_BREAKPOINT - 1}px)`

/**
 * Subscribes to changes of the mobile media query.
 *
 * @param onChange called whenever the query flips
 * @returns the unsubscribe function
 */
function subscribe(onChange: () => void): () => void {
  const mql = window.matchMedia(QUERY)
  mql.addEventListener('change', onChange)
  return () => mql.removeEventListener('change', onChange)
}

/**
 * Reports whether the viewport is narrower than the mobile breakpoint, following resizes.
 *
 * @returns true on a phone-width viewport
 */
export function useIsMobile(): boolean {
  // An external store, not state set in an effect: the template's version rendered twice on every mount.
  return useSyncExternalStore(subscribe, () => window.matchMedia(QUERY).matches)
}
