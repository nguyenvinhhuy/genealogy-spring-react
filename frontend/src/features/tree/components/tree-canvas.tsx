import { hierarchy, tree as d3tree, type HierarchyPointNode } from 'd3-hierarchy'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { usePanZoom } from '@/features/tree/hooks'
import type { TreeNode, UnionSlot } from '@/features/tree/lib/build-tree'
import type { PersonNode } from '@/features/tree/types'

const NODE_WIDTH = 168
const ROW_HEIGHT = 20
const BOX_PADDING_Y = 16
const LABEL_INSET_X = 10
const LABEL_BASELINE = 14
const CORNER_RADIUS = 8
const UNION_ANCHOR_GAP = 28
const UNION_MARKER_RADIUS = 7
const H_GAP = 28
const V_GAP = 96
// How far the chart's first row sits from the edge it grows away from.
const VIEW_MARGIN = 76
const MIN_SCALE = 0.2
const MAX_SCALE = 2.5
const LINK_STROKE = 1.5
const BOX_STROKE = 1
const FOCUS_STROKE = 2
const DASH = '5 4'
const DEAD_MARK = ' †'
const REPEAT_MARK = ' ↺'

/** One laid-out half of the chart; a sign of -1 mirrors it upward, for the ancestor side. */
interface Half {
  nodes: HierarchyPointNode<TreeNode>[]
  links: { source: HierarchyPointNode<TreeNode>; target: HierarchyPointNode<TreeNode> }[]
  sign: 1 | -1
}

/** What the chart draws and how it reports a click. */
interface TreeCanvasProps {
  descendants: TreeNode | null
  ancestors: TreeNode | null
  focusId: number
  recordedDead: ReadonlySet<number>
  onSelect: (personId: number) => void
}

/**
 * Lists the unions that get a row of their own in a box.
 *
 * @param node the box's node
 * @returns the unions to label
 */
function labelledUnions(node: TreeNode): UnionSlot[] {
  // With several unions each needs its numbered row, even an unknown spouse, or the children cannot be told apart.
  return node.unions.length > 1 ? node.unions : node.unions.filter((slot) => slot.spouse != null)
}

/**
 * Measures a box, which grows by one row per labelled union.
 *
 * @param node the box's node
 * @returns its height
 */
function boxHeight(node: TreeNode): number {
  return 2 * BOX_PADDING_Y + ROW_HEIGHT * (1 + labelledUnions(node).length)
}

/**
 * Places the point on a box's bottom edge that one union's children hang from.
 *
 * @param node the parent's node
 * @param unionIndex which of its unions, or null
 * @returns the horizontal offset from the box's centre
 */
function unionAnchorOffset(node: TreeNode, unionIndex: number | null): number {
  if (unionIndex == null || node.unions.length < 2) {
    return 0
  }
  return (unionIndex - (node.unions.length - 1) / 2) * UNION_ANCHOR_GAP
}

/**
 * Lays out one half of the chart.
 *
 * @param root the root of that half
 * @param sign 1 to grow downward, -1 to grow upward
 * @returns the laid-out nodes and links
 */
function layoutHalf(root: TreeNode, sign: 1 | -1): Half {
  const tree = hierarchy<TreeNode>(root, (node) => node.children)
  const tallest = Math.max(...tree.descendants().map((node) => boxHeight(node.data)))
  const laid = d3tree<TreeNode>().nodeSize([NODE_WIDTH + H_GAP, tallest + V_GAP])(tree)
  return { nodes: laid.descendants(), links: laid.links(), sign }
}

/**
 * Draws a family chart as plain SVG, laid out by d3-hierarchy and panned with d3-zoom.
 *
 * @param props the halves to draw, the focus person, who is recorded as dead, and the selection handler
 * @returns the chart element
 */
export function TreeCanvas({ descendants, ancestors, focusId, recordedDead, onSelect }: TreeCanvasProps) {
  // Drawn by hand (§3.5): no packaged tree component models several unions per person, the normal case here.
  const { t } = useTranslation()

  const halves = useMemo(() => {
    const result: Half[] = []
    if (descendants) {
      result.push(layoutHalf(descendants, 1))
    }
    if (ancestors) {
      result.push(layoutHalf(ancestors, -1))
    }
    return result
  }, [descendants, ancestors])

  const { svgRef, transform, size } = usePanZoom(MIN_SCALE, MAX_SCALE, halves)

  const geometry = useMemo(() => {
    const xs = halves.flatMap((half) => half.nodes.map((node) => node.x))
    const ys = halves.flatMap((half) => half.nodes.map((node) => node.y * half.sign))
    return {
      centreX: (Math.min(...xs) + Math.max(...xs)) / 2,
      minY: Math.min(...ys),
      maxY: Math.max(...ys),
    }
  }, [halves])

  // Built once per layout, so a pan or zoom tick only rewrites the outer transform.
  const content = useMemo(
    () => (
      <>
        {halves.map((half) =>
          half.links.map((link) => (
            <Connector
              key={`${half.sign}-${link.source.data.key}-${link.target.data.key}`}
              source={link.source}
              target={link.target}
              sign={half.sign}
            />
          )),
        )}
        {halves.map((half) =>
          half.nodes
            // Both halves are rooted at the focus person; the descending one keeps it, as it has spouses.
            .filter((node) => !(halves.length > 1 && half.sign === -1 && node.depth === 0))
            .map((node) => (
              <PersonBox
                key={`${half.sign}-${node.data.key}`}
                node={node}
                sign={half.sign}
                isFocus={node.data.person.id === focusId}
                recordedDead={recordedDead}
                unknownSpouse={t('tree.unknownSpouse')}
                onSelect={onSelect}
              />
            )),
        )}
      </>
    ),
    [halves, focusId, recordedDead, onSelect, t],
  )

  // y = 0 lands at the top when only descending, the bottom when only ascending, the middle when both.
  const anchorY =
    geometry.minY < 0 && geometry.maxY > 0
      ? size.height / 2
      : geometry.minY < 0
        ? size.height - VIEW_MARGIN
        : VIEW_MARGIN

  return (
    <div className="bg-muted/30 relative h-[70vh] w-full overflow-hidden rounded-lg border">
      <svg
        ref={svgRef}
        className="h-full w-full cursor-grab active:cursor-grabbing"
        role="img"
        aria-label={t('tree.canvasLabel')}
      >
        {/* The zoom transform is outermost and alone, so d3 keeps the point under the cursor still. */}
        <g transform={transform.toString()}>
          <g transform={`translate(${size.width / 2 - geometry.centreX},${anchorY})`}>{content}</g>
        </g>
      </svg>
    </div>
  )
}

