/** Recorded sex of a person; old records frequently omit it. */
export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN'

/** Which kind of name this is, of the three or four a cụ tổ commonly has. */
export type PersonNameType = 'BIRTH' | 'HUY' | 'TU' | 'HIEU' | 'THUY' | 'SAINT' | 'ALIAS'

/** One of a person's names, split surname / middle / given. */
export interface PersonName {
  id: number
  type: PersonNameType
  surname: string | null
  middleName: string | null
  givenName: string
  primary: boolean
  // The name rendered surname-first, as Vietnamese names are always written.
  display: string
}

/** The fields every shape of a person carries. */
interface PersonCore {
  id: number
  displayName: string
  gender: Gender
  // Đời — null until the parentage graph places them.
  generation: number | null
  // A privacy flag, not a fact: false also means "born over a century ago", with no death recorded.
  living: boolean
}

/** The least of a person a link needs: the fields every role may see, as `GET /persons/nodes` returns them. */
export type PersonNode = PersonCore

/** A person in list form. */
export interface PersonSummary extends PersonCore {
  // True only for a recorded death or burial, so "Đã mất" is never said of a mere presumption.
  deathRecorded: boolean
}

/** A person in full, as returned to a caller allowed to see them. */
export interface PersonDetail extends PersonCore {
  notes: string | null
  // Chi / phái — on the detail only, because a list is not redacted per row (CLAUDE.md §3.6).
  branchId: number | null
  names: PersonName[]
  createdAt: string
  // The row version an edit must be sent back with, so a stale form is refused rather than overwriting.
  version: number
  redacted?: false
}

/**
 * A living person as returned to a MEMBER: enough to place them in the tree, nothing more (CLAUDE.md §3.6).
 */
// `redacted` is the discriminant: a missing field is also what a leak looks like, so never test for one.
export interface PersonRedacted {
  id: number
  displayName: string
  gender: Gender
  generation: number | null
  living: true
  redacted: true
}

/** What the API returns for one person: the full record, or the redacted one. */
export type PersonView = PersonDetail | PersonRedacted

/**
 * Narrows a person view to the redacted shape.
 *
 * @param person the view the API returned
 * @returns true when the caller may not see this person's details
 */
export function isRedacted(person: PersonView): person is PersonRedacted {
  return person.redacted === true
}

/** One name in a create/update payload. */
export interface PersonNamePayload {
  type: PersonNameType
  surname?: string | null
  middleName?: string | null
  givenName: string
  primary: boolean
}

/** Payload for creating or updating a person, of which only the name list is required. */
export interface PersonPayload {
  gender?: Gender
  branchId?: number | null
  notes?: string | null
  names: PersonNamePayload[]
  // Why the edit was made, for the change history (§3.8).
  changeNote?: string | null
  // The version the edit was made from; omitted on create.
  version?: number
}
