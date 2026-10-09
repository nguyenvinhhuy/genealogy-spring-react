import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { MergePanel } from '@/features/merge/components/merge-panel'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'

/** Props of {@link MergeDialog}. */
interface MergeDialogProps {
  personId: number
  personName: string
  relatedPersonId: number
  relatedPersonName: string
}

/**
 * Folds one suspected duplicate into the other, after the reader says which to keep and why.
 *
 * @param props the two people the finding paired up
 * @returns the dialog element
 */
export function MergeDialog({ personId, personName, relatedPersonId, relatedPersonName }: MergeDialogProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          {t('merge.open')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('merge.title')}</DialogTitle>
        </DialogHeader>
        <MergePanel
          personId={personId}
          personName={personName}
          relatedPersonId={relatedPersonId}
          relatedPersonName={relatedPersonName}
          onClose={() => setOpen(false)}
        />
      </DialogContent>
    </Dialog>
  )
}
