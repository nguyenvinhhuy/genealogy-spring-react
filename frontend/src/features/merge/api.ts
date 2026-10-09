import { apiClient } from '@/shared/lib/api-client'

import type { MergePayload, MergeResult } from './types'

/**
 * Merges one person into another and deletes the duplicate.
 *
 * @param payload which two people, and on what basis
 * @returns what was moved, folded or dropped
 */
export async function mergePersons(payload: MergePayload): Promise<MergeResult> {
  const response = await apiClient.post<MergeResult>('/merges/persons', payload)
  return response.data
}
