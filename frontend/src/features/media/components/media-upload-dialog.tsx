import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Upload } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { MEDIA_ACCEPT, mediaQueryKey, uploadMedia } from '@/features/media/api'
import { buildUploadSchema, MEDIA_KINDS, type UploadFormValues } from '@/features/media/lib/schemas'
import type { MediaTargetType } from '@/features/media/types'
import { voidSubmit } from '@/shared/lib/forms'
import { problemMessage } from '@/shared/lib/problem-detail'
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
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

/** Props of {@link MediaUploadDialog}. */
interface MediaUploadDialogProps {
  targetType: MediaTargetType
  targetId: number
}

/**
 * Uploads one or more files to a record, with what they are and a caption.
 *
 * @param props which record the files belong to
 * @returns the dialog with its trigger button
 */
export function MediaUploadDialog({ targetType, targetId }: MediaUploadDialogProps) {
  // Several at once: the 80 A3 pages of an old gia phả were one click and one dialog each (§8.9 #23).
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [files, setFiles] = useState<File[]>([])
  const [done, setDone] = useState(0)
  // Changing it remounts the file input, so it stops listing files that are no longer waiting to be sent.
  const [inputKey, setInputKey] = useState(0)
  const schema = useMemo(() => buildUploadSchema(t), [t])
  const kinds = MEDIA_KINDS.filter((kind) => kind !== 'PORTRAIT' || targetType === 'PERSON')
  const defaults: UploadFormValues = { kind: targetType === 'SOURCE' ? 'SCAN' : 'PHOTO', caption: '' }
  const { control, register, handleSubmit, reset, formState } = useForm<UploadFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })
  const kind = useWatch({ control, name: 'kind' })
  const tooManyForPortrait = kind === 'PORTRAIT' && files.length > 1

  const upload = useMutation({
    mutationFn: async (values: UploadFormValues) => {
      // One at a time: the server holds a 20 MB body per request, and a failed file must not hide the others.
      const failures: { file: File; message: string }[] = []
      for (const [index, file] of files.entries()) {
        try {
          await uploadMedia(targetType, targetId, file, values.kind, values.caption)
        } catch (error) {
          failures.push({ file, message: `${file.name}: ${problemMessage(error, t('common.unexpectedError'))}` })
        }
        setDone(index + 1)
      }
      return failures
    },
    onSuccess: (failures) => {
      void queryClient.invalidateQueries({ queryKey: mediaQueryKey(targetType, targetId) })
      void queryClient.invalidateQueries({ queryKey: ['revisions'] })
      const stored = files.length - failures.length
      if (stored > 0) {
        toast.success(t('media.uploadedN', { count: stored }))
      }
      failures.forEach((failure) => toast.error(failure.message))
      if (failures.length === 0) {
        setOpen(false)
        return
      }
      // Only what failed stays, or pressing the button again would store every page that already went up twice.
      setFiles(failures.map((failure) => failure.file))
      setInputKey((current) => current + 1)
      setDone(0)
    },
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          reset(defaults)
          setFiles([])
          setDone(0)
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Upload aria-hidden />
          {t('media.add')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('media.uploadTitle')}</DialogTitle>
          <DialogDescription>{t('media.hint')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => upload.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor="media-files">{t('media.files')}</Label>
            <Input
              key={inputKey}
              id="media-files"
              type="file"
              multiple
              accept={MEDIA_ACCEPT}
              onChange={(event) => setFiles(Array.from(event.target.files ?? []))}
            />
            {files.length > 0 && (
              <p className="text-muted-foreground text-xs">{t('media.filesChosen', { count: files.length })}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="media-kind">{t('media.kind')}</Label>
            <Controller
              control={control}
              name="kind"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="media-kind" className="w-full">
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
            {kind === 'PORTRAIT' && <p className="text-muted-foreground text-xs">{t('media.portraitHint')}</p>}
            {tooManyForPortrait && <p className="text-destructive text-xs">{t('media.onePortrait')}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="media-caption">{t('media.caption')}</Label>
            <Input id="media-caption" placeholder={t('media.captionPlaceholder')} {...register('caption')} />
            {formState.errors.caption && (
              <p className="text-destructive text-xs">{formState.errors.caption.message}</p>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={upload.isPending || files.length === 0 || tooManyForPortrait}>
              {upload.isPending
                ? t('media.uploadingN', { done, count: files.length })
                : t('media.upload')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
