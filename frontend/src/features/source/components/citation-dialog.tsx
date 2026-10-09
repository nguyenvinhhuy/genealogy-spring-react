import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { addCitation, invalidateCitationQueries, updateCitation } from '@/features/source/api'
import { SourcePicker } from '@/features/source/components/source-picker'
import { buildCitationSchema, type CitationFormValues, NEW_SOURCE } from '@/features/source/lib/schemas'
import { type Citation, type CitationTargetType, SOURCE_TYPES } from '@/features/source/types'
import { useVersionAtOpen } from '@/shared/hooks/use-version-at-open'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'

// Matches CitationRequest.MAX_LOCATOR, so the field stops where the server would refuse.
const LOCATOR_MAX_LENGTH = 200

/** Props of {@link CitationDialog}. */
interface CitationDialogProps {
  targetType: CitationTargetType
  targetId: number
  // Omitted to add a citation; passed to change an existing one's source, locator or quote.
  citation?: Citation
}

/**
 * Adds a citation to one record, or edits one, creating a new source in the same request when needed.
 *
 * @param props the record cited, and the citation being edited if any
 * @returns the dialog with its trigger button
 */
export function CitationDialog({ targetType, targetId, citation }: CitationDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const version = useVersionAtOpen(citation?.version ?? null, open)
  const schema = useMemo(() => buildCitationSchema(t), [t])
  const defaults: CitationFormValues = {
    source: citation ? String(citation.sourceId) : '',
    newTitle: '',
    newType: 'OTHER',
    locator: citation?.locator ?? '',
    quote: citation?.quote ?? '',
    changeNote: '',
  }

  const { control, register, handleSubmit, reset, formState } = useForm<CitationFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })
  const source = useWatch({ control, name: 'source' })

  const mutation = useMutation({
    mutationFn: (values: CitationFormValues) => {
      const isNew = values.source === NEW_SOURCE
      // One request for both: a source created first and a citation that then failed left an orphan (§8.8 #16).
      const payload = {
        sourceId: isNew ? null : Number(values.source),
        newSource: isNew ? { title: values.newTitle.trim(), type: values.newType } : null,
        targetType,
        targetId,
        locator: values.locator.trim() || null,
        quote: values.quote.trim() || null,
        changeNote: values.changeNote.trim() || null,
        version,
      }
      return citation ? updateCitation(citation.id, payload) : addCitation(payload)
    },
    onSuccess: async () => {
      await invalidateCitationQueries(queryClient, targetType, targetId)
      toast.success(t(citation ? 'source.citationUpdated' : 'source.cited'))
      setOpen(false)
    },
  })

  const idPrefix = citation ? `citation-${citation.id}` : `citation-new-${targetType}-${targetId}`

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          reset(defaults)
        }
      }}
    >
      <DialogTrigger asChild>
        {citation ? (
          <Button variant="ghost" size="sm">
            <Pencil aria-hidden />
            {t('common.edit')}
          </Button>
        ) : (
          // Pulled left by its own padding, so the icon lines up with the citation rows above it.
          <Button variant="ghost" size="sm" className="-ml-2">
            <Plus aria-hidden />
            {t('source.addCitation')}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(citation ? 'source.editCitation' : 'source.addCitation')}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.stopPropagation()
            void handleSubmit((values) => mutation.mutate(values))(event)
          }}
        >
          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-source`}>{t('source.source')}</Label>
            <Controller
              control={control}
              name="source"
              render={({ field }) => (
                <SourcePicker
                  id={`${idPrefix}-source`}
                  value={field.value}
                  onChange={field.onChange}
                  currentTitle={citation?.sourceTitle}
                />
              )}
            />
            {formState.errors.source && <p className="text-destructive text-xs">{formState.errors.source.message}</p>}
          </div>

          {source === NEW_SOURCE && (
            <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
              <div className="space-y-2">
                <Label htmlFor={`${idPrefix}-new-title`}>{t('source.title')}</Label>
                <Input
                  id={`${idPrefix}-new-title`}
                  placeholder={t('source.titlePlaceholder')}
                  {...register('newTitle')}
                />
                {formState.errors.newTitle && (
                  <p className="text-destructive text-xs">{formState.errors.newTitle.message}</p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor={`${idPrefix}-new-type`}>{t('source.type')}</Label>
                <Controller
                  control={control}
                  name="newType"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id={`${idPrefix}-new-type`} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {SOURCE_TYPES.map((type) => (
                          <SelectItem key={type} value={type}>
                            {t(`source.types.${type}`)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-locator`}>{t('source.locator')}</Label>
            <Input
              id={`${idPrefix}-locator`}
              maxLength={LOCATOR_MAX_LENGTH}
              placeholder={t('source.locatorPlaceholder')}
              {...register('locator')}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-quote`}>{t('source.quote')}</Label>
            <Textarea id={`${idPrefix}-quote`} placeholder={t('source.quotePlaceholder')} {...register('quote')} />
            {formState.errors.quote && <p className="text-destructive text-xs">{formState.errors.quote.message}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-change-note`}>{t('common.changeNote')}</Label>
            <Textarea
              id={`${idPrefix}-change-note`}
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
