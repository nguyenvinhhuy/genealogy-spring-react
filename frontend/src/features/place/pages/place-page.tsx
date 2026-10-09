import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { deletePlace, fetchAllPlaces, invalidatePlaceQueries, PLACE_QUERY_KEY } from '@/features/place/api'
import { PlaceDialog } from '@/features/place/components/place-dialog'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { SearchInput } from '@/shared/components/search-input'
import { TableCard, TableMessage } from '@/shared/components/table-card'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { flattenHierarchy } from '@/shared/lib/hierarchy'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

// How far each level of the tree is indented, and the gutter the first level starts from.
const INDENT_PX = 20
const GUTTER_PX = 16

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
 * Manages the clan's places as a tree: thôn inside xã inside huyện inside tỉnh (§3.7).
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
  // The whole tree is laid out once per fetch, not once per keystroke: hundreds of places, each sorted by locale.
  const flattened = useMemo(
    () => flattenHierarchy(places).map((row) => ({ ...row, folded: fold(row.path) })),
    [places],
  )
  const wanted = fold(useDebounced(filter.trim(), TYPING_DEBOUNCE_MS))
  // Filtered on the rendered path, so a xã is found by its tỉnh as well as by its own name.
  const rows = useMemo(
    () => flattened.filter((row) => wanted === '' || row.folded.includes(wanted)),
    [flattened, wanted],
  )

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
                return (
                  <TableRow key={row.id}>
                    <TableCell
                      className="font-medium"
                      style={{ paddingLeft: `${row.depth * INDENT_PX + GUTTER_PX}px` }}
                    >
                      {place.name}
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
