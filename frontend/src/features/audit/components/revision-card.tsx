import { useTranslation } from 'react-i18next'

import { RevisionList, type RevisionTarget } from '@/features/audit/components/revision-list'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

/**
 * Shows who changed this record, when, and on what basis, as a card of its own (CLAUDE.md §3.8).
 *
 * @param props which record the history belongs to
 * @returns the card element
 */
export function RevisionCard({ entityType, entityId }: RevisionTarget) {
  const { t } = useTranslation()
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('audit.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        <RevisionList entityType={entityType} entityId={entityId} />
      </CardContent>
    </Card>
  )
}