/**
 * Draws the elbow connector between a parent box and a child box.
 *
 * @param props the two endpoints and which way the half grows
 * @returns the path element
 */
function Connector({
  source,
  target,
  sign,
}: {
  source: HierarchyPointNode<TreeNode>
  target: HierarchyPointNode<TreeNode>
  sign: 1 | -1
}) {
  // Each end meets its own box's edge, and a single person's box is shorter than a couple's.
  const sourceY = source.y * sign + (sign * boxHeight(source.data)) / 2
  const targetY = target.y * sign - (sign * boxHeight(target.data)) / 2
  const sourceX = source.x + unionAnchorOffset(source.data, target.data.viaUnion)
  const midY = (sourceY + targetY) / 2

  return (
    <path
      className="stroke-border fill-none"
      strokeWidth={LINK_STROKE}
      strokeDasharray={target.data.birthLink ? undefined : DASH}
      d={`M${sourceX},${sourceY} V${midY} H${target.x} V${targetY}`}
    />
  )
}

/**
 * Draws one box: a person, plus a row for each of their unions.
 *
 * @param props the laid-out node, which way the half grows, whether it is the focus, who is dead, and the handlers
 * @returns the group element
 */
function PersonBox({
  node,
  sign,
  isFocus,
  recordedDead,
  unknownSpouse,
  onSelect,
}: {
  node: HierarchyPointNode<TreeNode>
  sign: 1 | -1
  isFocus: boolean
  recordedDead: ReadonlySet<number>
  unknownSpouse: string
  onSelect: (personId: number) => void
}) {
  const { person, unions, repeat, children } = node.data
  const slots = labelledUnions(node.data)
  const numbered = unions.length > 1
  const height = boxHeight(node.data)
  const top = node.y * sign - height / 2
  const baseline = (row: number) => BOX_PADDING_Y + LABEL_BASELINE + row * ROW_HEIGHT

  return (
    <g transform={`translate(${node.x - NODE_WIDTH / 2},${top})`}>
      <rect
        width={NODE_WIDTH}
        height={height}
        rx={CORNER_RADIUS}
        className={
          isFocus
            ? 'fill-primary/10 stroke-primary'
            : 'fill-background stroke-border hover:stroke-primary'
        }
        strokeWidth={isFocus ? FOCUS_STROKE : BOX_STROKE}
        strokeDasharray={repeat ? DASH : undefined}
      />
      <PersonLabel
        person={person}
        y={baseline(0)}
        dead={recordedDead.has(person.id)}
        suffix={repeat ? REPEAT_MARK : ''}
        onSelect={onSelect}
        bold
      />
      {slots.map((slot, index) => {
        const prefix = numbered ? `${index + 1}. ` : ''
        return slot.spouse ? (
          <PersonLabel
            key={slot.familyId}
            person={slot.spouse}
            y={baseline(index + 1)}
            dead={recordedDead.has(slot.spouse.id)}
            prefix={prefix}
            onSelect={onSelect}
          />
        ) : (
          <text
            key={slot.familyId}
            x={LABEL_INSET_X}
            y={baseline(index + 1)}
            className="fill-muted-foreground text-[13px] italic"
          >
            {prefix}
            {unknownSpouse}
          </text>
        )
      })}
      {numbered &&
        unions.map((slot, index) =>
          children.some((child) => child.viaUnion === index) ? (
            <g
              key={slot.familyId}
              transform={`translate(${NODE_WIDTH / 2 + unionAnchorOffset(node.data, index)},${height})`}
            >
              <circle r={UNION_MARKER_RADIUS} className="fill-background stroke-border" />
              <text
                textAnchor="middle"
                dominantBaseline="central"
                className="fill-muted-foreground text-[10px]"
              >
                {index + 1}
              </text>
            </g>
          ) : null,
        )}
    </g>
  )
}

/**
 * Draws one clickable name inside a box.
 *
 * @param props the person, their baseline, whether they are recorded dead, any prefix or suffix, and the handler
 * @returns the text element
 */
function PersonLabel({
  person,
  y,
  dead,
  prefix = '',
  suffix = '',
  onSelect,
  bold = false,
}: {
  person: PersonNode
  y: number
  dead: boolean
  prefix?: string
  suffix?: string
  onSelect: (personId: number) => void
  bold?: boolean
}) {
  return (
    <text
      x={LABEL_INSET_X}
      y={y}
      className={`fill-foreground cursor-pointer text-[13px] ${bold ? 'font-medium' : 'opacity-70'}`}
      onClick={() => onSelect(person.id)}
    >
      {prefix}
      {person.displayName}
      {dead && <tspan className="fill-muted-foreground">{DEAD_MARK}</tspan>}
      {suffix && <tspan className="fill-muted-foreground">{suffix}</tspan>}
    </text>
  )
}
