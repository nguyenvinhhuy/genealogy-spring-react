import axios from 'axios'

import { useAuthStore } from '@/shared/store/auth-store'
import type { LoginResponse } from '@/shared/types/session'

const API_BASE = '/api/v1'

// The name every tab locks while it exchanges the shared refresh cookie.
const REFRESH_LOCK = 'genealogy.session-refresh'

// How long a refresh may take before it gives up, since every other tab waits on its lock meanwhile.
const REFRESH_TIMEOUT_MS = 15_000

// The refresh in flight in this tab, so concurrent callers share one request.
let refreshInFlight: Promise<LoginResponse> | null = null

/**
 * Exchanges the HttpOnly refresh cookie for a new session and stores it, one request at a time across all tabs.
 *
 * @returns the new access token and the signed-in account
 */
export function refreshSession(): Promise<LoginResponse> {
  // Two refreshes of one cookie make the loser look like a theft, and the server then ends every session (§8.2).
  refreshInFlight ??= withRefreshLock(() =>
    axios.post<LoginResponse>(`${API_BASE}/auth/refresh`, null, {
      withCredentials: true,
      timeout: REFRESH_TIMEOUT_MS,
    }),
  )
    .then((response) => {
      // The account comes back too: a role changed since sign-in must not linger in the menu (§8.11 #10).
      useAuthStore.getState().setSession(response.data.accessToken, response.data.member)
      return response.data
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

/**
 * Signs the caller out on the server, then ends the session here whatever the server answered.
 */
export async function endSession(): Promise<void> {
  // The cookie may already be gone server-side; the local session ends either way.
  await axios.post(`${API_BASE}/auth/logout`, null, { withCredentials: true }).catch(() => undefined)
  useAuthStore.getState().clear()
}

/**
 * Runs a refresh while holding a lock every tab of this site shares, when the browser has one.
 *
 * @param refresh the request to run
 * @returns what the request returned
 */
function withRefreshLock<T>(refresh: () => Promise<T>): Promise<T> {
  // A second tab waits, then sends the cookie the first one rotated, instead of the one it just spent.
  if (typeof navigator !== 'undefined' && 'locks' in navigator) {
    return navigator.locks.request(REFRESH_LOCK, refresh)
  }
  return refresh()
}
