import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import type { PersonSummary } from '@/features/person/types'
import { searchPersons } from '@/features/search/api'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'

// A handful is enough to spot the same person; more would read as a search, not a warning.
const HINT_LIMIT = 5

/** Props of {@link DuplicateHint}. */
interface DuplicateHintProps {
  surname: string
  middleName: string
  givenName: string
  // The person the new one is being added to, who is no duplicate of them.
  excludeIds?: number[]
  // What each match offers; a link to their page when absent.
  renderAction?: (person: PersonSummary) => ReactNode
}

/**
 * Warns, while a new person's name is typed, that someone of that name is already in the gia phả.
 *
 * @param props the name being typed, who to leave out, and what each match offers
 * @returns the warning, or nothing when no one matches
 */
export function DuplicateHint({
  surname,
  middleName,
  givenName,
  excludeIds = [],
  renderAction,
}: DuplicateHintProps) {
  // A warning, never a refusal (§5.1): two people can share a name, but a second copy of one person splits the tree.
  const { t } = useTranslation()
  const typed = givenName.trim()
    ? [surname, middleName, givenName]
        .map((part) => part.trim())
        .join(' ')
        .trim()
    : ''
  const settled = useDebounced(typed, TYPING_DEBOUNCE_MS)

  const { data } = useQuery({
    queryKey: ['persons', 'duplicate-hint', settled],
    queryFn: () => searchPersons({ query: settled }, HINT_LIMIT, 0),
    enabled: settled !== '',
  })

  const matches =
    settled === '' ? [] : (data?.content ?? []).filter((person) => !excludeIds.includes(person.id))
  if (matches.length === 0) {
    return null
  }

  return (
    <div role="status" className="border-bronze/50 bg-bronze/10 space-y-2 rounded-md border p-3 text-sm">
      <p className="font-medium">{t('person.duplicateHint.title')}</p>
      <p className="text-muted-foreground text-xs">{t('person.duplicateHint.hint')}</p>
      <ul className="space-y-1">
        {matches.map((person) => (
          <li key={person.id} className="flex flex-wrap items-center justify-between gap-2">
            <span>
              {person.displayName} · #{person.id}
              {person.generation != null && ` · ${t('person.generationN', { n: person.generation })}`}
            </span>
            {renderAction ? (
              renderAction(person)
            ) : (
              <Link
                className="text-primary underline-offset-4 hover:underline"
                to={`/persons/${person.id}`}
                target="_blank"
                rel="noreferrer"
              >
                {t('person.duplicateHint.open')}
              </Link>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
