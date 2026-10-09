import type { ReactNode } from 'react'

import { cn } from '@/shared/lib/utils'

/** Props of {@link PageHeader}. */
interface PageHeaderProps {
  title: ReactNode
  description?: ReactNode
  // Buttons on the right of the title, such as "Thêm người".
  actions?: ReactNode
  // Anything shown beside the title, such as a status badge.
  badges?: ReactNode
  className?: string
}

/**
 * Draws a page's title, its one-line description and its actions, the same way on every page.
 *
 * @param props the title, description, badges and actions
 * @returns the header
 */
export function PageHeader({ title, description, actions, badges, className }: PageHeaderProps) {
  return (
    <header className={cn('flex flex-wrap items-end justify-between gap-4 border-b pb-5', className)}>
      <div className="min-w-0 space-y-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">{title}</h1>
          {badges}
        </div>
        {description && <p className="text-muted-foreground max-w-2xl text-sm">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

/** Props of {@link PageContainer}. */
interface PageContainerProps {
  children: ReactNode
  // `narrow` for a single record or a form, `wide` for tables and charts.
  width?: 'narrow' | 'wide'
}

/**
 * Centres a page's content at the width its kind of content reads best at.
 *
 * @param props the content and how wide it may grow
 * @returns the container
 */
export function PageContainer({ children, width = 'wide' }: PageContainerProps) {
  return (
    <div className={cn('mx-auto w-full space-y-6', width === 'narrow' ? 'max-w-4xl' : 'max-w-6xl')}>{children}</div>
  )
}
