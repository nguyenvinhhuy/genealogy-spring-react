import { apiClient } from '@/shared/lib/api-client'

import type { LoginRequest, LoginResponse } from './types'

/**
 * Signs in with email and password, setting the refresh cookie.
 *
 * @param request the credentials to present
 * @returns the access token and the signed-in account
 */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', request)
  return response.data
}
