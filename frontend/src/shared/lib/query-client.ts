import { QueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { toast } from 'sonner'

import i18n from '@/shared/i18n'
import { problemMessage, problemStatus } from '@/shared/lib/problem-detail'
import { useAuthStore } from '@/shared/store/auth-store'

// How long a fetched answer is reused before a screen asks again; a gia phả changes slowly.
const STALE_MS = 30_000

// Attempts a failed query gets in all, when the failure is worth retrying.
const MAX_ATTEMPTS = 3

// The status a stale edit is refused with (§8.6).
const CONFLICT = 409

/**
 * Decides whether a failed query is worth attempting again.
 *
 * @param failureCount how many attempts have already failed
 * @param error what the last attempt threw
 * @returns true to try again
 */
function retryUnlessTheServerSaidNo(failureCount: number, error: Error): boolean {
  // A 404 will not become a 200 on the third try: retrying one only holds "Đang tải…" on screen for 7 seconds.
  const status = isAxiosError(error) ? error.response?.status : undefined
  if (status !== undefined && status >= 400 && status < 500) {
    return false
  }
  return failureCount < MAX_ATTEMPTS
}

/**
 * Shows why a write failed, in the server's own words.
 *
 * @param error what the mutation threw
 */
function toastFailure(error: Error): void {
  // The default for every mutation: 32 of them each wrote this line, and a mutation with its own onError replaces it.
  toast.error(problemMessage(error, i18n.t('common.unexpectedError')))
  // A refused stale edit means the cache is behind: reload it, or reopening the form would send the old version again.
  if (problemStatus(error) === CONFLICT) {
    void invalidateClanData()
  }
}

/** The app's one query cache. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: retryUnlessTheServerSaidNo, staleTime: STALE_MS },
    mutations: { onError: toastFailure },
  },
})

/**
 * Marks every cached answer about the gia phả stale, for a write that reaches across the whole clan.
 *
 * @returns once the screens on view have refetched
 */
export function invalidateClanData(): Promise<void> {
  // One rule for import, merge and delete: three hand-kept key lists had drifted, and one missed sources (#16).
  return queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'members' })
}

useAuthStore.subscribe((state, previous) => {
  // An ADMIN's unredacted answers must not be shown to whoever signs in next on this browser (§8.11 #9).
  const sameViewer =
    state.member?.id === previous.member?.id && state.member?.role === previous.member?.role
  if (!sameViewer) {
    queryClient.clear()
  }
})
