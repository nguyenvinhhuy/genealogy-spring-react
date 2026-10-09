import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'

import { createPerson } from '@/features/person/api'
import { PersonForm } from '@/features/person/components/person-form'
import type { PersonPayload } from '@/features/person/types'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { Card, CardContent } from '@/shared/ui/card'

/**
 * Creates a new person.
 *
 * @returns the page element
 */
export function PersonCreatePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: (payload: PersonPayload) => createPerson(payload),
    onSuccess: async (person) => {
      await queryClient.invalidateQueries({ queryKey: ['persons'] })
      toast.success(t('person.created'))
      void navigate(`/persons/${person.id}`, { replace: true })
    },
  })

  return (
    <PageContainer width="narrow">
      <PageHeader title={t('person.add')} />
      <Card>
        <CardContent>
          <PersonForm
            submitting={mutation.isPending}
            onSubmit={(payload) => mutation.mutate(payload)}
            onCancel={() => void navigate('/persons')}
          />
        </CardContent>
      </Card>
    </PageContainer>
  )
}
