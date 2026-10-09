import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router'

import { fetchFounderId } from '@/features/tree/api'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Button } from '@/shared/ui/button'

/**
 * Opens the tree on the clan's thuỷ tổ, for the sidebar link that names no one.
 *
 * @returns a redirect to the founder's tree, or what to do when there is none yet
 */
export function TreeStartPage() {
  const { t } = useTranslation()
  const { data: founderId, isLoading, isError, error } = useQuery({
    queryKey: ['tree', 'founder'],
    queryFn: fetchFounderId,
  })

  if (founderId != null) {
    return <Navigate to={`/tree/${founderId}`} replace />
  }
  return (
    <PageContainer width="narrow">
      <PageHeader title={t('tree.title')} description={t('tree.startHint')} />
      {isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}
      {isError && <p className="text-destructive text-sm">{problemMessage(error, t('common.unexpectedError'))}</p>}
      {!isLoading && !isError && (
        <div className="space-y-3">
          <p className="text-muted-foreground text-sm">{t('tree.noFounder')}</p>
          <Button asChild variant="outline" size="sm">
            <Link to="/persons">{t('person.title')}</Link>
          </Button>
        </div>
      )}
    </PageContainer>
  )
}
