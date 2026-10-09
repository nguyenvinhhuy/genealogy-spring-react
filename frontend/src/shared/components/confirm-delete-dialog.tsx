import { Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

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

/** Props of {@link ConfirmDeleteDialog}. */
interface ConfirmDeleteDialogProps {
  // The label on the button that opens the dialog, which also names what is deleted.
  label: string
  title: string
  description: string
  pending: boolean
  // Settles when the delete has; the dialog closes only on success, so a refused delete keeps the reason typed.
  onConfirm: (changeNote: string) => Promise<unknown>
}

/**
 * Asks before deleting something, and for the reason, which goes into the change history.
 *
 * @param props what is being deleted, the pending state and the confirm handler
 * @returns the dialog with its trigger button
 */
export function ConfirmDeleteDialog({ label, title, description, pending, onConfirm }: ConfirmDeleteDialogProps) {
  // The reason is asked here, at the moment of deleting, because §3.8 wants "on what basis" answerable years later.
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState('')

  /**
   * Opens or closes the dialog, forgetting the reason whenever it closes.
   *
   * @param next whether the dialog should be open
   */
  const changeOpen = (next: boolean) => {
    setOpen(next)
    if (!next) {
      setNote('')
    }
  }

  /** Deletes, closing the dialog only once the server has agreed. */
  const confirm = async () => {
    try {
      await onConfirm(note.trim())
      changeOpen(false)
    } catch {
      // The caller's mutation has already shown why; staying open keeps the reason for a second try (#15).
    }
  }

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm" className="text-destructive">
          <Trash2 aria-hidden />
          {label}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="delete-reason">{t('common.changeNote')}</Label>
          <Textarea
            id="delete-reason"
            value={note}
            placeholder={t('common.changeNotePlaceholder')}
            onChange={(event) => setNote(event.target.value)}
          />
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => changeOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button variant="destructive" disabled={pending} onClick={() => void confirm()}>
            {pending ? t('common.deleting') : t('common.delete')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
