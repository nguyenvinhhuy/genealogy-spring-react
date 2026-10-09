import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { PersonNode } from '@/features/person/types'
import { searchPersons } from '@/features/search/api'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

// Enough to find a name, few enough that the list stays something a person reads rather than scrolls.
const RESULT_LIMIT = 10

/** Props of {@link PersonPicker}. */
interface PersonPickerProps {
  id: string
  label: string
  value: PersonNode | null
  onChange: (person: PersonNode | null) => void
  // People who must not be offered, such as the person the link is being made from.
  excludeIds?: number[]
}

/**
 * Finds one person by typing part of their name, then choosing from the matches.
 *
 * @param props the field id and label, the chosen person, the change handler and who to leave out
 * @returns the field element
 */
export function PersonPicker({ id, label, value, onChange, excludeIds = [] }: PersonPickerProps) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const settled = useDebounced(query.trim(), TYPING_DEBOUNCE_MS)

  const { data, isError } = useQuery({
    queryKey: ['persons', 'picker', settled],
    queryFn: () => searchPersons({ query: settled }, RESULT_LIMIT, 0),
    // An empty search would list the first ten people of the clan, which is no help in finding one.
    enabled: settled !== '',
  })

  const found = (data?.content ?? []).filter((person) => !excludeIds.includes(person.id))
  // The chosen person stays listed after the search moves on, or the select goes blank under them.
  const options = value && !found.some((person) => person.id === value.id) ? [value, ...found] : found

  return (
    <div className="space-y-2">
      <Label htmlFor={`${id}-query`}>{label}</Label>
      <Input
        id={`${id}-query`}
        value={query}
        placeholder={t('person.searchPlaceholder')}
        onChange={(event) => setQuery(event.target.value)}
      />
      <Select
        value={value ? String(value.id) : ''}
        onValueChange={(next) => onChange(options.find((person) => String(person.id) === next) ?? null)}
      >
        <SelectTrigger id={id} className="w-full" aria-label={label}>
          <SelectValue placeholder={t('person.pickPerson')} />
        </SelectTrigger>
        <SelectContent>
          {options.map((person) => (
            <SelectItem key={person.id} value={String(person.id)}>
              {/* The id and đời, because duplicates share a name exactly and a name alone tells them apart not at all. */}
              {person.displayName} · #{person.id}
              {person.generation != null && ` · ${t('person.generationN', { n: person.generation })}`}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {isError && <p className="text-destructive text-xs">{t('common.unexpectedError')}</p>}
    </div>
  )
}
