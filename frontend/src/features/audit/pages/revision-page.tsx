import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { searchRevisions } from '@/features/audit/api'
import type { AuditAction, AuditEntityType, Revision } from '@/features/audit/types'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { Pager, TableCard, TableMessage } from '@/shared/components/table-card'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { formatMoment } from '@/shared/lib/format-moment'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

const ENTITY_TYPES: (AuditEntityType | 'ALL')[] = [
  'ALL',
  'PERSON',
  'FAMILY',
  'EVENT',
  'BRANCH',
  'PLACE',
  'SOURCE',
  'CITATION',
  'GRAVE',
  'MEDIA',
  'SUGGESTION',
  'MEMBER',
]
const ACTIONS: (AuditAction | 'ALL')[] = ['ALL', 'CREATE', 'UPDATE', 'DELETE']

/**
 * The whole-clan change history: every recorded write, newest first, filterable and paginated (CLAUDE.md §3.8).
 *
 * @returns the page element
 */
export function RevisionPage() {
  const { t } = useTranslation()
  const { mayManageMembers } = usePermissions()
  // An account's history carries its email, so the server shows it to the trưởng tộc alone.
  const entityTypes = ENTITY_TYPES.filter((value) => value !== 'MEMBER' || mayManageMembers)
  const [entityType, setEntityType] = useState<AuditEntityType | 'ALL'>('ALL')
  const [action, setAction] = useState<AuditAction | 'ALL'>('ALL')
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['revisions', 'search', entityType, action, page],
    queryFn: () =>
      searchRevisions(entityType === 'ALL' ? undefined : entityType, action === 'ALL' ? undefined : action, page),
    placeholderData: keepPreviousData,
  })

  const revisions = data?.content ?? []
  const totalPages = data?.page?.totalPages ?? 0

  const changeFilter = (next: () => void) => {
    next()
    setPage(0)
  }

  return (
    <PageContainer>
      <PageHeader title={t('audit.pageTitle')} description={t('audit.pageSubtitle')} />

      <TableCard
        toolbar={
          <>
            <Select
              value={entityType}
              onValueChange={(next) => changeFilter(() => setEntityType(next as AuditEntityType | 'ALL'))}
            >
              <SelectTrigger className="w-48" aria-label={t('audit.filterByType')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {entityTypes.map((value) => (
                  <SelectItem key={value} value={value}>
                    {value === 'ALL' ? t('audit.allTypes') : t(`audit.entityTypes.${value}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              value={action}
              onValueChange={(next) => changeFilter(() => setAction(next as AuditAction | 'ALL'))}
            >
              <SelectTrigger className="w-48" aria-label={t('audit.filterByAction')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ACTIONS.map((value) => (
                  <SelectItem key={value} value={value}>
                    {value === 'ALL' ? t('audit.allActions') : t(`audit.actions.${value}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </>
        }
        footer={
          totalPages > 1 && (
            <Pager page={page} totalPages={totalPages} onChange={setPage} label={t('audit.pageTitle')} />
          )
        }
      >
        {isLoading && <TableMessage>{t('common.loading')}</TableMessage>}
        {isError && <TableMessage tone="error">{problemMessage(error, t('common.unexpectedError'))}</TableMessage>}
        {!isLoading && !isError && revisions.length === 0 && <TableMessage>{t('audit.empty')}</TableMessage>}

        {revisions.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('audit.when')}</TableHead>
                <TableHead>{t('audit.what')}</TableHead>
                <TableHead>{t('audit.actionColumn')}</TableHead>
                <TableHead>{t('audit.who')}</TableHead>
                <TableHead>{t('audit.why')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {revisions.map((revision) => (
                <RevisionRow key={revision.id} revision={revision} />
              ))}
            </TableBody>
          </Table>
        )}
      </TableCard>
    </PageContainer>
  )
}

/**
 * One row of the whole-clan change history.
 *
 * @param props the revision to render
 * @returns the table row
 */
function RevisionRow({ revision }: { revision: Revision }) {
  const { t } = useTranslation()
  return (
    <TableRow>
      <TableCell className="whitespace-nowrap">{formatMoment(revision.changedAt)}</TableCell>
      <TableCell>
        {t(`audit.entityTypes.${revision.entityType}`)} #{revision.entityId}
      </TableCell>
      <TableCell>
        <Badge variant={revision.action === 'DELETE' ? 'destructive' : 'outline'}>
          {t(`audit.actions.${revision.action}`)}
        </Badge>
      </TableCell>
      <TableCell>{revision.changedByName ?? t('audit.unknownMember')}</TableCell>
      <TableCell className="text-muted-foreground">{revision.note || '—'}</TableCell>
    </TableRow>
  )
}
