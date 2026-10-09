import { useCallback, useLayoutEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from 'react'

import { ThemeContext, type Theme, type ThemeState } from '@/shared/lib/theme'

const STORAGE_KEY = 'genealogy.theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

/**
 * Reads the theme this browser last chose.
 *
 * @returns the stored theme, or `system`
 */
function storedTheme(): Theme {
  // Storage can throw in a private window or with site data blocked; following the system is always safe.
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}

/**
 * Subscribes to the operating system switching between light and dark.
 *
 * @param onChange called when it switches
 * @returns the unsubscribe function
 */
function subscribeToSystem(onChange: () => void): () => void {
  const mql = window.matchMedia(DARK_QUERY)
  mql.addEventListener('change', onChange)
  return () => mql.removeEventListener('change', onChange)
}

/**
 * Keeps the `dark` class on the page in step with the viewer's choice, after the template's theme provider.
 *
 * @param props the app
 * @returns the app inside the theme context
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  // Written by hand rather than next-themes, whose inline script React 19 reports as an error on every load.
  const [theme, setThemeState] = useState<Theme>(storedTheme)
  const systemDark = useSyncExternalStore(subscribeToSystem, () => window.matchMedia(DARK_QUERY).matches)
  const resolvedTheme = theme === 'system' ? (systemDark ? 'dark' : 'light') : theme

  // A layout effect, so a dark-mode reader never sees one light frame before the class lands.
  useLayoutEffect(() => {
    document.documentElement.classList.toggle('dark', resolvedTheme === 'dark')
  }, [resolvedTheme])

  const setTheme = useCallback((next: Theme) => {
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // A choice that cannot be stored still applies for this visit.
    }
    setThemeState(next)
  }, [])

  const value = useMemo<ThemeState>(() => ({ resolvedTheme, setTheme }), [resolvedTheme, setTheme])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
