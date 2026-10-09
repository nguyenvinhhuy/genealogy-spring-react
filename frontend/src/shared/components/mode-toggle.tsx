import { Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { IconTooltip } from '@/shared/components/icon-tooltip'
import { useCircularTransition } from '@/shared/hooks/use-circular-transition'
import { Button } from '@/shared/ui/button'

/**
 * Switches between the light and dark gia phả themes.
 *
 * @returns the toggle button
 */
export function ModeToggle() {
  const { t } = useTranslation()
  const { isDark, toggleTheme } = useCircularTransition()
  const label = t(isDark ? 'theme.toLight' : 'theme.toDark')

  return (
    <IconTooltip label={label}>
      <Button variant="ghost" size="icon-sm" aria-label={label} onClick={toggleTheme}>
        {/* The icon shows the theme a click switches to, as in the template. */}
        {isDark ? <Sun aria-hidden /> : <Moon aria-hidden />}
      </Button>
    </IconTooltip>
  )
}
