import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router'

import { useAuthStore } from '@/shared/store/auth-store'

/** Where sign-in should return to, carried in the router's location state. */
export interface SignInState {
  from?: string
}

/**
 * Renders its children only for a signed-in caller, redirecting to sign-in otherwise.
 *
 * @param props the element to guard
 * @returns the guarded element or a redirect
 */
export function RequireAuth({ children }: { children: ReactElement }) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const location = useLocation()
  if (accessToken) {
    return children
  }
  // A link to a person page opened while signed out used to land on the home page after signing in (#24).
  const state: SignInState = { from: `${location.pathname}${location.search}${location.hash}` }
  return <Navigate to="/sign-in" replace state={state} />
}
