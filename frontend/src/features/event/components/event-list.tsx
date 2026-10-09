import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { RevisionToggle } from '@/features/audit/components/revision-list'
import { deleteEvent, fetchEvents } from '@/features/event/api'
import { EventDialog } from '@/features/event/components/event-dialog'
import { invalidateAfterEventChange } from '@/features/event/lib/invalidate'
import type { EventSubjectType, GenealogyEvent } from '@/features/event/types'
import { PlaceName } from '@/features/place/components/place-name'
import { CitationToggle } from '@/features/source/components/citation-card'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'

/** Props of {@link EventList}. */
interface EventListProps {
  subjectType: EventSubjectType
  subjectId: number
  // Shown when there is nothing to list; the caller knows whether that means "none" or "hidden".
  emptyText: string
}

/**
 * Lists the events of a person or a union, with edit and delete for those allowed to.
 *
 * @param props the subject and the text to show when it has no events
 * @returns the list element
 */
export function EventList({ subjectType, subjectId, emptyText }: EventListProps) {
  const { t } = useTranslation()
  const { mayEdit, mayDelete, mayAudit } = usePermissions()

  const { data: events = [], isError, error } = useQuery({
    queryKey: ['events', subjectType, subjectId],
    queryFn: () => fetchEvents(subjectType, subjectId),
  })

  if (isError) {
    return <p className="text-destructive text-sm">{problemMessage(error, t('common.unexpectedError'))}</p>
  }
  if (events.length === 0) {
    return <p className="text-muted-foreground text-sm">{emptyText}</p>
  }
  return (
    <ul className="divide-y">
      {events.map((item) => (
        <EventRow key={item.id} event={item} mayEdit={mayEdit} mayDelete={mayDelete} mayAudit={mayAudit} />
      ))}
    </ul>
  )
}

/** Props of {@link EventRow}. */
interface EventRowProps {
  event: GenealogyEvent
  mayEdit: boolean
  mayDelete: boolean
  mayAudit: boolean
}

/**
 * Renders one event with its date, place and note, and the actions the caller may take on it.
 *
 * @param props the event and what the caller may do
 * @returns the row element
 */
function EventRow({ event, mayEdit, mayDelete, mayAudit }: EventRowProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const removal = useMutation({
    mutationFn: (changeNote: string) => deleteEvent(event.id, changeNote),
    onSuccess: async () => {
      await invalidateAfterEventChange(queryClient, event.subjectType, event.subjectId)
      toast.success(t('event.deleted'))
    },
  })

  return (
    <li className="flex items-start justify-between gap-3 py-2">
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Badge variant="outline">{t(`event.types.${event.type}`)}</Badge>
          {/* Rendered server-side: a second renderer here is how the book and the screen drifted apart. */}
          <span>{event.date?.display ?? t('event.noDate')}</span>
          {event.placeId != null && (
            <span className="text-muted-foreground">
              · <PlaceName id={event.placeId} />
            </span>
          )}
        </div>
        {event.description && <p className="text-muted-foreground text-sm whitespace-pre-line">{event.description}</p>}
        {/* On what basis a ngày mất was written is asked of the event itself, not of the whole person (§8.8 #14). */}
        <CitationToggle targetType="EVENT" targetId={event.id} />
        {/* "Who changed cụ's ngày mất, and why" is the question §3.8 exists to answer, so it is asked per event. */}
        {mayAudit && <RevisionToggle entityType="EVENT" entityId={event.id} />}
      </div>
      {(mayEdit || mayDelete) && (
        <div className="flex shrink-0 items-center gap-1">
          {mayEdit && <EventDialog subjectType={event.subjectType} subjectId={event.subjectId} event={event} />}
          {mayDelete && (
            <ConfirmDeleteDialog
              label={t('common.delete')}
              title={t('event.deleteTitle', { type: t(`event.types.${event.type}`) })}
              description={t('event.deleteDescription')}
              pending={removal.isPending}
              onConfirm={(note) => removal.mutateAsync(note)}
            />
          )}
        </div>
      )}
    </li>
  )
}
