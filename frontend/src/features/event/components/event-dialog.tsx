import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { createEvent, updateEvent } from '@/features/event/api'
import { FuzzyDateInput } from '@/features/event/components/fuzzy-date-input'
import { buildEventSchema, EVENT_TYPES_BY_SUBJECT, type EventFormValues } from '@/features/event/lib/event-schema'
import type { EventPayload, EventSubjectType, EventType, GenealogyEvent } from '@/features/event/types'
import { invalidateAfterEventChange } from '@/features/event/lib/invalidate'
import { PlacePicker } from '@/features/place/components/place-picker'
import { useVersionAtOpen } from '@/shared/hooks/use-version-at-open'
import { voidSubmit } from '@/shared/lib/forms'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link EventDialog}. */
interface EventDialogProps {
  subjectType: EventSubjectType
  subjectId: number
  // The event being edited, or undefined to add a new one.
  event?: GenealogyEvent
}

/**
 * Adds or edits one event of a person or a union, with a fuzzy date the family can type as they know it.
 *
 * @param props the subject the event belongs to, and the event when editing
 * @returns the dialog with its trigger button
 */
export function EventDialog({ subjectType, subjectId, event }: EventDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const version = useVersionAtOpen(event?.version, open)
  const schema = useMemo(() => buildEventSchema(t), [t])
  const types = EVENT_TYPES_BY_SUBJECT[subjectType]

  const defaults: EventFormValues = {
    type: event?.type ?? types[0],
    date: (event?.date as Record<string, unknown> | null) ?? null,
    placeId: event?.placeId ?? null,
    description: event?.description ?? '',
    changeNote: '',
  }
  const { control, register, handleSubmit, reset } = useForm<EventFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })

  const mutation = useMutation({
    mutationFn: (values: EventFormValues) => {
      const payload: EventPayload = {
        subjectId,
        type: values.type as EventType,
        date: values.date,
        placeId: values.placeId,
        description: values.description.trim() || null,
        changeNote: values.changeNote.trim() || null,
      }
      return event ? updateEvent(event.id, { ...payload, version }) : createEvent(payload)
    },
    onSuccess: async () => {
      await invalidateAfterEventChange(queryClient, subjectType, subjectId)
      toast.success(t(event ? 'event.updated' : 'event.created'))
      setOpen(false)
    },
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        // Reopened fresh each time: a half-typed edit from last time is not what the family expects to see.
        if (next) {
          reset(defaults)
        }
      }}
    >
      <DialogTrigger asChild>
        {event ? (
          <Button variant="ghost" size="sm">
            <Pencil aria-hidden />
            {t('common.edit')}
          </Button>
        ) : (
          <Button variant="outline" size="sm">
            <Plus aria-hidden />
            {t('event.add')}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(event ? 'event.editTitle' : 'event.addTitle')}</DialogTitle>
        </DialogHeader>

        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor="event-type">{t('event.type')}</Label>
            <Controller
              control={control}
              name="type"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="event-type" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {types.map((type) => (
                      <SelectItem key={type} value={type}>
                        {t(`event.types.${type}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <Controller
            control={control}
            name="date"
            render={({ field }) => (
              <FuzzyDateInput
                id="event-date"
                label={t('event.date')}
                value={field.value}
                onChange={field.onChange}
              />
            )}
          />

          <div className="space-y-2">
            <Label htmlFor="event-place">{t('event.place')}</Label>
            <Controller
              control={control}
              name="placeId"
              render={({ field }) => <PlacePicker id="event-place" value={field.value} onChange={field.onChange} />}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="event-description">{t('event.description')}</Label>
            <Textarea id="event-description" {...register('description')} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="event-change-note">{t('common.changeNote')}</Label>
            <Textarea
              id="event-change-note"
              placeholder={t('common.changeNotePlaceholder')}
              {...register('changeNote')}
            />
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? t('common.saving') : t('common.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
