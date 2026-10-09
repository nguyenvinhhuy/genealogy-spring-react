import type { QueryClient } from '@tanstack/react-query'

import { apiClient } from '@/shared/lib/api-client'

import type {
  AddRelationPayload,
  AddRelationResult,
  Family,
  FamilyChildUpdatePayload,
  FamilyPayload,
  LinkRelationPayload,
} from './types'

// A union or link feeds đời, charts, kinship, quality and history; deleting a union takes its citations and photos too.
const AFFECTED_BY_A_LINK = [
  'families',
  'person',
  'persons',
  'tree',
  'kinship',
  'quality',
  'revisions',
  'events',
  'citations',
  'sources',
  'media',
] as const

/**
 * Refetches everything a union or child-link write can change.
 *
 * @param queryClient the app's query client
 */
export async function invalidateFamilyQueries(queryClient: QueryClient): Promise<void> {
  // Exported with the API so the add-relation dialog in `person` and the panels here refresh the same set.
  await Promise.all(AFFECTED_BY_A_LINK.map((key) => queryClient.invalidateQueries({ queryKey: [key] })))
}

/**
 * Lists every union one person belongs to, vợ cả first.
 *
 * @param personId the person id
 * @returns that person's unions
 */
export async function fetchFamiliesOf(personId: number): Promise<Family[]> {
  const response = await apiClient.get<Family[]>('/families', { params: { personId } })
  return response.data
}

/**
 * Updates a union's partners, status or position among a person's unions.
 *
 * @param familyId the union id
 * @param payload the new values, carrying the version the edit was made from
 * @returns the updated union
 */
export async function updateFamily(familyId: number, payload: FamilyPayload): Promise<Family> {
  const response = await apiClient.put<Family>(`/families/${familyId}`, payload)
  return response.data
}

/**
 * Deletes a union with its child links, events, citations and media.
 *
 * @param familyId the union id
 * @param changeNote why it is being deleted, for the change history
 */
export async function deleteFamily(familyId: number, changeNote?: string): Promise<void> {
  await apiClient.delete(`/families/${familyId}`, { params: { changeNote: changeNote || undefined } })
}

/**
 * Adds a new spouse or child to a person, creating the new person and the link in one request.
 *
 * @param personId the person being added to
 * @param payload the new person and the kind of relation
 * @returns the new person's id and the union they joined
 */
export async function addRelation(personId: number, payload: AddRelationPayload): Promise<AddRelationResult> {
  const response = await apiClient.post<AddRelationResult>(`/persons/${personId}/relations`, payload)
  return response.data
}

/**
 * Links two people who are both already recorded, as spouses or as parent and child.
 *
 * @param personId the person the link is made from
 * @param payload who the other person is, what they are to them, and which union
 * @returns the other person's id and the union that now links them
 */
export async function linkExisting(personId: number, payload: LinkRelationPayload): Promise<AddRelationResult> {
  const response = await apiClient.post<AddRelationResult>(`/persons/${personId}/links`, payload)
  return response.data
}

/**
 * Corrects how a linked child relates to each partner, and their birth order.
 *
 * @param familyId the union id
 * @param childId the linked child
 * @param payload the corrected link
 * @returns the updated union
 */
export async function updateChildLink(
  familyId: number,
  childId: number,
  payload: FamilyChildUpdatePayload,
): Promise<Family> {
  const response = await apiClient.put<Family>(`/families/${familyId}/children/${childId}`, payload)
  return response.data
}

/**
 * Unlinks a child from a union.
 *
 * @param familyId the union id
 * @param childId the person to unlink
 * @param changeNote why they are being unlinked, for the change history
 */
export async function removeChild(familyId: number, childId: number, changeNote?: string): Promise<void> {
  await apiClient.delete(`/families/${familyId}/children/${childId}`, {
    params: { changeNote: changeNote || undefined },
  })
}
