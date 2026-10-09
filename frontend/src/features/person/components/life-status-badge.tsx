import { useTranslation } from 'react-i18next'

import { Badge } from '@/shared/ui/badge'

/** Props of {@link LifeStatusBadge}. */
interface LifeStatusBadgeProps {
  living: boolean
  deathRecorded: boolean
}

/**
 * Says what the gia phả actually records about whether a person has died.
 *
 * @param props the person's living flag and whether a death or burial is recorded
 * @returns the badge element
 */
export function LifeStatusBadge({ living, deathRecorded }: LifeStatusBadgeProps) {
  // `living` is the privacy flag: "Còn sống" for a thuỷ tổ with no dates was a claim the data never made.
  const { t } = useTranslation()
  if (deathRecorded) {
    return <Badge variant="secondary">{t('person.lifeStatus.dead')}</Badge>
  }
  if (!living) {
    return <Badge variant="secondary">{t('person.lifeStatus.presumedDead')}</Badge>
  }
  return <Badge variant="outline">{t('person.lifeStatus.unrecorded')}</Badge>
}
