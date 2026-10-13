import { useTranslation } from 'react-i18next'

import { NEW_UNION } from '@/features/family/api'
import type { Family } from '@/features/family/types'
import { PersonRole } from '@/features/person/components/person-role'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

/** Props of {@link UnionSelect}. */
interface UnionSelectProps {
  id: string
  // The unions of the parent the child is being added to.
  families: Family[]
  // That parent, so each union is named by the other partner.
  personId: number
  value: string
  onChange: (value: string) => void
  invalid?: boolean
}

/**
 * Chooses which of a parent's unions a child belongs to, each named "Vợ: X" or "Chồng: X".
 *
 * @param props the parent and their unions, the chosen value and the change handler
 * @returns the select element
 */
export function UnionSelect({ id, families, personId, value, onChange, invalid = false }: UnionSelectProps) {
  const { t } = useTranslation()
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger id={id} className="w-full" aria-invalid={invalid}>
        <SelectValue placeholder={t('family.pickUnion')} />
      </SelectTrigger>
      <SelectContent>
        {families.map((family) => (
          <SelectItem key={family.id} value={String(family.id)}>
            <UnionName family={family} personId={personId} />
          </SelectItem>
        ))}
        <SelectItem value={NEW_UNION}>{t('family.newSingleParentUnion')}</SelectItem>
      </SelectContent>
    </Select>
  )
}

/** Props of {@link UnionName}. */
interface UnionNameProps {
  family: Family
  personId: number
}

/**
 * Names one union of a person by the other partner, rather than "Hôn nhân thứ N".
 *
 * @param props the union and the person it is seen from
 * @returns the name of the union
 */
export function UnionName({ family, personId }: UnionNameProps) {
  // The position is stored once per union, so for the other partner "thứ N" could be wrong; a name never is.
  const { t } = useTranslation()
  const spouseId = family.partner1Id === personId ? family.partner2Id : family.partner1Id
  return spouseId != null ? <PersonRole id={spouseId} role="spouse" /> : <>{t('family.unknownSpouseUnion')}</>
}
