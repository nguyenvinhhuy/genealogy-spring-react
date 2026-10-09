import { apiClient } from '@/shared/lib/api-client'
import { HTTP_NOT_FOUND, problemStatus } from '@/shared/lib/problem-detail'

import type { Grave, GravePayload } from './types'

/**
 * Fetches the grave recorded for one person.
 *
 * @param personId the person id
 * @returns the grave, or null when none is recorded or the caller may not see it
 */
export async function fetchGrave(personId: number): Promise<Grave | null> {
  try {
    const response = await apiClient.get<Grave>(`/persons/${personId}/grave`)
    return response.data
  } catch (error) {
    // Most people have no grave recorded; that is an ordinary state, not an error to surface.
    if (problemStatus(error) === HTTP_NOT_FOUND) {
      return null
    }
    throw error
  }
}

/**
 * Records or replaces a person's grave.
 *
 * @param personId the person id
 * @param payload every field of the grave
 * @returns the saved grave
 */
export async function saveGrave(personId: number, payload: GravePayload): Promise<Grave> {
  const response = await apiClient.put<Grave>(`/persons/${personId}/grave`, payload)
  return response.data
}

/**
 * Removes a person's grave record, with the photos and citations that name it (ADMIN).
 *
 * @param personId the person id
 * @param changeNote why the grave is being removed, or null
 */
export async function deleteGrave(personId: number, changeNote: string | null): Promise<void> {
  await apiClient.delete(`/persons/${personId}/grave`, { params: { changeNote: changeNote || undefined } })
}
