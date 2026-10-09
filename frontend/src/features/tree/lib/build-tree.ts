import type { ChildEdge, PersonNode, Subgraph, UnionEdge } from '@/features/tree/types'

/** One union drawn inside a person's box: the spouse, when one is recorded. */
export interface UnionSlot {
  familyId: number
  spouse: PersonNode | null
}

/**
 * One box in the chart.
 */
// A couple is one box (§3.5), so the unions hang off the person instead of becoming nodes of their own.
export interface TreeNode {
  key: string
  person: PersonNode
  unions: UnionSlot[]
  viaUnion: number | null
  birthLink: boolean
  repeat: boolean
  children: TreeNode[]
}

/**
 * Indexes a subgraph for repeated lookups.
 *
 * @param subgraph the slice to index
 * @returns the person map and the unions grouped by each partner and by each child
 */
function index(subgraph: Subgraph) {
  const personById = new Map<number, PersonNode>()
  for (const person of subgraph.persons) {
    personById.set(person.id, person)
  }

  const unionsByPartner = new Map<number, UnionEdge[]>()
  const unionsByChild = new Map<number, UnionEdge[]>()

  for (const union of subgraph.unions) {
    for (const partnerId of [union.partner1Id, union.partner2Id]) {
      if (partnerId != null) {
        unionsByPartner.set(partnerId, [...(unionsByPartner.get(partnerId) ?? []), union])
      }
    }
    for (const child of union.children) {
      unionsByChild.set(child.childId, [...(unionsByChild.get(child.childId) ?? []), union])
    }
  }

  return { personById, unionsByPartner, unionsByChild }
}

/**
 * Returns a child's relation to one partner of the union it is linked into.
 *
 * @param union the union
 * @param link the child's link into it
 * @param partnerId one of the union's partners
 * @returns the child's relation to that partner
 */
function relationTo(union: UnionEdge, link: ChildEdge, partnerId: number) {
  return union.partner1Id === partnerId ? link.relationToP1 : link.relationToP2
}

/**
 * Builds the descendant chart rooted at the focus person.
 *
 * @param subgraph the slice returned by the tree endpoint
 * @returns the root node, or null when the focus person is missing from the slice
 */
export function buildDescendantTree(subgraph: Subgraph): TreeNode | null {
  const { personById, unionsByPartner } = index(subgraph)
  const expanded = new Set<number>()
  let serial = 0

  const build = (personId: number, viaUnion: number | null, birthLink: boolean): TreeNode | null => {
    const person = personById.get(personId)
    if (!person) {
      return null
    }
    const key = `${personId}-${serial++}`
    // A cousin marriage reaches one person by two lines; the second is drawn as a pointer, not expanded again.
    if (expanded.has(personId)) {
      return { key, person, unions: [], viaUnion, birthLink, repeat: true, children: [] }
    }
    expanded.add(personId)

    const own = [...(unionsByPartner.get(personId) ?? [])].sort((a, b) => a.orderIndex - b.orderIndex)
    const unions: UnionSlot[] = []
    const children: TreeNode[] = []

    own.forEach((union, unionIndex) => {
      const spouseId = union.partner1Id === personId ? union.partner2Id : union.partner1Id
      unions.push({
        familyId: union.familyId,
        spouse: spouseId != null ? (personById.get(spouseId) ?? null) : null,
      })
      const ordered = [...union.children].sort(
        (a, b) => (a.birthOrder ?? Number.MAX_SAFE_INTEGER) - (b.birthOrder ?? Number.MAX_SAFE_INTEGER),
      )
      for (const link of ordered) {
        const child = build(link.childId, unionIndex, relationTo(union, link, personId) === 'BIRTH')
        if (child) {
          children.push(child)
        }
      }
    })

    return { key, person, unions, viaUnion, birthLink, repeat: false, children }
  }

  return build(subgraph.focusId, null, true)
}

/**
 * Builds the pedigree chart rooted at the focus person, with parents as children of the node.
 *
 * @param subgraph the slice returned by the tree endpoint
 * @returns the root node, or null when the focus person is missing from the slice
 */
export function buildAncestorTree(subgraph: Subgraph): TreeNode | null {
  // Built downward and flipped at render time: d3's layout only knows one direction.
  const { personById, unionsByChild } = index(subgraph)
  const expanded = new Set<number>()
  let serial = 0

  const build = (personId: number, birthLink: boolean): TreeNode | null => {
    const person = personById.get(personId)
    if (!person) {
      return null
    }
    const key = `${personId}-${serial++}`
    // Cousins who married share ancestors; the second branch still shows who, instead of just ending.
    if (expanded.has(personId)) {
      return { key, person, unions: [], viaUnion: null, birthLink, repeat: true, children: [] }
    }
    expanded.add(personId)

    const parents: TreeNode[] = []
    for (const union of unionsByChild.get(personId) ?? []) {
      const link = union.children.find((child) => child.childId === personId)
      for (const parentId of [union.partner1Id, union.partner2Id]) {
        if (parentId != null && link) {
          const parent = build(parentId, relationTo(union, link, parentId) === 'BIRTH')
          if (parent) {
            parents.push(parent)
          }
        }
      }
    }

    return { key, person, unions: [], viaUnion: null, birthLink, repeat: false, children: parents }
  }

  return build(subgraph.focusId, true)
}
