import { createBrowserRouter, type RouteObject } from 'react-router'

import { RequireAuth } from '@/shared/components/require-auth'
import { AppLayout } from '@/shared/layouts/app-layout'
import type { Crumb, RouteHandle } from '@/shared/layouts/site-header'

/** A module that exports exactly one page component. */
type PageModule = Record<string, React.ComponentType>

/**
 * Builds a route whose page is fetched only when the route is first visited.
 *
 * @param path the route path
 * @param load imports the page module
 * @param name the exported component's name inside that module
 * @param crumbs the breadcrumb the header shows for this page
 * @returns the route definition
 */
function lazyRoute(path: string, load: () => Promise<PageModule>, name: string, crumbs: Crumb[]): RouteObject {
  // Every page is split, sign-in and home included: zod came with sign-in, and a signed-in reload never needs it.
  const handle: RouteHandle = { crumbs }
  return {
    path,
    handle,
    lazy: async () => ({ Component: (await load())[name] }),
  }
}

const PERSONS: Crumb = { titleKey: 'person.title', to: '/persons' }

export const router = createBrowserRouter([
  {
    path: '/sign-in',
    lazy: async () => ({ Component: (await import('@/features/auth/pages/sign-in-page')).SignInPage }),
  },
  {
    // One guard and one shell for every signed-in page, instead of a RequireAuth around each.
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      lazyRoute('/', () => import('@/features/home/pages/home-page'), 'HomePage', [{ titleKey: 'nav.overview' }]),
      lazyRoute('/persons', () => import('@/features/person/pages/person-list-page'), 'PersonListPage', [
        { titleKey: 'person.title' },
      ]),
      lazyRoute('/persons/new', () => import('@/features/person/pages/person-create-page'), 'PersonCreatePage', [
        PERSONS,
        { titleKey: 'nav.crumbNew' },
      ]),
      lazyRoute('/persons/:id', () => import('@/features/person/pages/person-detail-page'), 'PersonDetailPage', [
        PERSONS,
        { titleKey: 'nav.crumbDetail' },
      ]),
      lazyRoute('/persons/:id/edit', () => import('@/features/person/pages/person-edit-page'), 'PersonEditPage', [
        PERSONS,
        { titleKey: 'nav.crumbEdit' },
      ]),
      lazyRoute('/tree', () => import('@/features/tree/pages/tree-start-page'), 'TreeStartPage', [
        { titleKey: 'tree.title' },
      ]),
      lazyRoute('/tree/:id', () => import('@/features/tree/pages/tree-page'), 'TreePage', [
        { titleKey: 'tree.title', to: '/tree' },
        { titleKey: 'nav.crumbDetail' },
      ]),
      lazyRoute('/anniversaries', () => import('@/features/event/pages/anniversary-page'), 'AnniversaryPage', [
        { titleKey: 'anniversary.title' },
      ]),
      lazyRoute('/quality', () => import('@/features/quality/pages/quality-page'), 'QualityPage', [
        { titleKey: 'quality.title' },
      ]),
      lazyRoute('/branches', () => import('@/features/branch/pages/branch-page'), 'BranchPage', [
        { titleKey: 'branch.title' },
      ]),
      lazyRoute('/places', () => import('@/features/place/pages/place-page'), 'PlacePage', [
        { titleKey: 'place.title' },
      ]),
      lazyRoute('/sources', () => import('@/features/source/pages/source-page'), 'SourcePage', [
        { titleKey: 'source.pageTitle' },
      ]),
      lazyRoute('/revisions', () => import('@/features/audit/pages/revision-page'), 'RevisionPage', [
        { titleKey: 'audit.pageTitle' },
      ]),
      lazyRoute('/suggestions', () => import('@/features/suggestion/pages/suggestion-page'), 'SuggestionPage', [
        { titleKey: 'suggestion.title' },
      ]),
      lazyRoute('/data', () => import('@/features/gedcom/pages/data-page'), 'DataPage', [
        { titleKey: 'data.title' },
      ]),
      lazyRoute('/members', () => import('@/features/member/pages/member-page'), 'MemberPage', [
        { titleKey: 'member.pageTitle' },
      ]),
      lazyRoute('*', () => import('@/features/home/pages/not-found-page'), 'NotFoundPage', [
        { titleKey: 'notFound.crumb' },
      ]),
    ],
  },
])
