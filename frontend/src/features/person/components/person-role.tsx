import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { PersonLink } from '@/features/person/components/person-link'
import { NODE_STALE_MS, PersonName } from '@/features/person/components/person-name'
import { loadPersonNode } from '@/features/person/lib/node-batcher'

/** Which part a person plays towards someone else. */
export type PersonRoleKind = 'spouse' | 'parent'

/** Props of {@link PersonRole}. */
interface PersonRoleProps {
  id: number
  role: PersonRoleKind
  // A link to their page, or the name as plain text inside a control that is itself clickable.
  link?: boolean
}

/**
 * Names a person with the part they play, "Vợ: X" or "Cha: X", worded from their recorded sex.
 *
 * @param props the person, the part they play, and whether to link to them
 * @returns the label and the name
 */
export function PersonRole({ id, role, link = false }: PersonRoleProps) {
  const { t } = useTranslation()
  // The same cache entry PersonName reads, so the label and the name arrive in one request.
  const { data } = useQuery({
    queryKey: ['person', id, 'node'],
    queryFn: () => loadPersonNode(id),
    staleTime: NODE_STALE_MS,
  })
  // An unrecorded sex is never read as male (§5.3): "Vợ/chồng", "Cha/mẹ" until the record says which.
  const label = t(`person.roles.${role}.${data?.gender ?? 'UNKNOWN'}`)
  return (
    <>
      {label}: {link ? <PersonLink id={id} /> : <PersonName id={id} />}
    </>
  )
}
