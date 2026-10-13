import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  defaultUnionChoice,
  fetchFamiliesOf,
  invalidateFamilyQueries,
  linkExisting,
  NEW_UNION,
} from '@/features/family/api'
import { UnionSelect } from '@/features/family/components/union-select'
import type { Family, LinkKind } from '@/features/family/types'
import { PersonPicker } from '@/features/person/components/person-picker'
import type { PersonNode } from '@/features/person/types'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

/** Props of {@link LinkPersonDialog}. */
interface LinkPersonDialogProps {
  personId: number
  // This person's own unions, offered when the other person becomes their child.
  families: Family[]
  // What the form opens on: a child from the tree, where adding the next generation is the common case.
  defaultKind?: LinkKind
  // Named in the title, so it is plain who the other person is being linked to.
  personName?: string
}

/**
 * Links this person to someone already in the gia phả, as a spouse, a child or a parent.
 *
 * @param props the person the link is made from and their unions
 * @returns the dialog with its trigger button
 */
export function LinkPersonDialog({ personId, families, defaultKind = 'SPOUSE', personName }: LinkPersonDialogProps) {
  // Without it a person entered alone, or unlinked by mistake, could never be placed on the tree again (§8.12 #21).
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [kind, setKind] = useState<LinkKind>(defaultKind)
  const [other, setOther] = useState<PersonNode | null>(null)
  // Null until the member picks: the default is worked out from the unions, which may load after the dialog opens.
  const [familyId, setFamilyId] = useState<string | null>(null)

  // For a parent, the union is one of the parent's, which the page does not hold.
  const otherFamilies = useQuery({
    queryKey: ['families', 'of', other?.id],
    queryFn: () => fetchFamiliesOf(other?.id ?? 0),
    enabled: open && kind === 'PARENT' && other != null,
  })
  const unions = kind === 'CHILD' ? families : kind === 'PARENT' ? (otherFamilies.data ?? []) : []
  const unionAnchor = kind === 'PARENT' ? (other?.id ?? personId) : personId
  const chosenUnion = familyId ?? defaultUnionChoice(unions)
  // A parent's unions still loading would default to a new one-parent union and misfile the child.
  const unionPending = kind === 'PARENT' && other != null && otherFamilies.isFetching
  const unionMissing = kind !== 'SPOUSE' && chosenUnion === ''

  const mutation = useMutation({
    mutationFn: () =>
      linkExisting(personId, {
        kind,
        otherPersonId: other?.id ?? 0,
        familyId: kind !== 'SPOUSE' && chosenUnion !== NEW_UNION ? Number(chosenUnion) : null,
      }),
    onSuccess: async () => {
      await invalidateFamilyQueries(queryClient)
      toast.success(t('family.linked'))
      setOpen(false)
    },
  })

  const reset = () => {
    setKind(defaultKind)
    setOther(null)
    setFamilyId(null)
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
          <DialogTitle>
            {personName ? t('family.linkExistingFor', { name: personName }) : t('family.linkExisting')}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="link-kind">{t('family.linkKind')}</Label>
            <Select
              value={kind}
              onValueChange={(next) => {
                setKind(next as LinkKind)
                setFamilyId(null)
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
              setFamilyId(null)
            }}
            excludeIds={[personId]}
          />

          {kind !== 'SPOUSE' && (
            <div className="space-y-2">
              <Label htmlFor="link-family">{t('family.withUnion')}</Label>
              <UnionSelect
                id="link-family"
                families={unions}
                personId={unionAnchor}
                value={chosenUnion}
                onChange={setFamilyId}
                invalid={unionMissing}
              />
              {unionMissing && <p className="text-destructive text-xs">{t('family.pickUnionRequired')}</p>}
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button
            disabled={other == null || unionMissing || unionPending || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? t('common.saving') : t('family.link')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
