import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Combine } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { invalidateCitationQueries, mergeSources } from '@/features/source/api'
import { SourcePicker } from '@/features/source/components/source-picker'
import type { Source } from '@/features/source/types'
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
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link SourceMergeDialog}. */
interface SourceMergeDialogProps {
  // The source that is folded away; the one picked in the dialog is kept.
  source: Source
}

/**
 * Folds one source into another, which is how a cited duplicate is removed, recording why.
 *
 * @param props the source to fold away
 * @returns the dialog with its trigger button
 */
export function SourceMergeDialog({ source }: SourceMergeDialogProps) {
  // Merge, not delete: a source still cited is refused a delete, because its citations are the evidence (§3.8).
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [target, setTarget] = useState('')
  const [reason, setReason] = useState('')

  const mutation = useMutation({
    mutationFn: () => mergeSources(source.id, Number(target), reason.trim()),
    onSuccess: async (result) => {
      await invalidateCitationQueries(queryClient, null)
      // The scans moved with the citations, and a gallery is kept for twenty minutes (§8.9 #30).
      await queryClient.invalidateQueries({ queryKey: ['media'] })
      toast.success(t('source.merged', { count: result.citationsMoved }))
      // Folded citations kept both quotes on the survivor; the family is told, not left to find out.
      result.folded.forEach((line) => toast.info(line))
      setOpen(false)
    },
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          setTarget('')
          setReason('')
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <Combine aria-hidden />
          {t('source.merge')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('source.mergeTitle', { title: source.title })}</DialogTitle>
          <DialogDescription>{t('source.mergeDescription', { count: source.citationCount })}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor={`source-merge-${source.id}`}>{t('source.mergeInto')}</Label>
            <SourcePicker
              id={`source-merge-${source.id}`}
              value={target}
              onChange={setTarget}
              allowNew={false}
              excludeId={source.id}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`source-merge-reason-${source.id}`}>{t('source.mergeReason')}</Label>
            <Textarea
              id={`source-merge-reason-${source.id}`}
              value={reason}
              placeholder={t('source.mergeReasonPlaceholder')}
              onChange={(event) => setReason(event.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button
            disabled={target === '' || reason.trim() === '' || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? t('common.saving') : t('source.merge')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
