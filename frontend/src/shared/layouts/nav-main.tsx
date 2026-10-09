import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router'

import { isActiveLink, type NavGroup } from '@/shared/layouts/nav-items'
import { cn } from '@/shared/lib/utils'
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/shared/ui/sidebar'

/** Props of {@link NavMain}. */
interface NavMainProps {
  group: NavGroup
  pendingCount: number
}

/**
 * Draws one labelled group of sidebar links, marking the page being shown.
 *
 * @param props the group and the pending-suggestion count for its badge
 * @returns the group
 */
export function NavMain({ group, pendingCount }: NavMainProps) {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const { isMobile, setOpenMobile } = useSidebar()

  return (
    <SidebarGroup>
      <SidebarGroupLabel>{t(group.labelKey)}</SidebarGroupLabel>
      <SidebarMenu>
        {group.items.map((item) => {
          const title = t(item.titleKey)
          return (
            <SidebarMenuItem key={item.url}>
              {/* The tooltip names the link once the sidebar is folded down to its icons. */}
              <SidebarMenuButton asChild tooltip={title} isActive={isActiveLink(item.url, pathname)}>
                {/* On a phone the sidebar is a sheet over the page, and it stayed over the page just opened (#25). */}
                <Link to={item.url} onClick={() => isMobile && setOpenMobile(false)}>
                  <item.icon aria-hidden />
                  <span>{title}</span>
                </Link>
              </SidebarMenuButton>
              {item.showsPendingCount && pendingCount > 0 && (
                <SidebarMenuBadge
                  className={cn(
                    'bg-seal text-seal-foreground',
                    'peer-hover/menu-button:text-seal-foreground peer-data-active/menu-button:text-seal-foreground',
                  )}
                  aria-label={t('suggestion.pendingCount', { count: pendingCount })}
                >
                  {pendingCount}
                </SidebarMenuBadge>
              )}
            </SidebarMenuItem>
          )
        })}
      </SidebarMenu>
    </SidebarGroup>
  )
}
