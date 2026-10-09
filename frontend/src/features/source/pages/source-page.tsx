import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { MediaDialog } from '@/features/media/components/media-card'
import { deleteSource, invalidateCitationQueries, searchSources, SOURCE_QUERY_KEY } from '@/features/source/api'
import { SourceDialog } from '@/features/source/components/source-dialog'
import { SourceMergeDialog } from '@/features/source/components/source-merge-dialog'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { SearchInput } from '@/shared/components/search-input'
import { Pager, TableCard, TableMessage } from '@/shared/components/table-card'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

const PAGE_SIZE = 20

/**
 * Lists every source by title, searchable, with edit, merge and delete for those allowed to (EDITOR+).
 *
 * @returns the page element
 */
export function SourcePage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { mayEdit, mayDelete } = usePermissions()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const settled = useDebounced(query.trim(), TYPING_DEBOUNCE_MS)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [...SOURCE_QUERY_KEY, 'page', settled, page],
    queryFn: () => searchSources(settled, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
  const sources = data?.content ?? []
  const totalPages = data?.page.totalPages ?? 0

  const removal = useMutation({
    mutationFn: ({ id, changeNote }: { id: number; changeNote: string }) => deleteSource(id, changeNote),
    onSuccess: async () => {
      await invalidateCitationQueries(queryClient, null)
      toast.success(t('source.deleted'))
    },
  })

  return (
    <PageContainer>
      <PageHeader
        title={t('source.pageTitle')}
        description={t('source.pageSubtitle')}
        actions={mayEdit && <SourceDialog />}
      />

      <TableCard
        toolbar={
          <SearchInput
            value={query}
            placeholder={t('source.searchPlaceholder')}
            aria-label={t('source.search')}
            onChange={(event) => {
              setQuery(event.target.value)
              setPage(0)
            }}
          />
        }
        footer={
          totalPages > 1 && (
            <Pager page={page} totalPages={totalPages} onChange={setPage} label={t('source.pageTitle')} />
          )
        }
      >
        {isLoading && <TableMessage>{t('common.loading')}</TableMessage>}
        {isError && <TableMessage tone="error">{problemMessage(error, t('common.unexpectedError'))}</TableMessage>}
        {!isLoading && !isError && sources.length === 0 && <TableMessage>{t('source.empty')}</TableMessage>}

        {sources.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('source.title')}</TableHead>
                <TableHead>{t('source.type')}</TableHead>
                <TableHead>{t('source.author')}</TableHead>
                <TableHead>{t('source.dateText')}</TableHead>
                <TableHead className="text-right">{t('source.citationCount')}</TableHead>
                {(mayEdit || mayDelete) && <TableHead className="w-0">{t('common.actions')}</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {sources.map((source) => (
                <TableRow key={source.id}>
                  <TableCell className="font-heading font-medium">{source.title}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{t(`source.types.${source.type}`)}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{source.author ?? '—'}</TableCell>
                  <TableCell className="text-muted-foreground">{source.dateText ?? '—'}</TableCell>
                  <TableCell className="text-right tabular-nums">{source.citationCount}</TableCell>
                  {(mayEdit || mayDelete) && (
                    <TableCell>
                      <div className="flex items-center gap-1">
                        <MediaDialog targetType="SOURCE" targetId={source.id} title={source.title} />
                        {mayEdit && <SourceDialog source={source} />}
                        {mayDelete && <SourceMergeDialog source={source} />}
                        {/* Offered only when nothing cites it: a cited source is merged, never deleted (§8.8). */}
                        {mayDelete && source.citationCount === 0 && (
                          <ConfirmDeleteDialog
                            label={t('common.delete')}
                            title={t('source.deleteTitle', { title: source.title })}
                            description={t('source.deleteDescription')}
                            pending={removal.isPending}
                            onConfirm={(changeNote) => removal.mutateAsync({ id: source.id, changeNote })}
                          />
                        )}
                      </div>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TableCard>
    </PageContainer>
  )
}
