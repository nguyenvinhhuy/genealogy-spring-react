import { useQuery } from '@tanstack/react-query'
import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'

import { fetchFamiliesOf } from '@/features/family/api'
import { AddRelationDialog } from '@/features/person/components/add-relation-dialog'
import { LinkPersonDialog } from '@/features/person/components/link-person-dialog'
import { fetchSubgraph } from '@/features/tree/api'
import { FanChart } from '@/features/tree/components/fan-chart'
import { TreeCanvas } from '@/features/tree/components/tree-canvas'
import { buildAncestorTree, buildDescendantTree } from '@/features/tree/lib/build-tree'
import type { TreeDirection } from '@/features/tree/types'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { HTTP_NOT_FOUND, problemMessage, problemStatus } from '@/shared/lib/problem-detail'
import { Button } from '@/shared/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'

/** How the slice is drawn, FAN being a second rendering of the ancestor slice rather than a new walk. */
type ChartType = 'DESCENDANTS' | 'ANCESTORS' | 'HOURGLASS' | 'FAN'

const CHART_TYPES: ChartType[] = ['DESCENDANTS', 'ANCESTORS', 'HOURGLASS', 'FAN']
// Up to the server's own clamp (§3.5), so a "go deeper" banner always has a deeper option to offer.
const DEPTHS = [1, 2, 3, 4, 5, 6, 7, 8]
const DEFAULT_DEPTH = 3
// Every write that changes a chart invalidates it, so returning to the tab need not re-walk the tree.
const STALE_MS = 60_000

// Which way the server has to walk for each chart.
const WALK_DIRECTION: Record<ChartType, TreeDirection> = {
  DESCENDANTS: 'DESCENDANTS',
  ANCESTORS: 'ANCESTORS',
  HOURGLASS: 'HOURGLASS',
  FAN: 'ANCESTORS',
}

/**
 * Draws the family chart around one person.
 *
 * @returns the page element
 */
export function TreePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { id } = useParams()
  const focusId = Number(id)
  const { mayEdit } = usePermissions()

  const [chart, setChart] = useState<ChartType>('DESCENDANTS')
  const [depth, setDepth] = useState(DEFAULT_DEPTH)

  const direction = WALK_DIRECTION[chart]

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['tree', focusId, direction, depth],
    queryFn: () => fetchSubgraph(focusId, direction, depth),
    enabled: Number.isFinite(focusId),
    staleTime: STALE_MS,
  })

  const { data: families = [] } = useQuery({
    queryKey: ['families', focusId],
    queryFn: () => fetchFamiliesOf(focusId),
    enabled: mayEdit && Number.isFinite(focusId),
  })

  const descendantRoot = useMemo(
    () => (data && direction !== 'ANCESTORS' ? buildDescendantTree(data) : null),
    [data, direction],
  )
  const ancestorRoot = useMemo(
    () => (data && direction !== 'DESCENDANTS' ? buildAncestorTree(data) : null),
    [data, direction],
  )
  const recordedDead = useMemo(() => new Set(data?.recordedDeadIds ?? []), [data])

  const focusName = data?.persons.find((person) => person.id === focusId)?.displayName ?? ''
  const hasChart = (descendantRoot ?? ancestorRoot) != null

  const onSelect = useCallback((personId: number) => void navigate(`/tree/${personId}`), [navigate])

  if (isError) {
    return (
      <PageContainer>
        <p className="text-sm">
          {problemStatus(error) === HTTP_NOT_FOUND
            ? t('person.notFound')
            : problemMessage(error, t('common.unexpectedError'))}
        </p>
      </PageContainer>
    )
  }

  return (
    <PageContainer>
      <PageHeader
        title={t('tree.title')}
        description={focusName ? t('tree.centredOn', { name: focusName }) : t('common.loading')}
        actions={
          <>
            <Select value={chart} onValueChange={(next) => setChart(next as ChartType)}>
              <SelectTrigger className="w-44" aria-label={t('tree.chartType')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {CHART_TYPES.map((value) => (
                  <SelectItem key={value} value={value}>
                    {t(`tree.charts.${value}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={String(depth)} onValueChange={(next) => setDepth(Number(next))}>
              <SelectTrigger className="w-32" aria-label={t('tree.depth')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {DEPTHS.map((value) => (
                  <SelectItem key={value} value={String(value)}>
                    {t('tree.depthN', { n: value })}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {/* Adding a child or spouse right here, on the chart, is what keeps data entry cheap (§6.1). */}
            {mayEdit && data && <AddRelationDialog key={focusId} personId={focusId} families={families} />}
            {mayEdit && data && <LinkPersonDialog key={`link-${focusId}`} personId={focusId} families={families} />}
            <Button asChild variant="outline">
              <Link to={`/persons/${focusId}`}>{t('tree.openPerson')}</Link>
            </Button>
          </>
        }
      />

      {/* Saying so matters: a chart that just stops looks like the family ends there. */}
      {data?.truncatedAbove && (
        <p className="text-muted-foreground rounded-md border border-dashed px-3 py-2 text-sm">
          {t('tree.truncatedAbove')}
        </p>
      )}
      {data?.truncatedBelow && (
        <p className="text-muted-foreground rounded-md border border-dashed px-3 py-2 text-sm">
          {t('tree.truncatedBelow')}
        </p>
      )}

      {isLoading && <p className="text-muted-foreground text-sm">{t('common.loading')}</p>}

      {!isLoading && !hasChart && <p className="text-muted-foreground text-sm">{t('tree.empty')}</p>}

      {chart === 'FAN' && ancestorRoot && (
        <FanChart root={ancestorRoot} rings={depth} recordedDead={recordedDead} onSelect={onSelect} />
      )}

      {chart !== 'FAN' && hasChart && (
        <TreeCanvas
          descendants={descendantRoot}
          ancestors={ancestorRoot}
          focusId={focusId}
          recordedDead={recordedDead}
          onSelect={onSelect}
        />
      )}

      <p className="text-muted-foreground text-xs">{t('tree.hint')}</p>
      <p className="text-muted-foreground text-xs">{t('tree.legend')}</p>
    </PageContainer>
  )
}
