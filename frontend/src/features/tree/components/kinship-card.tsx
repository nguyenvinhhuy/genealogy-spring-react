import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'

import { PersonPicker } from '@/features/person/components/person-picker'
import type { PersonNode } from '@/features/person/types'
import { fetchKinship } from '@/features/tree/api'
import { Badge } from '@/shared/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

// Every write that changes a relationship invalidates the answer, so it need not be refetched on focus.
const STALE_MS = 60_000

/** The person the question is asked from. */
interface KinshipCardProps {
  personId: number
  personName: string
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
  const [target, setTarget] = useState<PersonNode | null>(null)

  const { data: kinship, isFetching, isError } = useQuery({
    queryKey: ['kinship', personId, target?.id],
    queryFn: () => fetchKinship(personId, target?.id ?? personId),
    enabled: target != null && target.id !== personId,
    staleTime: STALE_MS,
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('kinship.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <PersonPicker
          id={`kinship-person-${personId}`}
          label={t('kinship.pickPerson')}
          value={target}
          onChange={setTarget}
          excludeIds={[personId]}
        />

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
