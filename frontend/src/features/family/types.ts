import type { Gender, PersonNamePayload } from '@/features/person/types'

/** State of a union. */
export type FamilyStatus = 'MARRIED' | 'DIVORCED' | 'PARTNER' | 'UNKNOWN'

/** How a child relates to one partner of the union they were linked into. */
export type RelationType = 'BIRTH' | 'ADOPTED' | 'STEP' | 'FOSTER'

/** A child link inside a union. */
export interface FamilyChild {
  id: number
  childId: number
  relationToP1: RelationType
  relationToP2: RelationType
  // Con trưởng = 1, con thứ = 2; null when unrecorded.
  birthOrder: number | null
}

/** One union between two people. */
export interface Family {
  id: number
  partner1Id: number | null
  partner2Id: number | null
  status: FamilyStatus
  // Vợ cả = 0, vợ thứ = 1.
  orderIndex: number
  children: FamilyChild[]
  // The row version an edit must be sent back with, so a stale form is refused rather than overwriting.
  version: number
}

/** Payload for creating or updating a union. */
export interface FamilyPayload {
  partner1Id?: number | null
  partner2Id?: number | null
  status?: FamilyStatus
  orderIndex?: number
  // Why the edit was made, for the change history (§3.8).
  changeNote?: string | null
  // The version the edit was made from; omitted on create.
  version?: number
}

/** Payload for correcting a child's link into a union. */
export interface FamilyChildUpdatePayload {
  relationToP1: RelationType
  relationToP2: RelationType
  birthOrder: number | null
  // Why the edit was made, for the change history (§3.8).
  changeNote?: string | null
  // The union's version the form was read at: the link has none, and the union's stands for its children.
  version?: number
}

/** Which relation the add-relation flow creates. */
export type RelationKind = 'SPOUSE' | 'CHILD'

/** Payload for adding a new spouse or child to a person in one request. */
export interface AddRelationPayload {
  kind: RelationKind
  gender?: Gender
  names: PersonNamePayload[]
  // For a child, the union to join; omitted to start a one-parent union.
  familyId?: number | null
  changeNote?: string | null
}

/** What the person being linked is to the one the link is made from. */
export type LinkKind = 'SPOUSE' | 'CHILD' | 'PARENT'

/** Payload for linking two people who are both already recorded. */
export interface LinkRelationPayload {
  kind: LinkKind
  otherPersonId: number
  // The union to use: the person's own for a child, the other person's own for a parent; omitted for a new one.
  familyId?: number | null
  changeNote?: string | null
}

/** What adding a relation created. */
export interface AddRelationResult {
  personId: number
  family: Family
}
