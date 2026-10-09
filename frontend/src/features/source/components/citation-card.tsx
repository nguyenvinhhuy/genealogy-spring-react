import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { MediaDialog } from '@/features/media/components/media-card'
import { fetchCitations, invalidateCitationQueries, removeCitation } from '@/features/source/api'
import { CitationDialog } from '@/features/source/components/citation-dialog'
import type { Citation, CitationTargetType } from '@/features/source/types'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

/** The record a set of citations backs up. */
export interface CitationTarget {
  targetType: CitationTargetType
  targetId: number
}

/**
 * Lists the citations backing up one record, with the add, edit and remove the caller may use.
 *
 * @param props which record the citations belong to
 * @returns the list element
 */
export function CitationList({ targetType, targetId }: CitationTarget) {
  const { t } = useTranslation()
  const { mayEdit } = usePermissions()

  const { data: citations = [], isLoading, isError, error } = useQuery({
    queryKey: ['citations', targetType, targetId],
    queryFn: () => fetchCitations(targetType, targetId),
  })

  return (
    <div className="space-y-3">
      {isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}
      {/* Its own state: a failed read shown as "no citations" invited someone to enter them all again (§8.8 #22). */}
      {isError && <p className="text-destructive text-sm">{problemMessage(error, t('common.unexpectedError'))}</p>}
      {!isLoading && !isError && citations.length === 0 && (
        <p className="text-muted-foreground text-sm">{t('source.noCitations')}</p>
      )}
      {citations.map((citation) => (
        <CitationRow key={citation.id} citation={citation} />
      ))}
      {mayEdit && !isError && <CitationDialog targetType={targetType} targetId={targetId} />}
    </div>
  )
}

/** Props of {@link CitationRow}. */
interface CitationRowProps {
  citation: Citation
}

/**
 * Renders one citation with its source, locator and quote, and the actions the caller may take on it.
 *
 * @param props the citation
 * @returns the row element
 */
function CitationRow({ citation }: CitationRowProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { mayEdit, mayDelete } = usePermissions()

  const removal = useMutation({
    mutationFn: (changeNote: string) => removeCitation(citation.id, changeNote),
    onSuccess: async () => {
      await invalidateCitationQueries(queryClient, citation.targetType, citation.targetId)
      toast.success(t('source.citationRemoved'))
    },
  })

  return (
    <div className="flex items-start justify-between gap-3 text-sm">
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          {citation.sourceType && <Badge variant="outline">{t(`source.types.${citation.sourceType}`)}</Badge>}
          <span className="font-medium">{citation.sourceTitle}</span>
          {citation.locator && <span className="text-muted-foreground">— {citation.locator}</span>}
        </div>
        {citation.quote && (
          <p className="text-muted-foreground border-l-2 pl-3 whitespace-pre-line italic">“{citation.quote}”</p>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-1">
        {/* Every role: the scan of the old gia phả is what the citation rests on, and the server decides (§8.12 D3). */}
        <MediaDialog targetType="SOURCE" targetId={citation.sourceId} title={citation.sourceTitle ?? ''} />
        {mayEdit && (
          <CitationDialog targetType={citation.targetType} targetId={citation.targetId} citation={citation} />
        )}
        {/* ADMIN only, like every delete, and it asks why: a removed citation is evidence gone (§3.8). */}
        {mayDelete && (
          <ConfirmDeleteDialog
            label={t('common.remove')}
            title={t('source.removeCitationTitle', { title: citation.sourceTitle ?? '' })}
            description={t('source.removeCitationDescription')}
            pending={removal.isPending}
            onConfirm={(note) => removal.mutateAsync(note)}
          />
        )}
      </div>
    </div>
  )
}

/**
 * Shows the citations backing up one record as a card of its own, for a person or a grave.
 *
 * @param props which record the citations belong to
 * @returns the card element
 */
export function CitationCard({ targetType, targetId }: CitationTarget) {
  const { t } = useTranslation()
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('source.citations')}</CardTitle>
      </CardHeader>
      <CardContent>
        <CitationList targetType={targetType} targetId={targetId} />
      </CardContent>
    </Card>
  )
}

/**
 * A "Dẫn chứng" toggle for a row inside another list, such as one event or one union, fetched only when opened.
 *
 * @param props which record the citations belong to
 * @returns the toggle and, when open, the list
 */
export function CitationToggle({ targetType, targetId }: CitationTarget) {
  // Fetched on open, not with the row: a page of twenty events would otherwise be twenty requests up front.
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  // Styled exactly like RevisionToggle, which sits right below it, so the two read as one row of controls.
  return (
    <div className="space-y-2">
      <Button
        variant="ghost"
        size="sm"
        className="-ml-2"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <BookOpen aria-hidden />
        {open ? t('source.hideCitations') : t('source.citations')}
      </Button>
      {open && (
        <div className="rounded-md border border-dashed p-3">
          <CitationList targetType={targetType} targetId={targetId} />
        </div>
      )}
    </div>
  )
}
