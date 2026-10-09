import { useTranslation } from 'react-i18next'

import { Badge } from '@/shared/ui/badge'

// Inside this many days the reminder is worth flagging rather than just listing.
const SOON_DAYS = 14

/**
 * Says how far off a giỗ is, standing out once it is close.
 *
 * @param props the days until the next occurrence
 * @returns the badge
 */
export function AnniversaryCountdown({ daysUntil }: { daysUntil: number }) {
  // One rule for the home page and the giỗ page: the two had drifted to different thresholds (#41).
  const { t } = useTranslation()
  return (
    <Badge variant={daysUntil <= SOON_DAYS ? 'default' : 'secondary'}>
      {daysUntil === 0 ? t('anniversary.today') : t('anniversary.inDays', { count: daysUntil })}
    </Badge>
  )
}
