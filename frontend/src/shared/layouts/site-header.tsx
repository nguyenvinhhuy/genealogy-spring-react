import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useMatches } from 'react-router'

import { IconTooltip } from '@/shared/components/icon-tooltip'
import { LanguageToggle } from '@/shared/components/language-toggle'
import { ModeToggle } from '@/shared/components/mode-toggle'
import { TextSizeToggle } from '@/shared/components/text-size-toggle'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/shared/ui/breadcrumb'
import { Separator } from '@/shared/ui/separator'
import { SidebarTrigger } from '@/shared/ui/sidebar'

/** One step of the breadcrumb a route declares in its `handle`. */
export interface Crumb {
  titleKey: string
  to?: string
}

/** What a route may declare in its `handle`. */
export interface RouteHandle {
  crumbs?: Crumb[]
}

/**
 * Reads the breadcrumb of the deepest route that declares one.
 *
 * @param handles the `handle` of every matched route, outermost first
 * @returns the crumbs, or none
 */
function crumbsOf(handles: unknown[]): Crumb[] {
  for (let index = handles.length - 1; index >= 0; index--) {
    const crumbs = (handles[index] as RouteHandle | undefined)?.crumbs
    if (crumbs) {
      return crumbs
    }
  }
  return []
}

/**
 * Draws the bar above every page: the sidebar toggle, where the reader is, and the language and theme switches.
 *
 * @returns the header
 */
export function SiteHeader() {
  const { t } = useTranslation()
  const crumbs = crumbsOf(useMatches().map((match) => match.handle))
  const toggleLabel = t('nav.toggleSidebar')

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear">
      <div className="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
        <IconTooltip label={toggleLabel}>
          <SidebarTrigger className="-ml-1" aria-label={toggleLabel} />
        </IconTooltip>
        <Separator orientation="vertical" className="mx-2 data-vertical:h-4" />
        <Breadcrumb className="min-w-0">
          <BreadcrumbList className="flex-nowrap">
            <BreadcrumbItem className="hidden sm:inline-flex">
              <BreadcrumbLink asChild>
                <Link to="/">{t('app.name')}</Link>
              </BreadcrumbLink>
            </BreadcrumbItem>
            {crumbs.map((crumb, index) => (
              <Fragment key={crumb.titleKey}>
                <BreadcrumbSeparator className={index === 0 ? 'hidden sm:block' : undefined} />
                <BreadcrumbItem className="min-w-0">
                  {crumb.to && index < crumbs.length - 1 ? (
                    <BreadcrumbLink asChild>
                      <Link to={crumb.to}>{t(crumb.titleKey)}</Link>
                    </BreadcrumbLink>
                  ) : (
                    <BreadcrumbPage className="truncate">{t(crumb.titleKey)}</BreadcrumbPage>
                  )}
                </BreadcrumbItem>
              </Fragment>
            ))}
          </BreadcrumbList>
        </Breadcrumb>
        <div className="ml-auto flex items-center gap-1">
          <TextSizeToggle />
          <LanguageToggle />
          <ModeToggle />
        </div>
      </div>
    </header>
  )
}
