import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'

/** Props of {@link TableCard}. */
interface TableCardProps {
  // A line above the table: a count, filters, or both.
  toolbar?: ReactNode
  // A line below the table, usually a {@link Pager}.
  footer?: ReactNode
  children: ReactNode
}

/**
 * Frames a table the way every list page draws one: a card with an optional toolbar and footer, edge to edge.
 *
 * @param props the toolbar, the table and the footer
 * @returns the card
 */
export function TableCard({ toolbar, footer, children }: TableCardProps) {
  return (
    <Card
      className={cn(
        'gap-0 py-0 [&_thead]:bg-muted/50',
        // The table runs edge to edge, so its first and last columns take the card's own gutter.
        '[&_tbody_td:first-child]:pl-4 [&_tbody_td:last-child]:pr-4 [&_th:first-child]:pl-4 [&_th:last-child]:pr-4',
      )}
    >
      {toolbar && (
        <div className="text-muted-foreground flex flex-wrap items-center gap-3 border-b px-4 py-3 text-sm">
          {toolbar}
        </div>
      )}
      {children}
      {footer && <div className="border-t px-4 py-3">{footer}</div>}
    </Card>
  )
}

/** Props of {@link TableMessage}. */
interface TableMessageProps {
  tone?: 'muted' | 'error'
  children: ReactNode
}

/**
 * Shows a state where a table's rows would be: loading, empty or failed.
 *
 * @param props the message and whether it is an error
 * @returns the message
 */
export function TableMessage({ tone = 'muted', children }: TableMessageProps) {
  return (
    <p
      className={cn('px-4 py-10 text-center text-sm', tone === 'error' ? 'text-destructive' : 'text-muted-foreground')}
    >
      {children}
    </p>
  )
}

/** Props of {@link Pager}. */
interface PagerProps {
  page: number
  totalPages: number
  onChange: (page: number) => void
  label: string
}

/**
 * Steps through the pages of a list, drawn only when there is more than one.
 *
 * @param props the current page, how many there are, and what to call on a step
 * @returns the pager, or nothing
 */
export function Pager({ page, totalPages, onChange, label }: PagerProps) {
  const { t } = useTranslation()
  if (totalPages <= 1) {
    return null
  }
  return (
    <nav className="flex items-center justify-between gap-4" aria-label={label}>
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        {t('common.previousPage')}
      </Button>
      <span className="text-muted-foreground text-sm">
        {t('common.pageOf', { current: page + 1, total: totalPages })}
      </span>
      <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        {t('common.nextPage')}
      </Button>
    </nav>
  )
}
