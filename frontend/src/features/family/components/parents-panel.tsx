import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { fetchParentFamiliesOf } from '@/features/family/api'
import type { Family, RelationType } from '@/features/family/types'
import { PersonRole } from '@/features/person/components/person-role'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'

/** Props of {@link ParentsPanel}. */
interface ParentsPanelProps {
  // The person whose page this is: the panel names their parents.
  personId: number
}

/**
 * Lists a person's parents, "Cha: X · Mẹ: Y", for every union they were born or brought into.
 *
 * @param props the person whose parents to show
 * @returns the panel element
 */
export function ParentsPanel({ personId }: ParentsPanelProps) {
  // The person's own unions show their children; without this a child's page never named the parents (§6.1).
  const { t } = useTranslation()
  const parents = useQuery({
    queryKey: ['families', 'parents', personId],
    queryFn: () => fetchParentFamiliesOf(personId),
  })

  return (
    <div className="space-y-1">
      <h3 className="text-sm font-medium">{t('family.parents')}</h3>
      {parents.isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}
      {parents.isError && (
        <p className="text-destructive text-sm">
          {problemMessage(parents.error, t('common.unexpectedError'))}
        </p>
      )}
      {parents.isSuccess && parents.data.length === 0 && (
        <p className="text-muted-foreground text-sm">{t('family.noParents')}</p>
      )}
      {parents.data?.map((family) => (
        <ParentsRow key={family.id} family={family} personId={personId} />
      ))}
    </div>
  )
}

/** Props of {@link ParentsRow}. */
interface ParentsRowProps {
  family: Family
  personId: number
}

/**
 * Names the partners of one union a person is a child of, with how the person is linked to each.
 *
 * @param props the union and the child it is seen from
 * @returns the row element
 */
function ParentsRow({ family, personId }: ParentsRowProps) {
  const { t } = useTranslation()
  const link = family.children.find((child) => child.childId === personId)
  const slots: { partnerId: number | null; relation: RelationType | undefined }[] = [
    { partnerId: family.partner1Id, relation: link?.relationToP1 },
    { partnerId: family.partner2Id, relation: link?.relationToP2 },
  ]
  const known = slots.filter((slot): slot is { partnerId: number; relation: RelationType | undefined } => {
    return slot.partnerId != null
  })

  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
      {known.map((slot) => (
        <span key={slot.partnerId} className="inline-flex flex-wrap items-center gap-2">
          <span>
            <PersonRole id={slot.partnerId} role="parent" link />
          </span>
          {/* Only the unusual links are labelled, as on the union panel: "con đẻ" everywhere would bury them. */}
          {slot.relation != null && slot.relation !== 'BIRTH' && (
            <Badge variant="secondary">{t(`family.relations.${slot.relation}`)}</Badge>
          )}
        </span>
      ))}
      {known.length < 2 && <span className="text-muted-foreground">{t('family.newSingleParentUnion')}</span>}
    </div>
  )
}
