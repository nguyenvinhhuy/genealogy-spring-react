import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { usePanZoom } from '@/features/tree/hooks'
import type { TreeNode } from '@/features/tree/lib/build-tree'
import { fanLabelTransform, fanSegmentPath, layoutFan } from '@/features/tree/lib/fan-layout'

const RING_WIDTH = 64
const CENTRE_RADIUS = 52
const MIN_SCALE = 0.3
const MAX_SCALE = 3
// The fan opens upward, so its centre sits this far down the viewport rather than in the middle.
const ORIGIN_HEIGHT_RATIO = 0.78
const DASH = '5 4'
const DEAD_MARK = ' †'
const REPEAT_MARK = ' ↺'
// Alternating ring tints keep neighbouring generations distinguishable without a legend.
const RING_FILLS = ['fill-primary/10', 'fill-muted', 'fill-primary/5', 'fill-background']

/** What the fan draws and how it reports a click. */
interface FanChartProps {
  root: TreeNode
  rings: number
  recordedDead: ReadonlySet<number>
  onSelect: (personId: number) => void
}

/**
 * Draws a pedigree as a fan: the focus person at the centre, each generation back a ring outward.
 *
 * @param props the ancestor tree, how many rings to draw, who is recorded as dead, and the click handler
 * @returns the chart element
 */
export function FanChart({ root, rings, recordedDead, onSelect }: FanChartProps) {
  // A polar pedigree fits far more generations in the same area — this is the one people print and hang up.
  const { t } = useTranslation()
  const segments = useMemo(() => layoutFan(root, rings), [root, rings])
  const { svgRef, transform, size } = usePanZoom(MIN_SCALE, MAX_SCALE, segments)

  // Built once per layout, so a pan or zoom tick only rewrites the outer transform.
  const content = useMemo(
    () =>
      segments.map((segment) => {
        const inner = segment.ring === 0 ? 0 : CENTRE_RADIUS + (segment.ring - 1) * RING_WIDTH
        const outer = segment.ring === 0 ? CENTRE_RADIUS : CENTRE_RADIUS + segment.ring * RING_WIDTH
        const label = fanLabelTransform(segment, (inner + outer) / 2)
        const fill = segment.ring === 0 ? 'fill-primary/25' : RING_FILLS[segment.ring % RING_FILLS.length]

        return (
          <g key={segment.key} className="cursor-pointer" onClick={() => onSelect(segment.person.id)}>
            <path
              d={fanSegmentPath(segment, inner, outer)}
              className={`${fill} stroke-border hover:stroke-primary`}
              strokeWidth={1}
              strokeDasharray={segment.birthLink && !segment.repeat ? undefined : DASH}
            />
            <text
              transform={`translate(${label.x},${label.y}) rotate(${label.rotation})`}
              textAnchor="middle"
              dominantBaseline="middle"
              className="fill-foreground pointer-events-none text-[11px]"
            >
              {segment.person.displayName}
              {recordedDead.has(segment.person.id) && (
                <tspan className="fill-muted-foreground">{DEAD_MARK}</tspan>
              )}
              {segment.repeat && <tspan className="fill-muted-foreground">{REPEAT_MARK}</tspan>}
            </text>
          </g>
        )
      }),
    [segments, recordedDead, onSelect],
  )

  return (
    <div className="bg-muted/30 relative h-[70vh] w-full overflow-hidden rounded-lg border">
      <svg
        ref={svgRef}
        className="h-full w-full cursor-grab active:cursor-grabbing"
        role="img"
        aria-label={t('tree.fanLabel')}
      >
        {/* The zoom transform is outermost and alone, so d3 keeps the point under the cursor still. */}
        <g transform={transform.toString()}>
          <g transform={`translate(${size.width / 2},${size.height * ORIGIN_HEIGHT_RATIO})`}>{content}</g>
        </g>
      </svg>
    </div>
  )
}
