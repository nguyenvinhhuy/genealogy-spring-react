import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { RevisionToggle } from '@/features/audit/components/revision-list'
import { EventDialog } from '@/features/event/components/event-dialog'
import { EventList } from '@/features/event/components/event-list'
import { deleteFamily, invalidateFamilyQueries, removeChild } from '@/features/family/api'
import { ChildLinkDialog } from '@/features/family/components/child-link-dialog'
import { UnionDialog } from '@/features/family/components/union-dialog'
import type { Family, FamilyChild } from '@/features/family/types'
import { MediaToggle } from '@/features/media/components/media-card'
import { PersonLink } from '@/features/person/components/person-link'
import { CitationToggle } from '@/features/source/components/citation-card'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { Badge } from '@/shared/ui/badge'

/** Props of {@link UnionPanel}. */
interface UnionPanelProps {
  family: Family
  // The person whose page this is, so the other partner can be named as the spouse.
  personId: number
}

/**
 * Shows one union of a person — the spouse, the marriage's events and the children — with its edit actions.
 *
 * @param props the union and the person whose page it is on
 * @returns the panel element
 */
export function UnionPanel({ family, personId }: UnionPanelProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { mayEdit, mayDelete, mayAudit } = usePermissions()
  const spouseId = family.partner1Id === personId ? family.partner2Id : family.partner1Id

  const removal = useMutation({
    mutationFn: (changeNote: string) => deleteFamily(family.id, changeNote),
    onSuccess: async () => {
      await invalidateFamilyQueries(queryClient)
      toast.success(t('family.deleted'))
    },
  })

  return (
    <section className="space-y-3 rounded-md border p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Badge variant="outline">{t('family.unionN', { n: family.orderIndex + 1 })}</Badge>
          <span>
            {t('family.spouse')}: {spouseId ? <PersonLink id={spouseId} /> : t('family.unknownSpouse')}
          </span>
          <Badge variant="secondary">{t(`family.statuses.${family.status}`)}</Badge>
        </div>
        {(mayEdit || mayDelete) && (
          <div className="flex items-center gap-1">
            {mayEdit && <UnionDialog family={family} />}
            {mayDelete && (
              <ConfirmDeleteDialog
                label={t('family.deleteUnion')}
                title={t('family.deleteUnionTitle')}
                description={t('family.deleteUnionDescription', { count: family.children.length })}
                pending={removal.isPending}
                onConfirm={(note) => removal.mutateAsync(note)}
              />
            )}
          </div>
        )}
      </div>

      <div className="space-y-1">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-sm font-medium">{t('family.unionEvents')}</h3>
          {mayEdit && <EventDialog subjectType="FAMILY" subjectId={family.id} />}
        </div>
        <EventList
          subjectType="FAMILY"
          subjectId={family.id}
          emptyText={t(mayEdit ? 'family.noUnionEvents' : 'family.noUnionEventsOrHidden')}
        />
      </div>

      <div className="space-y-1">
        <h3 className="text-sm font-medium">{t('family.children')}</h3>
        {family.children.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('family.noChildren')}</p>
        ) : (
          <ul className="divide-y">
            {family.children.map((child) => (
              <ChildRow key={child.id} family={family} child={child} mayEdit={mayEdit} mayDelete={mayDelete} />
            ))}
          </ul>
        )}
      </div>

      {/* A union's own evidence: until 2026-09-28 a citation could be entered and seen on a person only. */}
      <CitationToggle targetType="FAMILY" targetId={family.id} />
      {/* A wedding photo belongs to the union, not to either partner (§8.9 #21). */}
      <MediaToggle targetType="FAMILY" targetId={family.id} />
      {/* The union's own trail: status, order and every child linked, corrected or unlinked (§3.8). */}
      {mayAudit && <RevisionToggle entityType="FAMILY" entityId={family.id} />}
    </section>
  )
}

/** Props of {@link ChildRow}. */
interface ChildRowProps {
  family: Family
  child: FamilyChild
  mayEdit: boolean
  mayDelete: boolean
}

/**
 * Renders one child of a union, saying when they are not a con đẻ of a partner.
 *
 * @param props the union, the child link and what the caller may do
 * @returns the row element
 */
function ChildRow({ family, child, mayEdit, mayDelete }: ChildRowProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const unlink = useMutation({
    mutationFn: (changeNote: string) => removeChild(family.id, child.childId, changeNote),
    onSuccess: async () => {
      await invalidateFamilyQueries(queryClient)
      toast.success(t('family.unlinked'))
    },
  })

  // Only the unusual links are labelled: "con đẻ" on every row would bury the con nuôi and con riêng.
  const notByBirth = [
    { partnerId: family.partner1Id, relation: child.relationToP1 },
    { partnerId: family.partner2Id, relation: child.relationToP2 },
  ].filter((slot) => slot.partnerId != null && slot.relation !== 'BIRTH')

  return (
    <li className="flex items-center justify-between gap-3 py-2">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <PersonLink id={child.childId} />
        {child.birthOrder != null && (
          <Badge variant="outline">{t('family.birthOrderN', { n: child.birthOrder })}</Badge>
        )}
        {notByBirth.map((slot) => (
          <Badge key={slot.partnerId} variant="secondary">
            {t(`family.relations.${slot.relation}`)} · <PersonLink id={slot.partnerId as number} />
          </Badge>
        ))}
      </div>
      {(mayEdit || mayDelete) && (
        <div className="flex shrink-0 items-center gap-1">
          {mayEdit && <ChildLinkDialog family={family} child={child} />}
          {mayDelete && (
            <ConfirmDeleteDialog
              label={t('family.unlinkChild')}
              title={t('family.unlinkTitle')}
              description={t('family.unlinkDescription')}
              pending={unlink.isPending}
              onConfirm={(note) => unlink.mutateAsync(note)}
            />
          )}
        </div>
      )}
    </li>
  )
}
