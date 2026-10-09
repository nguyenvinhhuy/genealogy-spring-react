import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { fetchSuggestions, SUGGESTION_QUERY_KEY } from '@/features/suggestion/api'
import { ReviewDialog } from '@/features/suggestion/components/review-dialog'
import type { PersonSide, Suggestion, SuggestionStatus } from '@/features/suggestion/types'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { Pager } from '@/shared/components/table-card'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { formatDateOnly } from '@/shared/lib/format-moment'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Badge } from '@/shared/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

const FILTERS: (SuggestionStatus | 'ALL')[] = ['PENDING', 'APPROVED', 'REJECTED', 'ALL']

const PAGE_SIZE = 20

/**
 * The review queue: what con cháu have offered, and what a trưởng tộc decided.
 *
 * @returns the page element
 */
export function SuggestionPage() {
  // A MEMBER sees only their own here, which is how they learn what became of what they offered.
  const { t } = useTranslation()
  const { mayReview } = usePermissions()
  const [filter, setFilter] = useState<SuggestionStatus | 'ALL'>('PENDING')
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [SUGGESTION_QUERY_KEY, 'page', filter, page],
    queryFn: () => fetchSuggestions(filter === 'ALL' ? undefined : filter, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
  const suggestions = data?.content ?? []
  const totalPages = data?.page.totalPages ?? 0

  return (
    <PageContainer width="narrow">
      <PageHeader
        title={t('suggestion.title')}
        description={t(mayReview ? 'suggestion.subtitle' : 'suggestion.subtitleMine')}
        actions={
          <Select
            value={filter}
            onValueChange={(next) => {
              setFilter(next as SuggestionStatus | 'ALL')
              setPage(0)
            }}
          >
            <SelectTrigger className="w-48" aria-label={t('suggestion.filter')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {FILTERS.map((value) => (
                <SelectItem key={value} value={value}>
                  {value === 'ALL' ? t('suggestion.all') : t(`suggestion.statuses.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        }
      />

      {isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}
      {/* Its own state: a failed read shown as "no suggestions" tells a reviewer the queue is clear. */}
      {isError && <p className="text-destructive text-sm">{problemMessage(error, t('common.unexpectedError'))}</p>}
      {!isLoading && !isError && suggestions.length === 0 && (
        <p className="text-muted-foreground rounded-lg border border-dashed px-3 py-10 text-center text-sm">
          {t('suggestion.empty')}
        </p>
      )}

      {suggestions.map((suggestion) => (
        <SuggestionRow key={suggestion.id} suggestion={suggestion} mayReview={mayReview} />
      ))}

      {/* The queue used to stop at its first twenty, so the twenty-first đề xuất was never seen (§8.9 #11). */}
      <Pager page={page} totalPages={totalPages} onChange={setPage} label={t('suggestion.title')} />
    </PageContainer>
  )
}

/** Props of {@link SuggestionRow}. */
interface SuggestionRowProps {
  suggestion: Suggestion
  mayReview: boolean
}

/**
 * One suggestion: who it is about, what was proposed, and what was decided, with the review control.
 *
 * @param props the suggestion and whether the reader may review it
 * @returns the card element
 */
function SuggestionRow({ suggestion, mayReview }: SuggestionRowProps) {
  const { t } = useTranslation()
  const pending = suggestion.status === 'PENDING'

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-3">
        <CardTitle className="text-base">
          {suggestion.targetId ? (
            <Link to={`/persons/${suggestion.targetId}`} className="underline-offset-4 hover:underline">
              {suggestion.targetName ?? `#${suggestion.targetId}`}
            </Link>
          ) : (
            t('suggestion.newPerson')
          )}
        </CardTitle>
        <div className="flex items-center gap-2">
          <Badge variant="outline">{t(`suggestion.kinds.${suggestion.kind}`)}</Badge>
          <Badge variant={pending ? 'default' : 'secondary'}>{t(`suggestion.statuses.${suggestion.status}`)}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <p className="text-muted-foreground border-l-2 pl-3 whitespace-pre-line italic">{suggestion.message}</p>
        <p className="text-muted-foreground text-xs">
          {suggestion.createdByName ?? t('suggestion.unknownMember')} · {formatDateOnly(suggestion.createdAt)}
        </p>

        {suggestion.anchor && (
          <p>
            {t(suggestion.anchor.kind === 'CHILD' ? 'suggestion.addAsChild' : 'suggestion.addAsSpouse', {
              name: suggestion.anchor.personName ?? `#${suggestion.anchor.personId}`,
            })}
          </p>
        )}
        {suggestion.proposal && <ProposalSummary proposal={suggestion.proposal} />}
        {!suggestion.payloadReadable && <p className="text-destructive">{t('suggestion.unreadable')}</p>}

        {suggestion.reviewNote && (
          <p className="text-muted-foreground">
            {t('suggestion.reviewNote')}: {suggestion.reviewNote}
            {suggestion.reviewedByName && ` — ${suggestion.reviewedByName}`}
          </p>
        )}

        {mayReview && pending && (
          <div className="border-t pt-3">
            <ReviewDialog suggestion={suggestion} />
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** Props of {@link ProposalSummary}. */
interface ProposalSummaryProps {
  proposal: PersonSide
}

/**
 * Lists what a suggestion proposes, field by field, leaving out what it does not touch.
 *
 * @param props the rendered proposal
 * @returns the list element
 */
function ProposalSummary({ proposal }: ProposalSummaryProps) {
  const { t } = useTranslation()
  return (
    <div className="space-y-1">
      <p className="font-medium">{t('suggestion.proposed')}</p>
      {/* Keyed by position: two proposed names can share a type and a tên, which collided as keys (#43). */}
      {proposal.names.map((name, index) => (
        <p key={index} className="text-muted-foreground">
          {t(`person.nameTypes.${name.type}`)}: {name.display}
        </p>
      ))}
      {proposal.gender && (
        <p className="text-muted-foreground">
          {t('person.gender')}: {t(`person.genders.${proposal.gender}`)}
        </p>
      )}
      {proposal.birth && (
        <p className="text-muted-foreground">
          {t('suggestion.birth')}: {proposal.birth}
        </p>
      )}
      {proposal.death && (
        <p className="text-muted-foreground">
          {t('suggestion.death')}: {proposal.death}
        </p>
      )}
    </div>
  )
}
