import { createContext, useContext } from 'react'

/** The theme a viewer chose; `system` follows the operating system. */
export type Theme = 'light' | 'dark' | 'system'

/** What {@link useTheme} hands out. */
export interface ThemeState {
  // The theme actually showing, with `system` resolved.
  resolvedTheme: 'light' | 'dark'
  setTheme: (theme: Theme) => void
}

export const ThemeContext = createContext<ThemeState | null>(null)

/**
 * Reads the current theme and the setter for it.
 *
 * @returns the theme state
 */
export function useTheme(): ThemeState {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used inside a ThemeProvider')
  }
  return context
}
