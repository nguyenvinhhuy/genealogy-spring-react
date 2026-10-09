import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { MEDIA_QUERY_KEY, PORTRAIT_TYPES, updateMedia } from '@/features/media/api'
import { buildMediaEditSchema, MEDIA_KINDS, type MediaEditFormValues } from '@/features/media/lib/schemas'
import type { Media } from '@/features/media/types'
import { useVersionAtOpen } from '@/shared/hooks/use-version-at-open'
import { voidSubmit } from '@/shared/lib/forms'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link MediaEditDialog}. */
interface MediaEditDialogProps {
  file: Media
}

/**
 * Edits what is recorded about one file: what it is, its caption and its place in the gallery.
 *
 * @param props the file being edited
 * @returns the dialog with its trigger button
 */
export function MediaEditDialog({ file }: MediaEditDialogProps) {
  // One dialog for every field of the file (§6.3); until now kind, caption and order could not be set at all.
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const version = useVersionAtOpen(file.version, open)
  const schema = useMemo(() => buildMediaEditSchema(t), [t])
  const mayBePortrait = file.targetType === 'PERSON' && PORTRAIT_TYPES.has(file.contentType)
  const kinds = MEDIA_KINDS.filter((kind) => kind !== 'PORTRAIT' || mayBePortrait || file.kind === 'PORTRAIT')
  const defaults: MediaEditFormValues = {
    kind: file.kind,
    caption: file.caption ?? '',
    sortOrder: String(file.sortOrder),
    changeNote: '',
  }
  const { control, register, handleSubmit, reset, formState } = useForm<MediaEditFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })

  const save = useMutation({
    mutationFn: (values: MediaEditFormValues) =>
      updateMedia(file.id, {
        kind: values.kind,
        // Sent even when blank: a blank caption is how one is taken back.
        caption: values.caption,
        sortOrder: values.sortOrder === '' ? null : Number(values.sortOrder),
        changeNote: values.changeNote.trim() || null,
        version,
      }),
    onSuccess: () => {
      // Every gallery: setting a portrait demotes the old one, which may be drawn by another card.
      void queryClient.invalidateQueries({ queryKey: [MEDIA_QUERY_KEY] })
      void queryClient.invalidateQueries({ queryKey: ['revisions'] })
      toast.success(t('media.saved'))
      setOpen(false)
    },
  })

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
        <Button variant="ghost" size="sm">
          <Pencil aria-hidden />
          {t('common.edit')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('media.editTitle', { name: file.filename })}</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => save.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor={`media-kind-${file.id}`}>{t('media.kind')}</Label>
            <Controller
              control={control}
              name="kind"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id={`media-kind-${file.id}`} className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {kinds.map((value) => (
                      <SelectItem key={value} value={value}>
                        {t(`media.kinds.${value}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`media-caption-${file.id}`}>{t('media.caption')}</Label>
            <Input id={`media-caption-${file.id}`} {...register('caption')} />
            {formState.errors.caption && (
              <p className="text-destructive text-xs">{formState.errors.caption.message}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor={`media-order-${file.id}`}>{t('media.sortOrder')}</Label>
            <Input id={`media-order-${file.id}`} inputMode="numeric" {...register('sortOrder')} />
            {formState.errors.sortOrder && (
              <p className="text-destructive text-xs">{formState.errors.sortOrder.message}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor={`media-note-${file.id}`}>{t('common.changeNote')}</Label>
            <Textarea
              id={`media-note-${file.id}`}
              placeholder={t('common.changeNotePlaceholder')}
              {...register('changeNote')}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={save.isPending}>
              {save.isPending ? t('common.saving') : t('common.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
