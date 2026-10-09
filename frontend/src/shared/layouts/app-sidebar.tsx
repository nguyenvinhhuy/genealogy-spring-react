import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { fetchPendingCount, SUGGESTION_QUERY_KEY } from '@/features/suggestion/api'
import { Logo } from '@/shared/components/logo'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { buildNavGroups } from '@/shared/layouts/nav-items'
import { NavMain } from '@/shared/layouts/nav-main'
import { NavUser } from '@/shared/layouts/nav-user'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/shared/ui/sidebar'

/**
 * Draws the app's sidebar: the seal and clan name, the role-aware links, and the signed-in member.
 *
 * @returns the sidebar
 */
export function AppSidebar() {
  const { t } = useTranslation()
  const permissions = usePermissions()
  const groups = buildNavGroups(permissions)
  // A reviewer's backlog, or a member's own still waiting: the same count the queue page shows (§8.9 #17).
  const { data: pendingCount = 0 } = useQuery({
    queryKey: [SUGGESTION_QUERY_KEY, 'pending-count'],
    queryFn: fetchPendingCount,
  })

  return (
    <Sidebar variant="inset" collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            {/* The tooltip names the link once the sidebar is folded down to the seal alone. */}
            <SidebarMenuButton size="lg" asChild tooltip={t('nav.overview')}>
              <Link to="/">
                {/* `!`: the menu button forces every svg inside it to the size of an icon. */}
                <Logo className="size-8! shrink-0" />
                <div className="grid flex-1 text-left leading-tight">
                  <span className="font-heading truncate text-base font-semibold">{t('app.name')}</span>
                  <span className="text-muted-foreground truncate text-xs">{t('app.tagline')}</span>
                </div>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {groups.map((group) => (
          <NavMain key={group.labelKey} group={group} pendingCount={pendingCount} />
        ))}
      </SidebarContent>
      <SidebarFooter>
        <NavUser />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
