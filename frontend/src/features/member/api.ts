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
 * Renames an account, changes its role or disables it, which signs it out everywhere when the access changes.
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
