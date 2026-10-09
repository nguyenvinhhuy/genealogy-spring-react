import type { FamilyStatus, RelationType } from '@/features/family/types'
import type { Gender } from '@/features/person/types'

/** Which way a tree request expands from its focus person. */
export type TreeDirection = 'DESCENDANTS' | 'ANCESTORS' | 'HOURGLASS'

/** The minimum a person needs to be drawn as a node. */
export interface PersonNode {
  id: number
  displayName: string
  gender: Gender
  generation: number | null
  living: boolean
}

/** One child inside a union edge. */
export interface ChildEdge {
  childId: number
  relationToP1: RelationType
  relationToP2: RelationType
  birthOrder: number | null
}

/** One union and its children, flattened for traversal. */
export interface UnionEdge {
  familyId: number
  partner1Id: number | null
  partner2Id: number | null
  status: FamilyStatus
  orderIndex: number
  children: ChildEdge[]
}

/** Which of the speaker's parents a kinship path runs through. */
export type KinshipSide = 'PATERNAL' | 'MATERNAL'

/**
 * What one person is to another, in Vietnamese.
 */
// `term` is the word the speaker uses for the target, and is null when no blood path was found.
export interface Kinship {
  fromId: number
  toId: number
  term: string | null
  stepsUp: number
  stepsDown: number
  commonAncestorId: number | null
  side: KinshipSide | null
}

/**
 * The slice of the gia phả around one focus person.
 */
// Each half says whether family lies beyond it, so the UI offers to go deeper instead of implying an end.
export interface Subgraph {
  focusId: number
  direction: TreeDirection
  depth: number
  truncatedBelow: boolean
  truncatedAbove: boolean
  persons: PersonNode[]
  unions: UnionEdge[]
  recordedDeadIds: number[]
}
