import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { PLACE_QUERY_KEY, searchPlaces } from '@/features/place/api'
import { PlaceName } from '@/features/place/components/place-name'
import { fitsInside } from '@/features/place/lib/levels'
import type { PlaceType } from '@/features/place/types'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { NONE_VALUE } from '@/shared/lib/select-values'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

const RESULT_LIMIT = 20
// A search result is good for a minute: the list only changes when someone adds a place, which invalidates it.
const STALE_MS = 60_000

/** Props of {@link PlaceSelect}. */
interface PlaceSelectProps {
  id: string
  value: number | null
  onChange: (value: number | null) => void
  // A place cannot sit inside itself or its own descendants, so those are left out when picking its parent.
  excludeIds?: ReadonlySet<number>
  // The level of the place being placed: a parent must be wider, so narrower levels are left out too.
  childType?: PlaceType
}

/**
 * Picks a recorded place by searching its name, showing each place's full path, or clears the choice.
 *
 * @param props the field id, the chosen place id, the change handler, and which places to leave out
 * @returns the search box and the select
 */
export function PlaceSelect({ id, value, onChange, excludeIds, childType }: PlaceSelectProps) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const settled = useDebounced(query.trim(), TYPING_DEBOUNCE_MS)

  // Fetched only once someone looks: a dialog with this field used to query the place table on every open.
  const { data: found } = useQuery({
    queryKey: [...PLACE_QUERY_KEY, 'picker', settled],
    queryFn: () => searchPlaces(settled, RESULT_LIMIT),
    enabled: open || settled !== '',
    staleTime: STALE_MS,
  })
  const places = (found?.content ?? []).filter(
    (place) => !excludeIds?.has(place.id) && (childType == null || fitsInside(childType, place.type)),
  )
  // The chosen place stays selectable after the search moves on, or the field would silently go blank.
  const keepCurrent = value != null && !places.some((place) => place.id === value)

  return (
    <div className="space-y-2">
      <Input
        value={query}
        placeholder={t('place.searchPlaceholder')}
        aria-label={t('place.search')}
        onChange={(event) => setQuery(event.target.value)}
      />
      <Select
        open={open}
        onOpenChange={setOpen}
        value={value == null ? NONE_VALUE : String(value)}
        onValueChange={(next) => onChange(next === NONE_VALUE ? null : Number(next))}
      >
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={NONE_VALUE}>{t('place.none')}</SelectItem>
          {keepCurrent && (
            <SelectItem value={String(value)}>
              <PlaceName id={value} />
            </SelectItem>
          )}
          {places.map((place) => (
            <SelectItem key={place.id} value={String(place.id)}>
              {place.path}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
