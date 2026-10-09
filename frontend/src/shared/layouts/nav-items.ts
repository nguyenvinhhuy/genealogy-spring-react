import {
  BookOpen,
  CalendarHeart,
  DatabaseBackup,
  GitBranch,
  History,
  LayoutDashboard,
  MapPin,
  MessageSquarePlus,
  Network,
  ShieldCheck,
  UserCog,
  Users,
  type LucideIcon,
} from 'lucide-react'

import type { Permissions } from '@/shared/hooks/use-permissions'

/** One link in the sidebar. */
export interface NavItem {
  titleKey: string
  url: string
  icon: LucideIcon
  // Shows the pending-suggestion count beside the link.
  showsPendingCount?: boolean
}

/** A labelled group of sidebar links. */
export interface NavGroup {
  labelKey: string
  items: NavItem[]
}

/**
 * Builds the sidebar's groups for what the signed-in member may open, dropping any group left empty.
 *
 * @param permissions what the member may do
 * @returns the groups to draw
 */
export function buildNavGroups(permissions: Permissions): NavGroup[] {
  // Mirrors the home buttons it replaces: EDITOR+ pages stay hidden from a MEMBER because they 403 server-side.
  const { mayAudit, mayManageMembers } = permissions
  const groups: NavGroup[] = [
    {
      labelKey: 'nav.groupGenealogy',
      items: [
        { titleKey: 'nav.overview', url: '/', icon: LayoutDashboard },
        { titleKey: 'person.title', url: '/persons', icon: Users },
        // The tree was reachable only from a person's page, so a newcomer never found the gia phả's own picture.
        { titleKey: 'tree.title', url: '/tree', icon: Network },
        { titleKey: 'anniversary.title', url: '/anniversaries', icon: CalendarHeart },
      ],
    },
    {
      labelKey: 'nav.groupRecords',
      items: [
        // Read-only for a MEMBER: the server lets every role read them, and neither is person data (D6).
        { titleKey: 'branch.title', url: '/branches', icon: GitBranch },
        { titleKey: 'place.title', url: '/places', icon: MapPin },
        ...(mayAudit ? [{ titleKey: 'source.pageTitle', url: '/sources', icon: BookOpen }] : []),
        { titleKey: 'suggestion.title', url: '/suggestions', icon: MessageSquarePlus, showsPendingCount: true },
      ],
    },
    {
      labelKey: 'nav.groupAdmin',
      items: [
        ...(mayAudit
          ? [
              { titleKey: 'quality.title', url: '/quality', icon: ShieldCheck },
              { titleKey: 'audit.pageTitle', url: '/revisions', icon: History },
              { titleKey: 'data.title', url: '/data', icon: DatabaseBackup },
            ]
          : []),
        ...(mayManageMembers ? [{ titleKey: 'member.pageTitle', url: '/members', icon: UserCog }] : []),
      ],
    },
  ]
  return groups.filter((group) => group.items.length > 0)
}

/**
 * Reports whether a sidebar link is the page being shown, counting the pages beneath it.
 *
 * @param url the link's path
 * @param pathname the current path
 * @returns true when the link should read as active
 */
export function isActiveLink(url: string, pathname: string): boolean {
  // "/" would match everything as a prefix, so the overview is active only on itself.
  if (url === '/') {
    return pathname === '/'
  }
  return pathname === url || pathname.startsWith(`${url}/`)
}
