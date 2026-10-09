import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { UserPlus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { LifeStatusBadge } from '@/features/person/components/life-status-badge'
import { searchPersons } from '@/features/search/api'
import { PersonFilters } from '@/features/search/components/person-filters'
import type { PersonSearchFilters } from '@/features/search/types'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { Pager, TableCard, TableMessage } from '@/shared/components/table-card'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'

const PAGE_SIZE = 20

/**
 * Lists persons, filtered by name, đời, living status and date ranges (F20).
 *
 * @returns the page element
 */
export function PersonListPage() {
  const { t } = useTranslation()
  const { mayEdit } = usePermissions()
  const [filters, setFilters] = useState<PersonSearchFilters>({})
  const [page, setPage] = useState(0)

  // Debounced, or every keystroke in the name and year boxes is its own uncancelled request.
  const settled = useDebounced(filters, TYPING_DEBOUNCE_MS)
  const query = settled.query?.trim() || undefined
  const { data, isLoading, isError, error } = useQuery({
    // The trimmed query is what the key holds too, or "an" and "an " become two cached copies of one search.
    queryKey: ['persons', { ...settled, query }, page],
    queryFn: () => searchPersons({ ...settled, query }, PAGE_SIZE, page),
    // Keeps the previous rows on screen while the next page loads, instead of blanking the table.
    placeholderData: keepPreviousData,
  })

  const persons = data?.content ?? []
  const total = data?.page?.totalElements ?? 0
  const totalPages = data?.page?.totalPages ?? 0

  const changeFilters = (next: PersonSearchFilters) => {
    // Back to the first page, or narrowing a filter while on page 4 lands on an empty page.
    setFilters(next)
    setPage(0)
  }

  return (
    <PageContainer>
      <PageHeader
        title={t('person.title')}
        description={t('person.subtitle')}
        actions={
          mayEdit && (
            <Button asChild>
              <Link to="/persons/new">
                <UserPlus aria-hidden />
                {t('person.add')}
              </Link>
            </Button>
          )
        }
      />

      <Card>
        <CardContent>
          <PersonFilters filters={filters} onChange={changeFilters} />
        </CardContent>
      </Card>

      <TableCard
        // The whole match, not the page: with filters on, "20 rows" says nothing about the filter.
        toolbar={isLoading ? t('common.loading') : t('search.found', { n: total })}
        footer={
          totalPages > 1 && (
            <Pager page={page} totalPages={totalPages} onChange={setPage} label={t('person.title')} />
          )
        }
      >
        {/* A failed search must not read as "nobody in the gia phả", so it gets its own state. */}
        {isError && <TableMessage tone="error">{problemMessage(error, t('common.unexpectedError'))}</TableMessage>}
        {!isLoading && !isError && persons.length === 0 && <TableMessage>{t('person.empty')}</TableMessage>}

        {persons.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('person.name')}</TableHead>
                <TableHead>{t('person.gender')}</TableHead>
                <TableHead>{t('person.generation')}</TableHead>
                <TableHead>{t('person.status')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {persons.map((person) => (
                <TableRow key={person.id}>
                  <TableCell>
                    <Link
                      className="font-heading font-medium underline-offset-4 hover:underline"
                      to={`/persons/${person.id}`}
                    >
                      {person.displayName}
                    </Link>
                  </TableCell>
                  <TableCell>{t(`person.genders.${person.gender}`)}</TableCell>
                  <TableCell className="tabular-nums">{person.generation ?? '—'}</TableCell>
                  <TableCell>
                    <LifeStatusBadge living={person.living} deathRecorded={person.deathRecorded} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TableCard>
    </PageContainer>
  )
}
