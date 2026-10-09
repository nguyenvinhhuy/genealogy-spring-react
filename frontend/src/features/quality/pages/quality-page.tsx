import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { MergeDialog } from '@/features/merge/components/merge-dialog'
import { fetchQualityIssues } from '@/features/quality/api'
import type { IssueSeverity, QualityIssue } from '@/features/quality/types'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'
import { Card, CardContent } from '@/shared/ui/card'

// Every write that changes a finding invalidates the list, so returning to the tab need not re-run every rule.
const STALE_MS = 60_000

// Badge styling per severity; ERROR and WARNING must not look alike.
const SEVERITY_VARIANT: Record<IssueSeverity, 'destructive' | 'default' | 'secondary'> = {
  ERROR: 'destructive',
  WARNING: 'default',
  INFO: 'secondary',
}

/**
 * Lists data-quality findings across the whole gia phả.
 *
 * @returns the page element
 */
export function QualityPage() {
  // Warnings, never blocking validation (§5.1): the page lets a reader judge, it does not refuse a save.
  const { t } = useTranslation()

  const { data = [], isLoading, isError, error } = useQuery({
    queryKey: ['quality', 'issues'],
    queryFn: fetchQualityIssues,
    staleTime: STALE_MS,
  })
  // A check that never ran must not read as a clean gia phả, so failure gets its own state.
  const clean = !isLoading && !isError && data.length === 0

  const counts = data.reduce<Record<string, number>>((totals, issue) => {
    totals[issue.severity] = (totals[issue.severity] ?? 0) + 1
    return totals
  }, {})

  return (
    <PageContainer width="narrow">
      <PageHeader
        title={t('quality.title')}
        description={t('quality.subtitle')}
        badges={
          !isLoading &&
          data.length > 0 &&
          (['ERROR', 'WARNING', 'INFO'] as IssueSeverity[])
            .filter((severity) => counts[severity])
            .map((severity) => (
              <Badge key={severity} variant={SEVERITY_VARIANT[severity]}>
                {t('quality.severityCount', { label: t(`quality.severities.${severity}`), n: counts[severity] })}
              </Badge>
            ))
        }
      />

      {isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}

      {isError && (
        <p className="text-destructive rounded-md border border-dashed px-3 py-6 text-center text-sm">
          {problemMessage(error, t('common.unexpectedError'))}
        </p>
      )}

      {clean && (
        <p className="text-muted-foreground rounded-md border border-dashed px-3 py-6 text-center text-sm">
          {t('quality.clean')}
        </p>
      )}

      <div className="space-y-3">
        {data.map((issue, index) => (
          <IssueCard key={`${issue.code}-${issue.personId}-${index}`} issue={issue} />
        ))}
      </div>

    </PageContainer>
  )
}

/**
 * Renders one finding, with its message built from the code and its numbers.
 *
 * @param props the finding to render
 * @returns the card element
 */
function IssueCard({ issue }: { issue: QualityIssue }) {
  const { t } = useTranslation()
  const { mayDelete } = usePermissions()
  const mergeable =
    issue.code === 'POSSIBLE_DUPLICATE' && issue.relatedPersonId != null && issue.relatedPersonName

  return (
    <Card>
      <CardContent className="flex flex-wrap items-start justify-between gap-3 py-4">
        <div className="space-y-1">
          <Link
            className="font-medium underline-offset-4 hover:underline"
            to={`/persons/${issue.personId}`}
          >
            {issue.personName}
          </Link>
          <p className="text-muted-foreground text-sm">
            {t(`quality.codes.${issue.code}`, {
              ...issue.params,
              type: issue.params.type == null ? '' : t(`event.types.${issue.params.type}`).toLowerCase(),
              related: issue.relatedPersonName ?? '',
            })}
          </p>
          {issue.relatedPersonId != null && (
            <Link
              className="text-muted-foreground text-xs underline-offset-4 hover:underline"
              to={`/persons/${issue.relatedPersonId}`}
            >
              {issue.relatedPersonName}
            </Link>
          )}
        </div>
        <div className="flex items-center gap-2">
          {/* A merge deletes a person, so it is offered only to the role allowed to delete one. */}
          {mergeable && mayDelete && (
            <MergeDialog
              personId={issue.personId}
              personName={issue.personName}
              relatedPersonId={issue.relatedPersonId as number}
              relatedPersonName={issue.relatedPersonName as string}
            />
          )}
          <Badge variant={SEVERITY_VARIANT[issue.severity]}>
            {t(`quality.severities.${issue.severity}`)}
          </Badge>
        </div>
      </CardContent>
    </Card>
  )
}
