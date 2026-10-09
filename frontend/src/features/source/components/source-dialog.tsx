import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { createSource, invalidateCitationQueries, updateSource } from '@/features/source/api'
import { buildSourceSchema, type SourceFormValues } from '@/features/source/lib/schemas'
import { type Source, SOURCE_TYPES } from '@/features/source/types'
import { useVersionAtOpen } from '@/shared/hooks/use-version-at-open'
import { voidSubmit } from '@/shared/lib/forms'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link SourceDialog}. */
interface SourceDialogProps {
  // Omitted to create a source; passed to edit an existing one.
  source?: Source
}

/**
 * Creates a source, or edits every field of an existing one, recording why.
 *
 * @param props the source being edited, or nothing to create one
 * @returns the dialog with its trigger button
 */
export function SourceDialog({ source }: SourceDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const version = useVersionAtOpen(source?.version ?? null, open)
  const schema = useMemo(() => buildSourceSchema(t), [t])
  const defaults: SourceFormValues = {
    title: source?.title ?? '',
    type: source?.type ?? 'OTHER',
    author: source?.author ?? '',
    dateText: source?.dateText ?? '',
    repository: source?.repository ?? '',
    notes: source?.notes ?? '',
    changeNote: '',
  }

  const { control, register, handleSubmit, reset, formState } = useForm<SourceFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })

  const mutation = useMutation({
    mutationFn: (values: SourceFormValues) => {
      const payload = {
        title: values.title.trim(),
        type: values.type,
        author: values.author.trim() || null,
        dateText: values.dateText.trim() || null,
        repository: values.repository.trim() || null,
        notes: values.notes.trim() || null,
        changeNote: values.changeNote.trim() || null,
        version,
      }
      return source ? updateSource(source.id, payload) : createSource(payload)
    },
    onSuccess: async () => {
      // Every citation of a renamed source shows the new title, so the citation lists refresh too.
      await invalidateCitationQueries(queryClient, null)
      toast.success(t(source ? 'source.updated' : 'source.created'))
      setOpen(false)
    },
  })

  const idPrefix = source ? `source-${source.id}` : 'source-new'
  const errorOf = (field: keyof SourceFormValues) =>
    formState.errors[field] && <p className="text-destructive text-xs">{formState.errors[field]?.message}</p>

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
        {source ? (
          <Button variant="ghost" size="sm">
            <Pencil aria-hidden />
            {t('common.edit')}
          </Button>
        ) : (
          <Button size="sm">
            <Plus aria-hidden />
            {t('source.create')}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(source ? 'source.editTitle' : 'source.create')}</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
            <div className="space-y-2">
              <Label htmlFor={`${idPrefix}-title`}>{t('source.title')}</Label>
              <Input id={`${idPrefix}-title`} placeholder={t('source.titlePlaceholder')} {...register('title')} />
              {errorOf('title')}
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${idPrefix}-type`}>{t('source.type')}</Label>
              <Controller
                control={control}
                name="type"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id={`${idPrefix}-type`} className="w-full">
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

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor={`${idPrefix}-author`}>{t('source.author')}</Label>
              <Input id={`${idPrefix}-author`} {...register('author')} />
              {errorOf('author')}
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${idPrefix}-date`}>{t('source.dateText')}</Label>
              <Input id={`${idPrefix}-date`} placeholder={t('source.dateTextPlaceholder')} {...register('dateText')} />
              {errorOf('dateText')}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-repository`}>{t('source.repository')}</Label>
            <Input
              id={`${idPrefix}-repository`}
              placeholder={t('source.repositoryPlaceholder')}
              {...register('repository')}
            />
            {errorOf('repository')}
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-notes`}>{t('source.notes')}</Label>
            <Textarea id={`${idPrefix}-notes`} {...register('notes')} />
            {errorOf('notes')}
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
