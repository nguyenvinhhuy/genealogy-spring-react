import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { fetchAnniversaries } from '@/features/event/api'
import { AnniversaryCountdown } from '@/features/event/components/anniversary-countdown'
import { LunarLeaf } from '@/features/event/components/lunar-leaf'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { formatSolarDate } from '@/shared/lib/format-moment'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Card, CardContent } from '@/shared/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'

const WINDOWS = [30, 60, 90, 180, 365]
const DEFAULT_WINDOW = 60

/**
 * Lists upcoming ngày giỗ, soonest first.
 *
 * @returns the page element
 */
export function AnniversaryPage() {
  const { t } = useTranslation()
  const [days, setDays] = useState(DEFAULT_WINDOW)

  const { data = [], isLoading, isError, error } = useQuery({
    queryKey: ['anniversaries', days],
    queryFn: () => fetchAnniversaries(days),
  })
  // A list that failed to load must not read as "no giỗ coming up", so failure gets its own state.
  const empty = !isLoading && !isError && data.length === 0

  return (
    <PageContainer width="narrow">
      <PageHeader
        title={t('anniversary.title')}
        description={t('anniversary.subtitle')}
        actions={
          <Select value={String(days)} onValueChange={(next) => setDays(Number(next))}>
            <SelectTrigger className="w-40" aria-label={t('anniversary.window')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {WINDOWS.map((value) => (
                <SelectItem key={value} value={String(value)}>
                  {t('anniversary.windowN', { n: value })}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        }
      />

      {isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}

      {isError && (
        <p className="text-destructive rounded-md border border-dashed px-3 py-6 text-center text-sm">
          {problemMessage(error, t('common.unexpectedError'))}
        </p>
      )}

      {empty && (
        <p className="text-muted-foreground rounded-lg border border-dashed px-3 py-10 text-center text-sm">
          {t('anniversary.empty')}
        </p>
      )}

      <div className="space-y-3">
        {data.map((item) => (
          <Card key={item.eventId}>
            <CardContent className="flex flex-wrap items-center gap-4">
              <LunarLeaf day={item.lunarDay} month={item.lunarMonth} leapMonth={item.leapMonth} />
              <div className="min-w-0 flex-1 space-y-1">
                <Link
                  className="font-heading font-medium underline-offset-4 hover:underline"
                  to={`/persons/${item.personId}`}
                >
                  {item.personName}
                </Link>
                <p className="text-muted-foreground text-sm">
                  {t(item.leapMonth ? 'anniversary.lunarDateLeap' : 'anniversary.lunarDate', {
                    day: item.lunarDay,
                    month: item.lunarMonth,
                  })}
                  {item.yearsSince != null && ` · ${t('anniversary.yearsSince', { n: item.yearsSince })}`}
                </p>
                {item.approximate && (
                  <p className="text-muted-foreground text-xs">{t('anniversary.approximate')}</p>
                )}
              </div>

              <div className="flex items-center gap-2">
                {/* The solar date is what goes in a phone calendar; the lunar one is what the family knows. */}
                <span className="text-sm">{formatSolarDate(item.nextOccurrence)}</span>
                <AnniversaryCountdown daysUntil={item.daysUntil} />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </PageContainer>
  )
}
