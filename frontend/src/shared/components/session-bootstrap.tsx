import { useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { refreshSession } from '@/shared/lib/session'
import { useAuthStore } from '@/shared/store/auth-store'

/**
 * Restores the session from the HttpOnly refresh cookie before any route renders.
 *
 * @param props the tree to render once the session has been settled
 * @returns the children, or a loading placeholder while the refresh is in flight
 */
export function SessionBootstrap({ children }: { children: ReactNode }) {
  // The access token lives in memory, so without this gate a reload bounces a signed-in user to sign-in.
  const { t } = useTranslation()
  const clear = useAuthStore((state) => state.clear)
  const [settled, setSettled] = useState(false)

  useEffect(() => {
    let cancelled = false

    refreshSession()
      // A missing or expired cookie is the normal signed-out case, not an error worth surfacing.
      .catch(() => {
        if (!cancelled) {
          clear()
        }
      })
      .finally(() => {
        if (!cancelled) {
          setSettled(true)
        }
      })

    return () => {
      cancelled = true
    }
  }, [clear])

  if (!settled) {
    return (
      <div className="text-muted-foreground flex min-h-screen items-center justify-center text-sm">
        {t('common.loading')}
      </div>
    )
  }
  return <>{children}</>
}
