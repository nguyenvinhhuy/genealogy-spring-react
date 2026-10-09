import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

import { problemStatus } from '@/shared/lib/problem-detail'
import { refreshSession } from '@/shared/lib/session'
import { useAuthStore } from '@/shared/store/auth-store'

const UNAUTHORIZED = 401

// A 401 from these is the answer itself: a wrong password at sign-in is not an expired session (§8.11 #7).
const AUTH_PATHS = new Set(['/auth/login', '/auth/refresh', '/auth/logout'])

/** The one HTTP client of the app, carrying the access token and renewing it once on a 401. */
export const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined

    const isRetryable =
      error.response?.status === UNAUTHORIZED &&
      request !== undefined &&
      request._retried !== true &&
      !AUTH_PATHS.has(request.url ?? '')

    if (!isRetryable) {
      return Promise.reject(error)
    }

    request._retried = true
    try {
      const session = await refreshSession()
      request.headers = { ...request.headers, Authorization: `Bearer ${session.accessToken}` }
    } catch (refreshError) {
      // Only a refused cookie ends the session; a dropped connection or a 5xx must not sign anyone out (#8).
      if (problemStatus(refreshError) === UNAUTHORIZED) {
        useAuthStore.getState().clear()
      }
      return Promise.reject(error)
    }
    // Outside the try: a 409 or 400 on the retried request is that request's own answer, not a dead session.
    return apiClient.request(request)
  },
)
