import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Logo } from '@/shared/components/logo'
import { Button } from '@/shared/ui/button'

/**
 * Tells the reader the address leads nowhere, after the template's 404 page.
 *
 * @returns the page element
 */
export function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <div className="mx-auto flex max-w-lg flex-1 flex-col items-center justify-center gap-6 py-16 text-center">
      <Logo className="size-16 opacity-90" />
      <div className="space-y-3">
        <p className="text-seal font-heading text-6xl font-semibold tracking-tight">{t('notFound.code')}</p>
        <h1 className="text-2xl font-semibold">{t('notFound.title')}</h1>
        <p className="text-muted-foreground">{t('notFound.body')}</p>
      </div>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Button asChild>
          <Link to="/">{t('notFound.home')}</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/persons">{t('notFound.persons')}</Link>
        </Button>
      </div>
    </div>
  )
}
