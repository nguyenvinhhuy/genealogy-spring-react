import { useMutation } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'

import { downloadBook } from '@/features/book/api'
import { downloadGedcom, importGedcom } from '@/features/gedcom/api'
import type { GedcomImportResult } from '@/features/gedcom/types'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { invalidateClanData } from '@/shared/lib/query-client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

/**
 * Lets the family take the gia phả out and bring one in.
 *
 * @returns the page element
 */
export function DataPage() {
  // Export and import are both EDITOR and above server-side: a whole-clan file cannot be redacted (§3.6).
  const { t } = useTranslation()
  // Through the hook, not a hand-rolled role test: a MEMBER who types the URL saw both buttons enabled.
  const { mayAudit, mayImport } = usePermissions()
  const fileInput = useRef<HTMLInputElement>(null)
  const [result, setResult] = useState<GedcomImportResult | null>(null)

  const exportGedcom = useMutation({
    mutationFn: downloadGedcom,
  })

  const exportBook = useMutation({
    mutationFn: () => downloadBook(),
  })

  const runImport = useMutation({
    mutationFn: importGedcom,
    onSuccess: (imported) => {
      setResult(imported)
      // An import adds people, sources, places and chi and rewrites everyone's đời, so every view is stale.
      void invalidateClanData()
      toast.success(t('data.imported', { n: imported.personsCreated }))
    },
  })

  const onPick = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      runImport.mutate(file)
    }
    // Clearing lets the same file be picked again after a failed attempt.
    event.target.value = ''
  }

  return (
    <PageContainer width="narrow">
      <PageHeader title={t('data.title')} description={t('data.subtitle')} />

      {/* A MEMBER who followed a link here saw a header over nothing at all (#22). */}
      {!mayAudit && (
        <Card>
          <CardContent>
            <p className="text-muted-foreground text-sm">{t('data.noAccess')}</p>
          </CardContent>
        </Card>
      )}

      {mayAudit && (
        <Card>
          <CardHeader>
            <CardTitle>{t('data.exportTitle')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-muted-foreground text-sm">{t('data.exportHint')}</p>
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => exportGedcom.mutate()} disabled={exportGedcom.isPending}>
                {exportGedcom.isPending ? t('common.loading') : t('data.exportGedcom')}
              </Button>
              <Button
                variant="outline"
                onClick={() => exportBook.mutate()}
                disabled={exportBook.isPending}
              >
                {exportBook.isPending ? t('common.loading') : t('data.exportBook')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {mayImport && (
        <Card>
          <CardHeader>
            <CardTitle>{t('data.importTitle')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-muted-foreground text-sm">{t('data.importHint')}</p>
            <input
              ref={fileInput}
              type="file"
              accept=".ged,text/plain"
              className="hidden"
              onChange={onPick}
            />
            <Button
              variant="outline"
              onClick={() => fileInput.current?.click()}
              disabled={runImport.isPending}
            >
              {runImport.isPending ? t('data.importing') : t('data.pickFile')}
            </Button>

            {result && (
              <div className="space-y-2 border-t pt-3 text-sm">
                <p>
                  {t('data.result', {
                    persons: result.personsCreated,
                    families: result.familiesCreated,
                    events: result.eventsCreated,
                    sources: result.sourcesCreated,
                    citations: result.citationsCreated,
                  })}
                </p>
                {result.skipped > 0 && (
                  <p className="text-muted-foreground">
                    {t('data.skipped', { n: result.skipped })}
                  </p>
                )}
                {result.dropped > 0 && (
                  <p className="text-destructive">{t('data.dropped', { n: result.dropped })}</p>
                )}
                {result.warnings.length > 0 && (
                  <ul className="text-muted-foreground list-disc space-y-1 pl-5">
                    {result.warnings.map((warning, index) => (
                      // Keyed by position: two records can fail the same way and produce the same text.
                      <li key={`${index}-${warning}`}>{warning}</li>
                    ))}
                  </ul>
                )}
                {/* An import adds and never merges, and this is the only honest place to say so. */}
                <p className="text-muted-foreground">
                  {t('data.duplicateHint')}{' '}
                  <Link to="/quality" className="underline">
                    {t('quality.title')}
                  </Link>
                </p>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </PageContainer>
  )
}
