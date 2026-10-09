import type { GenealogyDate } from '@/features/event/types'
import type { Gender, PersonNamePayload, PersonNameType } from '@/features/person/types'

/** What kind of record a suggestion is about. */
export type SuggestionTargetType = 'PERSON'

/** What a suggestion is asking for. */
export type SuggestionKind = 'UPDATE' | 'CREATE' | 'NOTE'

/** Where a suggestion is in the review queue. */
export type SuggestionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** Which relation a suggested new person has to the person they are added to. */
export type AnchorKind = 'SPOUSE' | 'CHILD'

/** The person a suggested new person is added to, and how. */
export interface SuggestedAnchor {
  personId: number
  kind: AnchorKind
  // The union a child is born into; null for "a new one-parent union".
  familyId: number | null
}

/** What a member proposes about a person: never chi or notes, which a MEMBER cannot read (§8.9 D1). */
export interface SuggestedPerson {
  names?: PersonNamePayload[] | null
  gender?: Gender | null
  birth?: Partial<GenealogyDate> | null
  death?: Partial<GenealogyDate> | null
  anchor?: SuggestedAnchor | null
}

/** One name as the screen shows it. */
export interface ProposedName {
  type: PersonNameType
  display: string
  primary: boolean
}

/** A person's names, sex and life dates, rendered, on one side of a comparison. */
export interface PersonSide {
  names: ProposedName[]
  gender: Gender | null
  birth: string | null
  death: string | null
}

/** Whom a suggested new person would be added to, named. */
export interface AnchorView {
  personId: number
  personName: string | null
  kind: AnchorKind
  familyId: number | null
}

/** What approving a suggestion would change: the person as they are, and as approval would leave them. */
export interface SuggestionPreview {
  current: PersonSide | null
  proposed: PersonSide | null
  anchor: AnchorView | null
}

/** One proposed change offered by a member who cannot edit the gia phả directly. */
export interface Suggestion {
  id: number
  targetType: SuggestionTargetType
  targetId: number | null
  // The display name of the person it is about, so the queue reads without a call per row.
  targetName: string | null
  kind: SuggestionKind
  // What was proposed, with no current state, so its author may see it too.
  proposal: PersonSide | null
  anchor: AnchorView | null
  // False when the stored proposal no longer reads; such a suggestion can only be rejected.
  payloadReadable: boolean
  message: string
  status: SuggestionStatus
  createdBy: number | null
  createdByName: string | null
  createdAt: string
  reviewedBy: number | null
  reviewedByName: string | null
  reviewedAt: string | null
  reviewNote: string | null
}

/** Payload for offering a suggestion. */
export interface SuggestionPayload {
  targetType: SuggestionTargetType
  targetId?: number | null
  kind: SuggestionKind
  person?: SuggestedPerson | null
  message: string
}

/** Payload for approving or rejecting a suggestion. */
export interface SuggestionReviewPayload {
  approve: boolean
  reviewNote?: string | null
}
