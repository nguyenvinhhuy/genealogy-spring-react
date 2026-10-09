import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'

import { router } from '@/router'
import { SessionBootstrap } from '@/shared/components/session-bootstrap'
import { ThemeProvider } from '@/shared/components/theme-provider'
import { queryClient } from '@/shared/lib/query-client'
import { Toaster } from '@/shared/ui/sonner'
import { TooltipProvider } from '@/shared/ui/tooltip'

/**
 * Renders the application shell and its providers.
 *
 * @returns the app element
 */
export default function App() {
  return (
    <ThemeProvider>
      {/* App-wide, so the icon buttons on the sign-in page get their tooltips as well as the signed-in shell. */}
      <TooltipProvider delayDuration={300}>
        <QueryClientProvider client={queryClient}>
          <SessionBootstrap>
            <RouterProvider router={router} />
          </SessionBootstrap>
          <Toaster position="top-right" />
        </QueryClientProvider>
      </TooltipProvider>
    </ThemeProvider>
  )
}
