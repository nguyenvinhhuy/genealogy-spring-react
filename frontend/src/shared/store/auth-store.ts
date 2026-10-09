import { create } from 'zustand'

import type { Member } from '@/shared/types/session'

/** The signed-in session as the app holds it, and the ways to change it. */
interface AuthState {
  // Access token, kept in memory only - the refresh token lives in an HttpOnly cookie.
  accessToken: string | null
  member: Member | null
  setSession: (accessToken: string, member: Member) => void
  clear: () => void
}

/** The one store of who is signed in, read by the API client and every guarded screen. */
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  member: null,
  setSession: (accessToken, member) => set({ accessToken, member }),
  clear: () => set({ accessToken: null, member: null }),
}))
