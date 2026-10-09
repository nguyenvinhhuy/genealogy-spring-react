import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'

import { fetchPerson, updatePerson } from '@/features/person/api'
import { PersonForm } from '@/features/person/components/person-form'
import { isRedacted, type PersonPayload } from '@/features/person/types'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { problemMessage, problemStatus } from '@/shared/lib/problem-detail'
import { Card, CardContent } from '@/shared/ui/card'

// A stale form is refused with 409 (§8.6): the message says so, and the page reloads the newer version.
const CONFLICT = 409

/**
 * Edits one person's names, sex, chi and notes.
 *
 * @returns the page element
 */
export function PersonEditPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { id } = useParams()
  const personId = Number(id)

  const { data: person, isLoading, isError, error } = useQuery({
    queryKey: ['person', personId],
    queryFn: () => fetchPerson(personId),
    enabled: Number.isFinite(personId),
    // Only a 409 reloads this record: a focus refetch would remount the form and wipe what is being typed (§8.12 #1).
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })

  const mutation = useMutation({
    mutationFn: (payload: PersonPayload) => updatePerson(personId, payload),
    onSuccess: async () => {
      await Promise.all(
        // The giỗ list carries each person's name, so a rename shows there too.
        ['person', 'persons', 'tree', 'kinship', 'quality', 'revisions', 'anniversaries'].map((key) =>
          queryClient.invalidateQueries({ queryKey: [key] }),
        ),
      )
      toast.success(t('person.updated'))
      void navigate(`/persons/${personId}`, { replace: true })
    },
    onError: async (failure) => {
      toast.error(problemMessage(failure, t('common.unexpectedError')))
      if (problemStatus(failure) === CONFLICT) {
        await queryClient.invalidateQueries({ queryKey: ['person', personId] })
      }
    },
  })

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">{t('common.loading')}</p>
  }
  if (isError) {
    return <p className="text-destructive text-sm">{problemMessage(error, t('common.unexpectedError'))}</p>
  }
  // An EDITOR always gets the full record; a redacted one here means the role changed under the page.
  if (!person || isRedacted(person)) {
    return <p className="text-sm">{t('person.notFound')}</p>
  }

  return (
    <PageContainer width="narrow">
      <PageHeader title={t('person.editTitle', { name: person.displayName })} />
      <Card>
        <CardContent>
          <PersonForm
            // Keyed by version, so a 409 that reloads the newer record also resets the form onto it.
            key={person.version}
            person={person}
            submitting={mutation.isPending}
            onSubmit={(payload) => mutation.mutate(payload)}
            onCancel={() => void navigate(`/persons/${personId}`)}
          />
        </CardContent>
      </Card>
    </PageContainer>
  )
}
