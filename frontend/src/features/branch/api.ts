import type { QueryClient } from '@tanstack/react-query'

import { apiClient } from '@/shared/lib/api-client'

import type { Branch, BranchPayload } from './types'

// The one query key every branch write invalidates, so a new picker never reads a stale list (§9).
export const BRANCH_QUERY_KEY = ['branches'] as const

/**
 * Lists every branch of the clan, by name.
 *
 * @returns the branches
 */
export async function fetchBranches(): Promise<Branch[]> {
  const response = await apiClient.get<Branch[]>('/branches')
  return response.data
}

/**
 * Creates a branch.
 *
 * @param payload the branch to create
 * @returns the created branch
 */
export async function createBranch(payload: BranchPayload): Promise<Branch> {
  const response = await apiClient.post<Branch>('/branches', payload)
  return response.data
}

/**
 * Updates a branch.
 *
 * @param id branch id
 * @param payload the new values
 * @returns the updated branch
 */
export async function updateBranch(id: number, payload: BranchPayload): Promise<Branch> {
  const response = await apiClient.put<Branch>(`/branches/${id}`, payload)
  return response.data
}

/**
 * Deletes a branch that has no members and no sub-branches.
 *
 * @param id branch id
 * @param changeNote why the branch is being deleted, or null
 */
export async function deleteBranch(id: number, changeNote: string | null): Promise<void> {
  await apiClient.delete(`/branches/${id}`, { params: { changeNote: changeNote || undefined } })
}

/**
 * Refetches every branch list and picker after a branch write.
 *
 * @param queryClient the app's query client
 */
export async function invalidateBranchQueries(queryClient: QueryClient): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: BRANCH_QUERY_KEY })
}
