import { apiClient } from '@/shared/lib/api-client'
import type { Page } from '@/shared/types/page'

import type {
  ChangePasswordRequest,
  CreateMemberRequest,
  Member,
  ResetPasswordRequest,
  UpdateMemberRequest,
} from './types'

export const MEMBER_QUERY_KEY = 'members'

/**
 * Fetches one page of every account, ordered by name, for the clan head.
 *
 * @param page the zero-based page number
 * @returns that page of accounts
 */
export async function fetchMembers(page: number): Promise<Page<Member>> {
  const response = await apiClient.get<Page<Member>>('/members', { params: { page } })
  return response.data
}

/**
 * Opens an account, for the clan head.
 *
 * @param request the account to open
 * @returns the account as created
 */
export async function createMember(request: CreateMemberRequest): Promise<Member> {
  const response = await apiClient.post<Member>('/members', request)
  return response.data
}

/**
 * Renames an account, changes its email or role, or disables it; any but a rename signs it out everywhere.
 *
 * @param memberId the account to change
 * @param request the account's new state
 * @returns the account as it is now
 */
export async function updateMember(memberId: number, request: UpdateMemberRequest): Promise<Member> {
  const response = await apiClient.put<Member>(`/members/${memberId}`, request)
  return response.data
}

/**
 * Deletes an account by hiding it, which signs it out everywhere and keeps its name on the changes it made.
 *
 * @param memberId the account to delete
 * @param changeNote why it is being deleted, or null
 */
export async function deleteMember(memberId: number, changeNote: string | null): Promise<void> {
  await apiClient.delete(`/members/${memberId}`, { params: { changeNote: changeNote || undefined } })
}

/**
 * Changes the caller's own password, which ends every session they hold, this one included.
 *
 * @param request the current and new passwords
 */
export async function changeOwnPassword(request: ChangePasswordRequest): Promise<void> {
  await apiClient.post('/members/me/password', request)
}

/**
 * Sets another member's password, which signs them out everywhere.
 *
 * @param memberId the account whose password is reset
 * @param request the password to set
 */
export async function resetPassword(memberId: number, request: ResetPasswordRequest): Promise<void> {
  await apiClient.post(`/members/${memberId}/password`, request)
}
