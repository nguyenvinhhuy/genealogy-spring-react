import { useMutation, useQuery } from '@tanstack/react-query'
import { Network, Pencil } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'

import { RevisionCard } from '@/features/audit/components/revision-card'
import { BRANCH_QUERY_KEY, fetchBranches } from '@/features/branch/api'
import { fetchEvents } from '@/features/event/api'
import { EventDialog } from '@/features/event/components/event-dialog'
import { EventList } from '@/features/event/components/event-list'
import { fetchFamiliesOf } from '@/features/family/api'
import { UnionPanel } from '@/features/family/components/union-panel'
import { fetchGrave } from '@/features/grave/api'
import { GraveCard } from '@/features/grave/components/grave-card'
import { MediaCard } from '@/features/media/components/media-card'
import { deletePerson, fetchPerson } from '@/features/person/api'
import { MergeWithDialog } from '@/features/merge/components/merge-with-dialog'
import { AddRelationDialog } from '@/features/person/components/add-relation-dialog'
import { LinkPersonDialog } from '@/features/person/components/link-person-dialog'
import { LifeStatusBadge } from '@/features/person/components/life-status-badge'
import { isRedacted } from '@/features/person/types'
import { CitationCard } from '@/features/source/components/citation-card'
import { SuggestCard } from '@/features/suggestion/components/suggest-card'
import { KinshipCard } from '@/features/tree/components/kinship-card'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { flattenHierarchy } from '@/shared/lib/hierarchy'
import { initialOf } from '@/shared/lib/initial'
import { HTTP_NOT_FOUND, problemMessage, problemStatus } from '@/shared/lib/problem-detail'
import { invalidateClanData } from '@/shared/lib/query-client'
import { cn } from '@/shared/lib/utils'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

// What makes "Đã mất" a recorded fact rather than the privacy flag's presumption.
const DEATH_TYPES = new Set(['DEATH', 'BURIAL', 'REBURIAL'])

/**
 * Shows one person with their names, events and relations, and the edits the caller may make.
 *
 * @returns the page element
 */
