import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ClipboardCheck } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { fetchSuggestionPreview, invalidateAfterReview, reviewSuggestion } from '@/features/suggestion/api'
import { buildReviewSchema, type ReviewFormValues } from '@/features/suggestion/lib/schemas'
import type { PersonSide, Suggestion } from '@/features/suggestion/types'
import { voidSubmit } from '@/shared/lib/forms'
import { problemMessage } from '@/shared/lib/problem-detail'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link ReviewDialog}. */
interface ReviewDialogProps {
  suggestion: Suggestion
}

/**
 * Shows a reviewer exactly what approving a suggestion would write, then approves or rejects it.
 *
 * @param props the suggestion under review
 * @returns the dialog with its trigger button
 */
export function ReviewDialog({ suggestion }: ReviewDialogProps) {
  // The comparison comes from the server's own merge, so every field approval writes is on it (§8.9 D1).
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const schema = useMemo(() => buildReviewSchema(t), [t])
  const { control, register, reset, formState, handleSubmit } = useForm<ReviewFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { reviewNote: '' },
  })
  const note = useWatch({ control, name: 'reviewNote' })
  const needsPreview = suggestion.kind !== 'NOTE' && suggestion.payloadReadable

  const preview = useQuery({
    queryKey: ['suggestions', 'preview', suggestion.id],
    queryFn: () => fetchSuggestionPreview(suggestion.id),
    enabled: open && needsPreview,
    // Read fresh each time: the person may have been edited since the dialog last opened.
    staleTime: 0,
  })

  const review = useMutation({
    mutationFn: ({ approve, values }: { approve: boolean; values: ReviewFormValues }) =>
      reviewSuggestion(suggestion.id, { approve, reviewNote: values.reviewNote.trim() || null }),
    onSuccess: (reviewed) => {
      void invalidateAfterReview(queryClient, reviewed)
      const key = reviewed.status === 'REJECTED'
        ? 'suggestion.rejected'
        : reviewed.kind === 'NOTE' ? 'suggestion.acknowledged' : 'suggestion.approved'
      toast.success(t(key))
      setOpen(false)
    },
  })

  const approveBlocked = !suggestion.payloadReadable || (needsPreview && !preview.data)

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          reset({ reviewNote: '' })
        }
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">
          <ClipboardCheck aria-hidden />
          {t('suggestion.review')}
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{suggestion.targetName ?? t('suggestion.newPerson')}</DialogTitle>
          <DialogDescription>{t(`suggestion.kinds.${suggestion.kind}`)}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 text-sm">
          <p className="text-muted-foreground border-l-2 pl-3 whitespace-pre-line italic">{suggestion.message}</p>

          {!suggestion.payloadReadable && <p className="text-destructive">{t('suggestion.unreadable')}</p>}
          {suggestion.kind === 'NOTE' && <p className="text-muted-foreground">{t('suggestion.noteReview')}</p>}
          {needsPreview && preview.isLoading && <p className="text-muted-foreground">{t('common.loading')}</p>}
          {needsPreview && preview.isError && (
            <p className="text-destructive">{problemMessage(preview.error, t('common.unexpectedError'))}</p>
          )}
          {preview.data?.anchor && (
            <p>
              {t(preview.data.anchor.kind === 'CHILD' ? 'suggestion.addAsChild' : 'suggestion.addAsSpouse', {
                name: preview.data.anchor.personName ?? `#${preview.data.anchor.personId}`,
              })}
            </p>
          )}
          {preview.data?.proposed && <Comparison current={preview.data.current} proposed={preview.data.proposed} />}

          <div className="space-y-2">
            <Label htmlFor={`review-note-${suggestion.id}`}>{t('suggestion.reviewNote')}</Label>
            <Textarea
              id={`review-note-${suggestion.id}`}
              placeholder={t('suggestion.reasonPlaceholder')}
              {...register('reviewNote')}
            />
            {formState.errors.reviewNote && (
              <p className="text-destructive text-xs">{formState.errors.reviewNote.message}</p>
            )}
            {/* The backend refuses a reasonless rejection; saying so before the click saves retyping. */}
            {!note.trim() && <p className="text-muted-foreground text-xs">{t('suggestion.reasonRequired')}</p>}
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            disabled={review.isPending || !note.trim()}
            onClick={voidSubmit(handleSubmit((values) => review.mutate({ approve: false, values })))}
          >
            {t('suggestion.reject')}
          </Button>
          <Button
            disabled={review.isPending || approveBlocked}
            onClick={voidSubmit(handleSubmit((values) => review.mutate({ approve: true, values })))}
          >
            {t(suggestion.kind === 'NOTE' ? 'suggestion.acknowledge' : 'suggestion.approve')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Props of {@link Comparison}. */
interface ComparisonProps {
  current: PersonSide | null
  proposed: PersonSide
}

/**
 * Lays the person as they are beside the person as approval would leave them, marking what changes.
 *
 * @param props the two sides; no current side for a new person
 * @returns the table element
 */
function Comparison({ current, proposed }: ComparisonProps) {
  const { t } = useTranslation()
  const names = (side: PersonSide | null) =>
    side ? side.names.map((name) => `${name.display}${name.primary ? ` (${t('suggestion.primary')})` : ''}`) : []
  const gender = (side: PersonSide | null) => (side?.gender ? t(`person.genders.${side.gender}`) : '—')
  // Lines, not one joined string: a person with three names overflowed the dialog on one line.
  const rows = [
    { field: t('suggestion.names'), before: names(current), after: names(proposed) },
    { field: t('person.gender'), before: [gender(current)], after: [gender(proposed)] },
    { field: t('suggestion.birth'), before: [current?.birth ?? '—'], after: [proposed.birth ?? '—'] },
    { field: t('suggestion.death'), before: [current?.death ?? '—'], after: [proposed.death ?? '—'] },
  ]
  const lines = (values: string[]) =>
    (values.length ? values : ['—']).map((value, index) => <div key={index}>{value}</div>)

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-28">{t('suggestion.field')}</TableHead>
          {current && <TableHead>{t('suggestion.now')}</TableHead>}
          <TableHead>{t(current ? 'suggestion.afterApproval' : 'suggestion.proposed')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => {
          const changed = current !== null && row.before.join('\n') !== row.after.join('\n')
          return (
            <TableRow key={row.field}>
              <TableCell className="align-top font-medium">{row.field}</TableCell>
              {current && (
                <TableCell className="text-muted-foreground align-top whitespace-normal">{lines(row.before)}</TableCell>
              )}
              <TableCell className={cn('align-top whitespace-normal', changed && 'bg-primary/10 font-medium')}>
                {lines(row.after)}
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}
