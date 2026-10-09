import type { ReactElement } from 'react'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/shared/ui/tooltip'

/** Props of {@link IconTooltip}. */
interface IconTooltipProps {
  label: string
  children: ReactElement
}

/**
 * Wraps an icon-only control in the shared tooltip that names it.
 *
 * @param props the label and the control it names
 * @returns the control with its tooltip
 */
export function IconTooltip({ label, children }: IconTooltipProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  )
}
