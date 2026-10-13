import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { deletePlace, fetchAllPlaces, invalidatePlaceQueries, PLACE_QUERY_KEY } from '@/features/place/api'
import { PlaceDialog } from '@/features/place/components/place-dialog'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { IconTooltip } from '@/shared/components/icon-tooltip'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { SearchInput } from '@/shared/components/search-input'
import { TableCard, TableMessage } from '@/shared/components/table-card'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { flattenHierarchy } from '@/shared/lib/hierarchy'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Button } from '@/shared/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

// How far each level of the tree is indented, and the gutter the first level starts from.
const INDENT_PX = 20
const GUTTER_PX = 16
// The width a toggle takes, so a row without one still lines its name up with its siblings.
const TOGGLE_PX = 28
// Matches shown at most while filtering: "xa" alone matches thousands of the seeded xã (V18).
const MAX_MATCHES = 200

/**
 * Removes Vietnamese diacritics and case, so "ha noi" finds "Hà Nội" the way the server's search does (§4.3).
 *
 * @param text the text to fold
 * @returns the folded text
 */
function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/gi, 'd').toLowerCase()
}

/**
 * Manages the clan's places as a folding tree: thôn inside xã inside huyện inside tỉnh (§3.7).
 *
 * @returns the page element
 */
export function PlacePage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { mayEdit, mayDelete } = usePermissions()
  const [filter, setFilter] = useState('')

  const { data: places = [], isLoading, isError, error } = useQuery({
    queryKey: [...PLACE_QUERY_KEY, 'all'],
    queryFn: fetchAllPlaces,
  })
  const byId = useMemo(() => new Map(places.map((place) => [place.id, place])), [places])
  // The whole tree is laid out once per fetch, not once per keystroke: thousands of places, each sorted by locale.
  const tree = useMemo(() => {
    const childCount = new Map<number, number>()
    for (const place of places) {
      if (place.parentId != null) {
        childCount.set(place.parentId, (childCount.get(place.parentId) ?? 0) + 1)
      }
    }
    const rows = flattenHierarchy(places).map((row) => ({ ...row, folded: fold(row.path), ancestors: [] as number[] }))
    // Rows come depth first, so the open chain of ancestors at each depth is the one just walked.
    const chain: number[] = []
    for (const row of rows) {
      chain.length = row.depth
      row.ancestors = [...chain]
      chain.push(row.id)
    }
    const roots = new Set(rows.filter((row) => row.depth === 0).map((row) => row.id))
    return { rows, childCount, roots }
  }, [places])
  // Only the top level starts open, so the page shows Việt Nam and its tỉnh, not every xã at once.
  const [expanded, setExpanded] = useState<ReadonlySet<number> | null>(null)
  const open = expanded ?? tree.roots
  const wanted = fold(useDebounced(filter.trim(), TYPING_DEBOUNCE_MS))
  const view = useMemo(() => {
    if (wanted === '') {
      const rows = tree.rows.filter((row) => row.ancestors.every((ancestor) => open.has(ancestor)))
      return { rows, matched: rows.length, filtering: false }
    }
    // Filtered on the rendered path, so a xã is found by its tỉnh as well as by its own name.
    const matches = tree.rows.filter((row) => row.folded.includes(wanted))
    const shown = new Set<number>()
    for (const row of matches.slice(0, MAX_MATCHES)) {
      row.ancestors.forEach((ancestor) => shown.add(ancestor))
      shown.add(row.id)
    }
    return { rows: tree.rows.filter((row) => shown.has(row.id)), matched: matches.length, filtering: true }
  }, [tree, open, wanted])
  const rows = view.rows

  const toggle = (id: number) => {
    const next = new Set(open)
    if (!next.delete(id)) {
      next.add(id)
    }
    setExpanded(next)
  }

  const removal = useMutation({
    mutationFn: ({ id, changeNote }: { id: number; changeNote: string }) => deletePlace(id, changeNote),
    onSuccess: async () => {
      await invalidatePlaceQueries(queryClient)
      toast.success(t('place.deleted'))
    },
  })

  return (
    <PageContainer>
      <PageHeader title={t('place.title')} description={t('place.subtitle')} actions={mayEdit && <PlaceDialog />} />

      <TableCard
        toolbar={
          <SearchInput
            value={filter}
            placeholder={t('place.searchPlaceholder')}
            aria-label={t('place.search')}
            onChange={(event) => setFilter(event.target.value)}
          />
        }
      >
        {isLoading && <TableMessage>{t('common.loading')}</TableMessage>}
        {isError && <TableMessage tone="error">{problemMessage(error, t('common.unexpectedError'))}</TableMessage>}
        {!isLoading && !isError && rows.length === 0 && (
          <TableMessage>{t(places.length === 0 ? 'place.empty' : 'place.noMatch')}</TableMessage>
        )}

        {view.filtering && view.matched > MAX_MATCHES && (
          <TableMessage>{t('place.tooMany', { shown: MAX_MATCHES, total: view.matched })}</TableMessage>
        )}
        {rows.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('place.name')}</TableHead>
                <TableHead>{t('place.type')}</TableHead>
                <TableHead>{t('coordinates.title')}</TableHead>
                {(mayEdit || mayDelete) && <TableHead className="w-0">{t('common.actions')}</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const place = byId.get(row.id)
                if (!place) {
                  return null
                }
                const children = tree.childCount.get(place.id) ?? 0
                const isOpen = open.has(place.id)
                return (
                  <TableRow key={row.id}>
                    <TableCell
                      className="font-medium"
                      style={{ paddingLeft: `${row.depth * INDENT_PX + GUTTER_PX}px` }}
                    >
                      <div className="flex items-center gap-1">
                        {children > 0 && !view.filtering ? (
                          <IconTooltip label={t(isOpen ? 'place.collapse' : 'place.expand', { name: place.name })}>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-expanded={isOpen}
                              aria-label={t(isOpen ? 'place.collapse' : 'place.expand', { name: place.name })}
                              onClick={() => toggle(place.id)}
                            >
                              {isOpen ? <ChevronDown aria-hidden /> : <ChevronRight aria-hidden />}
                            </Button>
                          </IconTooltip>
                        ) : (
                          !view.filtering && <span aria-hidden style={{ width: TOGGLE_PX }} className="shrink-0" />
                        )}
                        <span>{place.name}</span>
                        {children > 0 && (
                          <span className="text-muted-foreground text-xs font-normal">
                            {t('place.childCount', { count: children })}
                          </span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{t(`place.types.${place.type}`)}</TableCell>
                    <TableCell className="text-muted-foreground tabular-nums">
                      {place.latitude != null && place.longitude != null
                        ? `${place.latitude}, ${place.longitude}`
                        : '—'}
                    </TableCell>
                    {(mayEdit || mayDelete) && (
                      <TableCell>
                        <div className="flex items-center gap-1">
                          {mayEdit && <PlaceDialog place={place} />}
                          {mayDelete && (
                            <ConfirmDeleteDialog
                              label={t('common.delete')}
                              title={t('place.deleteTitle', { name: place.path })}
                              description={t('place.deleteDescription')}
                              pending={removal.isPending}
                              onConfirm={(changeNote) => removal.mutateAsync({ id: place.id, changeNote })}
                            />
                          )}
                        </div>
                      </TableCell>
                    )}
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </TableCard>
    </PageContainer>
  )
}
