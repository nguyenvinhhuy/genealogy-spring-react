import { Outlet } from 'react-router'

import { AppSidebar } from '@/shared/layouts/app-sidebar'
import { SiteHeader } from '@/shared/layouts/site-header'
import { SidebarInset, SidebarProvider } from '@/shared/ui/sidebar'

/**
 * Frames every signed-in page with the sidebar and header of the shadcn dashboard template.
 *
 * @returns the shell, with the current page in its body
 */
export function AppLayout() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <SiteHeader />
        <div className="@container/main flex flex-1 flex-col px-4 py-6 lg:px-8">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
