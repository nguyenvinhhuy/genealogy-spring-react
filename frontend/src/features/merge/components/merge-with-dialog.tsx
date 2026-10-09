import { Combine } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { MergePanel } from '@/features/merge/components/merge-panel'
import { PersonPicker } from '@/features/person/components/person-picker'
import type { PersonNode } from '@/features/person/types'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'

/** Props of {@link MergeWithDialog}. */
interface MergeWithDialogProps {
  personId: number
  personName: string
}

/**
 * Lets an ADMIN pick any other person to merge with this one, for a duplicate no finding has paired up.
 *
 * @param props the person the merge starts from
 * @returns the dialog with its trigger button
 */
export function MergeWithDialog({ personId, personName }: MergeWithDialogProps) {
  // The quality page offers only pairs it detects by name and year, so a duplicate spelled otherwise has no way in.
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [other, setOther] = useState<PersonNode | null>(null)

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          setOther(null)
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Combine aria-hidden />
          {t('merge.mergeWith')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('merge.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <PersonPicker
            id="merge-with"
            label={t('merge.pickOther')}
            value={other}
            onChange={setOther}
            excludeIds={[personId]}
          />
          {other && (
            <MergePanel
              key={other.id}
              personId={personId}
              personName={personName}
              relatedPersonId={other.id}
              relatedPersonName={other.displayName}
              onClose={() => setOpen(false)}
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
