import type { TreeNode } from '@/features/tree/lib/build-tree'
import type { PersonNode } from '@/features/tree/types'

/** One ancestor's slice of the fan; ring 0 is the focus person and each ring outward one generation back. */
export interface FanSegment {
  key: string
  person: PersonNode
  ring: number
  startAngle: number
  endAngle: number
  birthLink: boolean
  repeat: boolean
}

// Radians clockwise from straight up: at -180° the fan would sweep the left half and point sideways.
const SWEEP = Math.PI
const START = -Math.PI / 2

/**
 * Flattens an ancestor tree into angular slices, one ring per generation.
 *
 * @param root the ancestor tree, focus person first
 * @param maxRing how many generations to lay out
 * @returns the slices, innermost first
 */
export function layoutFan(root: TreeNode, maxRing: number): FanSegment[] {
  // Parents split their child's arc evenly, so one recorded parent fills the ring instead of leaving a gap.
  const segments: FanSegment[] = []

  const walk = (node: TreeNode, ring: number, startAngle: number, endAngle: number) => {
    if (ring > maxRing) {
      return
    }
    segments.push({
      key: node.key,
      person: node.person,
      ring,
      startAngle,
      endAngle,
      birthLink: node.birthLink,
      repeat: node.repeat,
    })

    const parents = node.children
    const step = (endAngle - startAngle) / Math.max(parents.length, 1)
    parents.forEach((parent, index) => {
      walk(parent, ring + 1, startAngle + index * step, startAngle + (index + 1) * step)
    })
  }

  walk(root, 0, START, START + SWEEP)
  return segments
}

/**
 * Builds the SVG path for one annular sector.
 *
 * @param segment the slice to draw
 * @param innerRadius the radius of its inner edge
 * @param outerRadius the radius of its outer edge
 * @returns the path's `d` attribute
 */
export function fanSegmentPath(
  segment: FanSegment,
  innerRadius: number,
  outerRadius: number,
): string {
  // The centre disc is a full circle, not a sector: an arc from a point back to itself draws nothing.
  if (segment.ring === 0) {
    const halfCircle = `A ${outerRadius},${outerRadius} 0 1,1`
    return `M ${-outerRadius},0 ${halfCircle} ${outerRadius},0 ${halfCircle} ${-outerRadius},0 Z`
  }

  const { startAngle, endAngle } = segment
  const point = (radius: number, angle: number) =>
    `${(radius * Math.sin(angle)).toFixed(2)},${(-radius * Math.cos(angle)).toFixed(2)}`

  // No slice outside the centre is wider than the half-circle fan, so neither arc is ever the large one.
  return [
    `M ${point(innerRadius, startAngle)}`,
    `L ${point(outerRadius, startAngle)}`,
    `A ${outerRadius},${outerRadius} 0 0,1 ${point(outerRadius, endAngle)}`,
    `L ${point(innerRadius, endAngle)}`,
    `A ${innerRadius},${innerRadius} 0 0,0 ${point(innerRadius, startAngle)} Z`,
  ].join(' ')
}

/**
 * Places a slice's label at the middle of its arc, rotated to follow it.
 *
 * @param segment the slice
 * @param radius the radius to sit the label on
 * @returns the label's position and rotation in degrees
 */
export function fanLabelTransform(segment: FanSegment, radius: number) {
  // The focus person owns the whole centre disc, so their name sits flat in the middle of it.
  if (segment.ring === 0) {
    return { x: 0, y: 0, rotation: 0 }
  }

  // The fan spans only the upper half, so a tangential label never reaches upside down and needs no flip.
  const mid = (segment.startAngle + segment.endAngle) / 2
  return {
    x: radius * Math.sin(mid),
    y: -radius * Math.cos(mid),
    rotation: (mid * 180) / Math.PI,
  }
}