export function PersonDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { id } = useParams()
  const personId = Number(id)
  const { mayEdit, mayDelete, mayAudit } = usePermissions()

  const { data: person, isLoading, isError, error } = useQuery({
    queryKey: ['person', personId],
    queryFn: () => fetchPerson(personId),
    enabled: Number.isFinite(personId),
  })

  const familiesQuery = useQuery({
    queryKey: ['families', personId],
    queryFn: () => fetchFamiliesOf(personId),
    enabled: Number.isFinite(personId),
  })
  const families = familiesQuery.data ?? []

  const eventsQuery = useQuery({
    queryKey: ['events', 'PERSON', personId],
    queryFn: () => fetchEvents('PERSON', personId),
    enabled: Number.isFinite(personId),
  })
  const events = eventsQuery.data ?? []

  // The same query the grave card runs, so it costs no second request; a recorded mộ is a recorded death.
  const graveQuery = useQuery({
    queryKey: ['grave', personId],
    queryFn: () => fetchGrave(personId),
    enabled: Number.isFinite(personId),
  })
  const grave = graveQuery.data

  // The same list every chi picker reads, so naming the person's chi costs nothing more.
  const { data: branches = [] } = useQuery({ queryKey: BRANCH_QUERY_KEY, queryFn: fetchBranches })

  const removal = useMutation({
    mutationFn: (changeNote: string) => deletePerson(personId, changeNote),
    onSuccess: async () => {
      toast.success(t('person.deleted'))
      // Away first, so the refetch does not briefly show this page's own 404 for the person just deleted.
      await navigate('/persons', { replace: true })
      // A purge takes the person's events, media, citations and suggestions with them, wherever those are shown.
      void invalidateClanData()
    },
  })

  if (isLoading) {
    return (
      <PageContainer>
        <p className="text-muted-foreground text-sm">{t('common.loading')}</p>
      </PageContainer>
    )
  }
  // Only a 404 means the person is gone; any other failure says so rather than claiming they never existed.
  if (isError && problemStatus(error) !== HTTP_NOT_FOUND) {
    return (
      <PageContainer>
        <p className="text-destructive text-sm">{problemMessage(error, t('common.unexpectedError'))}</p>
      </PageContainer>
    )
  }
  if (!person) {
    return (
      <PageContainer>
        <PageHeader title={t('person.notFound')} />
      </PageContainer>
    )
  }
  const hidden = isRedacted(person)
  // Unknown when either read failed: "Chưa ghi ngày mất" would then be a claim the page cannot make (#14).
  const deathKnown = !eventsQuery.isError && !graveQuery.isError
  const deathRecorded = events.some((item) => DEATH_TYPES.has(item.type)) || grave?.kind === 'GRAVE'
  const branchId = hidden ? null : person.branchId
  const branchPath =
    branchId == null ? null : (flattenHierarchy(branches).find((option) => option.id === branchId)?.path ?? null)

  return (
    <PageContainer>
      <Card className="from-primary/6 to-card bg-linear-to-br dark:bg-card">
        <CardContent className="flex flex-wrap items-center gap-5">
          {/* A seal with the given name's first letter, standing in for a portrait the way a gia phả stamps one. */}
          <div
            className={cn(
              'bg-seal text-seal-foreground font-heading flex size-16 shrink-0 items-center justify-center',
              'rounded-xl text-3xl font-semibold shadow-sm',
            )}
          >
            {initialOf(person.displayName)}
          </div>
          <div className="min-w-0 flex-1 space-y-2">
            <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">{person.displayName}</h1>
            <div className="flex flex-wrap items-center gap-2">
              {deathKnown && <LifeStatusBadge living={person.living} deathRecorded={deathRecorded} />}
              {person.generation != null && (
                <Badge variant="outline">{t('person.generationN', { n: person.generation })}</Badge>
              )}
              {branchPath && <Badge variant="outline">{branchPath}</Badge>}
              <span className="text-muted-foreground text-sm">{t(`person.genders.${person.gender}`)}</span>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button asChild>
              <Link to={`/tree/${person.id}`}>
                <Network aria-hidden />
                {t('tree.title')}
              </Link>
            </Button>
            {mayEdit && !hidden && (
              <Button asChild variant="outline">
                <Link to={`/persons/${person.id}/edit`}>
                  <Pencil aria-hidden />
                  {t('person.edit')}
                </Link>
              </Button>
            )}
            {/* ADMIN only, like the delete: a merge deletes the duplicate (§8, F11). */}
            {mayDelete && <MergeWithDialog personId={person.id} personName={person.displayName} />}
            {mayDelete && (
              <ConfirmDeleteDialog
                label={t('person.delete')}
                title={t('person.deleteTitle', { name: person.displayName })}
                description={t('person.deleteDescription')}
                pending={removal.isPending}
                onConfirm={(note) => removal.mutateAsync(note)}
              />
            )}
          </div>
        </CardContent>
      </Card>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0 space-y-6">
          {/* Said plainly, or the empty cards a redacted shape leaves behind read as "no data" (§3.6). */}
          {hidden ? (
            <Card>
              <CardHeader>
                <CardTitle>{t('person.redactedTitle')}</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">{t('person.redactedHint')}</p>
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>{t('person.names')}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {person.names.map((name) => (
                  <div key={name.id} className="flex items-center gap-3">
                    <Badge variant="outline">{t(`person.nameTypes.${name.type}`)}</Badge>
                    <span className={name.primary ? 'font-heading font-medium' : ''}>{name.display}</span>
                  </div>
                ))}
                {person.notes && (
                  <p className="text-muted-foreground border-t pt-3 text-sm whitespace-pre-line">{person.notes}</p>
                )}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle>{t('event.title')}</CardTitle>
              {mayEdit && <EventDialog subjectType="PERSON" subjectId={person.id} />}
            </CardHeader>
            <CardContent>
              <EventList
                subjectType="PERSON"
                subjectId={person.id}
                emptyText={t(hidden ? 'person.redactedDates' : 'event.empty')}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle>{t('person.relations')}</CardTitle>
              {/* Reachable from the person, not only from an admin table (§6.1); the tree node reuses it. */}
              {/* Not while the unions failed to load: the dialog would offer to record a marriage already there. */}
              {mayEdit && familiesQuery.isSuccess && (
                <div className="flex flex-wrap gap-2">
                  <AddRelationDialog key={person.id} personId={person.id} families={families} />
                  <LinkPersonDialog key={`link-${person.id}`} personId={person.id} families={families} />
                </div>
              )}
            </CardHeader>
            <CardContent className="space-y-3">
              {familiesQuery.isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}
              {familiesQuery.isError && (
                <p className="text-destructive text-sm">
                  {problemMessage(familiesQuery.error, t('common.unexpectedError'))}
                </p>
              )}
              {familiesQuery.isSuccess && families.length === 0 && (
                <p className="text-muted-foreground text-sm">{t('person.noRelations')}</p>
              )}
              {families.map((family) => (
                <UnionPanel key={family.id} family={family} personId={person.id} />
              ))}
            </CardContent>
          </Card>

          {/* Keyed by person: moving from one page to the next must not carry a half-typed form across (§8.8 #22). */}
          {!hidden && <MediaCard key={`media-${person.id}`} targetType="PERSON" targetId={person.id} />}

          {!hidden && <GraveCard key={`grave-${person.id}`} personId={person.id} />}

          {!hidden && <CitationCard key={`citations-${person.id}`} targetType="PERSON" targetId={person.id} />}
        </div>

        <aside className="min-w-0 space-y-6">
          <KinshipCard personId={person.id} personName={person.displayName} />

          {/* Keyed by person: a note typed on one person was filed against the next one opened (§8.9 #3). */}
          <SuggestCard key={`suggest-${person.id}`} personId={person.id} families={families} />

          {mayAudit && <RevisionCard entityType="PERSON" entityId={person.id} />}
        </aside>
      </div>
    </PageContainer>
  )
}
