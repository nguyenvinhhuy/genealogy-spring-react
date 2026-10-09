import type { PersonSummary } from '@/features/person/types'
import { apiClient } from '@/shared/lib/api-client'
import type { Page } from '@/shared/types/page'

import type { PersonSearchFilters } from './types'

/**
 * Finds the people matching every filter that was given.
 *
 * @param filters the filters to apply
 * @param size how many rows to return
 * @param page which page to return, counting from zero
 * @returns one page of matching people, ordered by tên then tên đệm then họ
 */
export async function searchPersons(
  filters: PersonSearchFilters,
  size: number,
  page: number,
): Promise<Page<PersonSummary>> {
  const response = await apiClient.get<Page<PersonSummary>>('/search/persons', {
    params: { ...filters, size, page },
  })
  return response.data
}
