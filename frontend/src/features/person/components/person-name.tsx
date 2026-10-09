import { useQuery } from '@tanstack/react-query'

import { loadPersonNode } from '@/features/person/lib/node-batcher'

// Names change rarely, so a resolved name stays fresh for the life of the page.
const STALE_MS = 5 * 60 * 1000

/** Props of {@link PersonName}. */
interface PersonNameProps {
  id: number
}

/**
 * Renders a person's display name from their id, as plain text.
 *
 * @param props the person id
 * @returns the name, or the id until it loads
 */
export function PersonName({ id }: PersonNameProps) {
  // Under ['person', id], so every write that invalidates the person also refreshes the name wherever it is shown.
  const { data } = useQuery({
    queryKey: ['person', id, 'node'],
    queryFn: () => loadPersonNode(id),
    staleTime: STALE_MS,
  })
  return <>{data?.displayName ?? `#${id}`}</>
}
