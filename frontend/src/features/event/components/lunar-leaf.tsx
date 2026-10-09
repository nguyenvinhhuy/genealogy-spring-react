import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'

/** Props of {@link LunarLeaf}. */
interface LunarLeafProps {
  day: number
  month: number
  leapMonth: boolean
  size?: 'sm' | 'md'
}

/**
 * Draws a lunar date as a tear-off calendar leaf: the big day, and the month beneath it.
 *
 * @param props the lunar day and month, whether the month is a tháng nhuận, and how large to draw it
 * @returns the leaf
 */
export function LunarLeaf({ day, month, leapMonth, size = 'md' }: LunarLeafProps) {
  // The lunar day is what the family keeps a giỗ by, so it is the figure that stands out.
  const { t } = useTranslation()
  return (
    <div
      className={cn(
        'border-seal/30 bg-seal/5 flex shrink-0 flex-col items-center rounded-md border',
        size === 'sm' ? 'w-14 py-1' : 'w-16 py-1.5',
      )}
    >
      <span
        className={cn('text-seal font-heading leading-none font-semibold', size === 'sm' ? 'text-xl' : 'text-2xl')}
      >
        {day}
      </span>
      <span className="text-muted-foreground text-[11px]">
        {t(leapMonth ? 'dashboard.leafMonthLeap' : 'dashboard.leafMonth', { month })}
      </span>
    </div>
  )
}
