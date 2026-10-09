import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'

import { searchPersons } from '@/features/search/api'
import { fetchKinship } from '@/features/tree/api'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { Badge } from '@/shared/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'

const RESULT_LIMIT = 10
// Every write that changes a relationship invalidates the answer, so it need not be refetched on focus.
const STALE_MS = 60_000

/** The person the question is asked from. */
interface KinshipCardProps {
  personId: number
  personName: string
}

/** A person picked as the other end of the question. */
interface Target {
  id: number
  displayName: string
}

/**
 * Works out what another person is to this one.
 *
 * @param props the person the question is asked from, and their name
 * @returns the card element
 */
export function KinshipCard({ personId, personName }: KinshipCardProps) {
  // bác and chú are the same relation at different birth orders, so the answer is computed, never guessed.
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [target, setTarget] = useState<Target | null>(null)
  const settledQuery = useDebounced(query.trim(), TYPING_DEBOUNCE_MS)

  const { data: candidates } = useQuery({
    queryKey: ['persons', 'kinship-search', settledQuery],
    queryFn: () => searchPersons({ query: settledQuery }, RESULT_LIMIT, 0),
    // Nothing typed would list the first ten people of the clan, on every person page, for nobody to read.
    enabled: settledQuery !== '',
  })

  const { data: kinship, isFetching, isError } = useQuery({
    queryKey: ['kinship', personId, target?.id],
    queryFn: () => fetchKinship(personId, target?.id ?? personId),
    enabled: target != null && target.id !== personId,
    staleTime: STALE_MS,
  })

  const found = (candidates?.content ?? []).filter((person) => person.id !== personId)
  // The chosen person stays in the list even after the search moves on, or the picker and the answer go blank.
  const options = target && !found.some((person) => person.id === target.id) ? [target, ...found] : found

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('kinship.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Input
          value={query}
          placeholder={t('person.searchPlaceholder')}
          onChange={(event) => setQuery(event.target.value)}
        />

        <Select
          value={target ? String(target.id) : ''}
          onValueChange={(next) => setTarget(options.find((person) => String(person.id) === next) ?? null)}
        >
          <SelectTrigger className="w-full" aria-label={t('kinship.pickPerson')}>
            <SelectValue placeholder={t('kinship.pickPerson')} />
          </SelectTrigger>
          <SelectContent>
            {options.map((person) => (
              <SelectItem key={person.id} value={String(person.id)}>
                {person.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {isFetching && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}

        {isError && !isFetching && (
          <p className="text-destructive text-sm">{t('common.unexpectedError')}</p>
        )}

        {kinship && target && !isFetching && (
          <div className="space-y-2">
            {kinship.term ? (
              <p className="text-sm">
                <Trans
                  i18nKey="kinship.answer"
                  values={{ speaker: personName, target: target.displayName, term: kinship.term }}
                  components={{ strong: <strong /> }}
                />
              </p>
            ) : (
              <p className="text-muted-foreground text-sm">{t('kinship.noRelation')}</p>
            )}

            {kinship.commonAncestorId != null && (
              <div className="flex flex-wrap gap-2">
                <Badge variant="outline">
                  {t('kinship.steps', { up: kinship.stepsUp, down: kinship.stepsDown })}
                </Badge>
                {/* The server leaves the side empty when it cannot tell, so no badge is better than a guess. */}
                {kinship.side != null && (
                  <Badge variant="secondary">
                    {t(kinship.side === 'MATERNAL' ? 'kinship.motherSide' : 'kinship.fatherSide')}
                  </Badge>
                )}
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
