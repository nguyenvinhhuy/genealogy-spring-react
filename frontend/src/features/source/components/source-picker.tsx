import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { searchSources, SOURCE_QUERY_KEY } from '@/features/source/api'
import { NEW_SOURCE } from '@/features/source/lib/schemas'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

const RESULT_LIMIT = 20
// A search result is good for a minute: every source write invalidates it anyway.
const STALE_MS = 60_000

/** Props of {@link SourcePicker}. */
interface SourcePickerProps {
  id: string
  // An existing source's id as a string, NEW_SOURCE, or empty for nothing picked yet.
  value: string
  onChange: (value: string) => void
  // The title of the source already chosen, shown while the search has moved on to other results.
  currentTitle?: string | null
  // Omitted where only an existing source makes sense, such as the target of a merge.
  allowNew?: boolean
  excludeId?: number
}

/**
 * Picks a source by searching its title, or chooses to record a new one.
 *
 * @param props the field id, the chosen value, the change handler and what to offer
 * @returns the search box and the select
 */
export function SourcePicker({ id, value, onChange, currentTitle, allowNew = true, excludeId }: SourcePickerProps) {
  // Searched on the server: the old picker offered the first twenty sources and nothing after them (§8.8 #15).
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const settled = useDebounced(query.trim(), TYPING_DEBOUNCE_MS)

  const { data: found } = useQuery({
    queryKey: [...SOURCE_QUERY_KEY, 'picker', settled],
    queryFn: () => searchSources(settled, 0, RESULT_LIMIT),
    enabled: open || settled !== '',
    staleTime: STALE_MS,
  })
  const sources = (found?.content ?? []).filter((source) => source.id !== excludeId)
  const keepCurrent =
    value !== '' && value !== NEW_SOURCE && !sources.some((source) => String(source.id) === value)

  return (
    <div className="space-y-2">
      <Input
        value={query}
        placeholder={t('source.searchPlaceholder')}
        aria-label={t('source.search')}
        onChange={(event) => setQuery(event.target.value)}
      />
      <Select open={open} onOpenChange={setOpen} value={value} onValueChange={onChange}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue placeholder={t('source.pickExisting')} />
        </SelectTrigger>
        <SelectContent>
          {/* First, and always there, so choosing "new" after an existing one is one click (§8.8 #16). */}
          {allowNew && <SelectItem value={NEW_SOURCE}>{t('source.newSource')}</SelectItem>}
          {keepCurrent && <SelectItem value={value}>{currentTitle ?? `#${value}`}</SelectItem>}
          {sources.map((source) => (
            <SelectItem key={source.id} value={String(source.id)}>
              {source.title} · {t(`source.types.${source.type}`)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
