import { apiClient } from '@/shared/lib/api-client'

import type { PersonDetail, PersonNode, PersonPayload, PersonView } from './types'

// The server's own cap on `/persons/nodes` (`PersonController.MAX_NODES`).
export const MAX_NODES_PER_REQUEST = 200

// Listing and searching live in the search feature: `GET /persons` duplicated it and was removed on 2026-09-24.

/**
 * Fetches one person, in full or redacted according to the caller's role (§3.6).
 *
 * @param id the person id
 * @returns the person
 */
export async function fetchPerson(id: number): Promise<PersonView> {
  const response = await apiClient.get<PersonView>(`/persons/${id}`)
  return response.data
}

/**
 * Fetches the name, sex, đời and living flag of several people in one request.
 *
 * @param ids the people to look up, at most {@link MAX_NODES_PER_REQUEST}
 * @returns one node per person found
 */
export async function fetchPersonNodes(ids: number[]): Promise<PersonNode[]> {
  const response = await apiClient.get<PersonNode[]>('/persons/nodes', {
    params: { ids: ids.join(',') },
  })
  return response.data
}

/**
 * Creates a person together with their names.
 *
 * @param payload the person to create
 * @returns the created person
 */
export async function createPerson(payload: PersonPayload): Promise<PersonDetail> {
  const response = await apiClient.post<PersonDetail>('/persons', payload)
  return response.data
}

/**
 * Replaces a person's fields and name list.
 *
 * @param id the person id
 * @param payload the new values
 * @returns the updated person
 */
export async function updatePerson(id: number, payload: PersonPayload): Promise<PersonDetail> {
  const response = await apiClient.put<PersonDetail>(`/persons/${id}`, payload)
  return response.data
}

/**
 * Deletes a person together with every row that names them.
 *
 * @param id the person id
 * @param changeNote why they are being deleted, for the change history
 */
export async function deletePerson(id: number, changeNote?: string): Promise<void> {
  await apiClient.delete(`/persons/${id}`, { params: { changeNote: changeNote || undefined } })
}
