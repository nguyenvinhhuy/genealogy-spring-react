import { useQuery } from '@tanstack/react-query'
import { CalendarHeart, ChevronRight, MessageSquarePlus, Search, UserPlus, Users, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { fetchAnniversaries } from '@/features/event/api'
import { AnniversaryCountdown } from '@/features/event/components/anniversary-countdown'
import { LunarLeaf } from '@/features/event/components/lunar-leaf'
import { searchPersons } from '@/features/search/api'
import { fetchPendingCount, SUGGESTION_QUERY_KEY } from '@/features/suggestion/api'
import { IconTooltip } from '@/shared/components/icon-tooltip'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { formatCount, formatSolarDate } from '@/shared/lib/format-moment'
import { cn } from '@/shared/lib/utils'
import { useAuthStore } from '@/shared/store/auth-store'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/shared/ui/card'
import { Skeleton } from '@/shared/ui/skeleton'

// The dashboard looks a month ahead; the full calendar page offers longer windows.
const UPCOMING_DAYS = 30
// How many of the coming giỗ the dashboard lists before pointing at the full page.
const UPCOMING_SHOWN = 5

/** Props of {@link StatCard}. */
interface StatCardProps {
  label: string
  value: number | undefined
  hint: string
  icon: LucideIcon
  to: string
  failed: boolean
}

/**
 * Draws one figure of the dashboard, after the template's section cards.
 *
 * @param props the figure, its label and hint, and where it leads
 * @returns the card
 */
function StatCard({ label, value, hint, icon: Icon, to, failed }: StatCardProps) {
  const { t } = useTranslation()
  return (
    <Card className="@container/card">
      <CardHeader>
        <CardDescription>{label}</CardDescription>
        <CardTitle className="font-heading text-3xl font-semibold tabular-nums">
          {failed ? '—' : value === undefined ? <Skeleton className="h-9 w-16" /> : formatCount(value)}
        </CardTitle>
        <CardAction>
          <span className="bg-accent text-accent-foreground flex size-9 items-center justify-center rounded-lg">
            <Icon className="size-4" aria-hidden />
          </span>
        </CardAction>
      </CardHeader>
      <CardFooter className="text-muted-foreground flex items-center justify-between gap-2 text-sm">
        <span className="line-clamp-1">{failed ? t('dashboard.loadFailed') : hint}</span>
        <IconTooltip label={label}>
          <Link to={to} className="text-foreground hover:text-primary shrink-0" aria-label={label}>
            <ChevronRight className="size-4" aria-hidden />
          </Link>
        </IconTooltip>
      </CardFooter>
    </Card>
  )
}

/** Props of {@link QuickAction}. */
interface QuickActionProps {
  to: string
  icon: LucideIcon
  children: ReactNode
}

/**
 * Draws one shortcut of the dashboard's common tasks.
 *
 * @param props where it leads, its icon and its label
 * @returns the link
 */
function QuickAction({ to, icon: Icon, children }: QuickActionProps) {
  return (
    <Button asChild variant="outline" className="h-auto w-full justify-start gap-3 px-3 py-3">
      <Link to={to}>
        <Icon className="text-primary size-4" aria-hidden />
        <span className="flex-1 text-left">{children}</span>
        <ChevronRight className="text-muted-foreground size-4" aria-hidden />
      </Link>
    </Button>
  )
}

/**
 * Renders the overview shown after sign-in: the clan's figures, the coming giỗ and the common tasks.
 *
 * @returns the page element
 */
export function HomePage() {
  const { t } = useTranslation()
  const member = useAuthStore((state) => state.member)
  const { mayEdit, mayReview } = usePermissions()

  const people = useQuery({
    queryKey: ['persons', 'count'],
    queryFn: async () => (await searchPersons({}, 1, 0)).page.totalElements,
  })
  const upcoming = useQuery({
    queryKey: ['anniversaries', UPCOMING_DAYS],
    queryFn: () => fetchAnniversaries(UPCOMING_DAYS),
  })
  const pending = useQuery({
    queryKey: [SUGGESTION_QUERY_KEY, 'pending-count'],
    queryFn: fetchPendingCount,
  })
  const coming = (upcoming.data ?? []).slice(0, UPCOMING_SHOWN)

  return (
    <PageContainer>
      <PageHeader
        title={t('dashboard.greeting', { name: member?.fullName ?? '' })}
        description={t('dashboard.subtitle')}
        badges={member && <Badge variant="outline">{t(`role.${member.role}`)}</Badge>}
      />

      {/* The template's section cards: each figure on a faint wash rising from the bottom. */}
      <div
        className={cn(
          'grid gap-4 sm:grid-cols-2 xl:grid-cols-3',
          '*:data-[slot=card]:from-primary/6 *:data-[slot=card]:to-card *:data-[slot=card]:bg-linear-to-t',
          '*:data-[slot=card]:shadow-xs dark:*:data-[slot=card]:bg-card',
        )}
      >
        <StatCard
          label={t('dashboard.people')}
          value={people.data}
          hint={t('dashboard.peopleHint')}
          icon={Users}
          to="/persons"
          failed={people.isError}
        />
        <StatCard
          label={t('dashboard.upcoming')}
          value={upcoming.data?.length}
          hint={t('dashboard.upcomingHint')}
          icon={CalendarHeart}
          to="/anniversaries"
          failed={upcoming.isError}
        />
        <StatCard
          label={t('dashboard.pending')}
          value={pending.data}
          hint={t(mayReview ? 'dashboard.pendingHintReviewer' : 'dashboard.pendingHintMember')}
          icon={MessageSquarePlus}
          to="/suggestions"
          failed={pending.isError}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-lg">{t('dashboard.nextGio')}</CardTitle>
            <CardAction>
              <Button asChild variant="ghost" size="sm">
                <Link to="/anniversaries">{t('dashboard.allAnniversaries')}</Link>
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent>
            {upcoming.isLoading && (
              <div className="space-y-3">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            )}
            {upcoming.isError && <p className="text-destructive text-sm">{t('dashboard.loadFailed')}</p>}
            {upcoming.isSuccess && coming.length === 0 && (
              <p className="text-muted-foreground py-6 text-center text-sm">{t('dashboard.nextGioEmpty')}</p>
            )}
            <ul className="divide-y">
              {coming.map((item) => (
                <li key={item.eventId} className="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
                  <LunarLeaf day={item.lunarDay} month={item.lunarMonth} leapMonth={item.leapMonth} size="sm" />
                  <div className="min-w-0 flex-1">
                    <Link
                      to={`/persons/${item.personId}`}
                      className="font-heading truncate font-medium underline-offset-4 hover:underline"
                    >
                      {item.personName}
                    </Link>
                    <p className="text-muted-foreground text-xs">
                      {formatSolarDate(item.nextOccurrence)}
                      {item.yearsSince != null && ` · ${t('anniversary.yearsSince', { n: item.yearsSince })}`}
                    </p>
                    {/* The home copy dropped it: an approximate death date is an approximate giỗ (#41). */}
                    {item.approximate && (
                      <p className="text-muted-foreground text-xs">{t('anniversary.approximate')}</p>
                    )}
                  </div>
                  <AnniversaryCountdown daysUntil={item.daysUntil} />
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t('dashboard.quickActions')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <QuickAction to="/persons" icon={Search}>
              {t('dashboard.searchPeople')}
            </QuickAction>
            {mayEdit && (
              <QuickAction to="/persons/new" icon={UserPlus}>
                {t('dashboard.addPerson')}
              </QuickAction>
            )}
            <QuickAction to="/suggestions" icon={MessageSquarePlus}>
              {t('dashboard.suggest')}
            </QuickAction>
          </CardContent>
        </Card>
      </div>
    </PageContainer>
  )
}
