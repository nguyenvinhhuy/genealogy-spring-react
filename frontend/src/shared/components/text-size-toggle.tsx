import { ALargeSmall } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { IconTooltip } from '@/shared/components/icon-tooltip'
import { setTextSize, useTextSize } from '@/shared/lib/text-size'
import { Button } from '@/shared/ui/button'

/**
 * Switches the app's text between its normal size and a larger one.
 *
 * @returns the toggle button
 */
export function TextSizeToggle() {
  // For the older members a gia phả is mostly read by; browser zoom is a setting few of them know (D7).
  const { t } = useTranslation()
  const size = useTextSize()
  const label = t(size === 'large' ? 'textSize.toNormal' : 'textSize.toLarge')

  return (
    <IconTooltip label={label}>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={label}
        aria-pressed={size === 'large'}
        onClick={() => setTextSize(size === 'large' ? 'normal' : 'large')}
      >
        <ALargeSmall aria-hidden />
      </Button>
    </IconTooltip>
  )
}
