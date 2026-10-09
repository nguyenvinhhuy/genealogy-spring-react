import { useMutation } from '@tanstack/react-query'
import { ArrowRightLeft } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { mergePersons } from '@/features/merge/api'
import type { MergeResult } from '@/features/merge/types'
import { invalidateClanData } from '@/shared/lib/query-client'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link MergePanel}. */
export interface MergePanelProps {
  personId: number
  personName: string
  relatedPersonId: number
  relatedPersonName: string
  // Called when the reader is done with the result, so the dialog around the panel can close.
  onClose: () => void
}

/**
 * Asks which of two people to keep and why, runs the merge, and reports what moved.
 *
 * @param props the two people, and what to call when the reader is finished
 * @returns the panel element
 */
export function MergePanel({ personId, personName, relatedPersonId, relatedPersonName, onClose }: MergePanelProps) {
  // Deliberately not one-click: a merge deletes a person, so the direction and the basis are both chosen here.
  const { t } = useTranslation()
  const [keepFirst, setKeepFirst] = useState(true)
  const [reason, setReason] = useState('')
  const [result, setResult] = useState<MergeResult | null>(null)
  const mounted = useRef(true)
  const merged = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      // Refreshed when the panel goes, not on success: refetching unmounts the card and the dialog along with it.
      if (merged.current) {
        void invalidateClanData()
      }
    }
  }, [])

  const targetId = keepFirst ? personId : relatedPersonId
  const targetName = keepFirst ? personName : relatedPersonName
  const duplicateId = keepFirst ? relatedPersonId : personId
  const duplicateName = keepFirst ? relatedPersonName : personName

  const merge = useMutation({
    mutationFn: () => mergePersons({ targetId, duplicateId, reason: reason.trim() }),
    onSuccess: (done) => {
      merged.current = true
      setResult(done)
      toast.success(t('merge.done', { name: done.targetName }))
      // Closed while it ran, so nothing is left to refresh it later: the lists would still show the duplicate.
      if (!mounted.current) {
        void invalidateClanData()
      }
    },
  })

  if (result) {
    return (
      <div className="space-y-2 text-sm">
        <p>{t('merge.summary', { name: result.targetName })}</p>
        <ul className="text-muted-foreground list-disc space-y-1 pl-5">
          <li>{t('merge.counts.names', { n: result.namesMoved })}</li>
          <li>{t('merge.counts.unions', { n: result.unionsMoved + result.unionsCollapsed })}</li>
          <li>{t('merge.counts.children', { n: result.childLinksMoved })}</li>
          <li>{t('merge.counts.events', { n: result.eventsMoved })}</li>
          <li>{t('merge.counts.citations', { n: result.citationsMoved })}</li>
        </ul>
        {result.notes.length > 0 && (
          <div className="space-y-1 border-t pt-2">
            <p className="font-medium">{t('merge.dropped')}</p>
            <ul className="text-muted-foreground list-disc space-y-1 pl-5">
              {result.notes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          </div>
        )}
        <Button size="sm" onClick={onClose}>
          {t('common.back')}
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4 text-sm">
      <div className="space-y-2 rounded-md border p-3">
        {/* The id is shown because duplicates share a name exactly, so names alone say nothing. */}
        <p>
          {t('merge.keeping')}: <span className="font-medium">{targetName}</span>{' '}
          <span className="text-muted-foreground">#{targetId}</span>
        </p>
        <p className="text-muted-foreground">
          {t('merge.deleting')}: <span className="font-medium">{duplicateName}</span> #{duplicateId}
        </p>
        <Button
          size="sm"
          variant="ghost"
          onClick={() => setKeepFirst((current) => !current)}
          aria-label={t('merge.swap')}
        >
          <ArrowRightLeft className="size-4" />
          {t('merge.swap')}
        </Button>
      </div>

      <p className="text-muted-foreground">{t('merge.warning')}</p>

      <div className="space-y-2">
        <Label htmlFor="merge-reason">{t('merge.reason')}</Label>
        <Textarea
          id="merge-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder={t('merge.reasonPlaceholder')}
        />
      </div>

      <Button size="sm" onClick={() => merge.mutate()} disabled={merge.isPending || !reason.trim()}>
        {merge.isPending ? t('common.saving') : t('merge.confirm')}
      </Button>
      {!reason.trim() && <p className="text-muted-foreground text-xs">{t('merge.reasonRequired')}</p>}
    </div>
  )
}
