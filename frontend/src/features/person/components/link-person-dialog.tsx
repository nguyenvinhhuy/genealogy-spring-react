import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { fetchFamiliesOf, invalidateFamilyQueries, linkExisting } from '@/features/family/api'
import type { Family, LinkKind } from '@/features/family/types'
import { PersonPicker } from '@/features/person/components/person-picker'
import type { PersonNode } from '@/features/person/types'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

// Radix Select cannot hold an empty-string value, so "a new union" needs a sentinel of its own.
const NEW_UNION = 'new'

/** Props of {@link LinkPersonDialog}. */
interface LinkPersonDialogProps {
  personId: number
  // This person's own unions, offered when the other person becomes their child.
  families: Family[]
}

/**
 * Links this person to someone already in the gia phả, as a spouse, a child or a parent.
 *
 * @param props the person the link is made from and their unions
 * @returns the dialog with its trigger button
 */
export function LinkPersonDialog({ personId, families }: LinkPersonDialogProps) {
  // Without it a person entered alone, or unlinked by mistake, could never be placed on the tree again (§8.12 #21).
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [kind, setKind] = useState<LinkKind>('SPOUSE')
  const [other, setOther] = useState<PersonNode | null>(null)
  const [familyId, setFamilyId] = useState(NEW_UNION)

  // For a parent, the union is one of the parent's, which the page does not hold.
  const otherFamilies = useQuery({
    queryKey: ['families', 'of', other?.id],
    queryFn: () => fetchFamiliesOf(other?.id ?? 0),
    enabled: open && kind === 'PARENT' && other != null,
  })
  const unions = kind === 'CHILD' ? families : kind === 'PARENT' ? (otherFamilies.data ?? []) : []

  const mutation = useMutation({
    mutationFn: () =>
      linkExisting(personId, {
        kind,
        otherPersonId: other?.id ?? 0,
        familyId: kind !== 'SPOUSE' && familyId !== NEW_UNION ? Number(familyId) : null,
      }),
    onSuccess: async () => {
      await invalidateFamilyQueries(queryClient)
      toast.success(t('family.linked'))
      setOpen(false)
    },
  })

  const reset = () => {
    setKind('SPOUSE')
    setOther(null)
    setFamilyId(NEW_UNION)
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          reset()
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Link2 aria-hidden />
          {t('family.linkExisting')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('family.linkExisting')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="link-kind">{t('family.linkKind')}</Label>
            <Select
              value={kind}
              onValueChange={(next) => {
                setKind(next as LinkKind)
                setFamilyId(NEW_UNION)
              }}
            >
              <SelectTrigger id="link-kind" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="SPOUSE">{t('family.linkSpouse')}</SelectItem>
                <SelectItem value="CHILD">{t('family.linkChild')}</SelectItem>
                <SelectItem value="PARENT">{t('family.linkParent')}</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <PersonPicker
            id="link-person"
            label={t('family.linkWho')}
            value={other}
            onChange={(next) => {
              setOther(next)
              setFamilyId(NEW_UNION)
            }}
            excludeIds={[personId]}
          />

          {kind !== 'SPOUSE' && (
            <div className="space-y-2">
              <Label htmlFor="link-family">{t('family.withUnion')}</Label>
              <Select value={familyId} onValueChange={setFamilyId}>
                <SelectTrigger id="link-family" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {unions.map((family) => (
                    <SelectItem key={family.id} value={String(family.id)}>
                      {t('family.unionN', { n: family.orderIndex + 1 })}
                    </SelectItem>
                  ))}
                  <SelectItem value={NEW_UNION}>{t('family.newSingleParentUnion')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button disabled={other == null || mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? t('common.saving') : t('family.link')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
