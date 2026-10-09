import { useQuery } from '@tanstack/react-query'

import { loadPlace, PLACE_QUERY_KEY } from '@/features/place/api'

// Places are renamed rarely, and every place write invalidates this key, so a path fetched once stays good.
const STALE_MS = 10 * 60_000

/** Props of {@link PlaceName}. */
interface PlaceNameProps {
  id: number
}

/**
 * Renders a place's full path from its id, batched with every other place on the page.
 *
 * @param props the place id
 * @returns the path, or nothing while it loads
 */
export function PlaceName({ id }: PlaceNameProps) {
  // The path, not the name: a birth "ở Hoằng Lộc" means nothing without the tỉnh around it (§8.8 #13).
  const { data } = useQuery({
    queryKey: [...PLACE_QUERY_KEY, 'one', id],
    queryFn: () => loadPlace(id),
    staleTime: STALE_MS,
  })
  return <>{data?.path ?? ''}</>
}
