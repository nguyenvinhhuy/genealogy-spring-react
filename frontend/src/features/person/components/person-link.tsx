import { Link } from 'react-router'

import { PersonName } from '@/features/person/components/person-name'

/** Props of {@link PersonLink}. */
interface PersonLinkProps {
  id: number
}

/**
 * Links to a person's page, showing their name rather than the raw id.
 *
 * @param props the person id to link to
 * @returns the link element
 */
export function PersonLink({ id }: PersonLinkProps) {
  return (
    <Link className="underline-offset-4 hover:underline" to={`/persons/${id}`}>
      <PersonName id={id} />
    </Link>
  )
}
