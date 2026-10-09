// Every kind of grave record: a mộ, or a sinh phần built for someone still alive.
export const GRAVE_KINDS = ['GRAVE', 'LIVING_PLOT'] as const

/** A mộ counts as a recorded death; a sinh phần counts for nothing and stays private while its owner lives. */
export type GraveKind = (typeof GRAVE_KINDS)[number]

/** Where a person's mộ phần is now; each earlier location is a cải táng event. */
export interface Grave {
  id: number
  personId: number
  kind: GraveKind
  placeId: number | null
  // Free text: Vietnamese cemeteries share no numbering scheme.
  plot: string | null
  // Numbers, as the server sends them: typed as strings, a located grave crashed its own edit form (§8.8 #1).
  latitude: number | null
  longitude: number | null
  notes: string | null
  // The row version an edit must be sent back with, so a stale form is refused rather than overwriting.
  version: number
}

/** Payload for recording or replacing a mộ phần: every field is sent, and an omitted one is cleared. */
export interface GravePayload {
  kind: GraveKind
  placeId: number | null
  plot: string | null
  latitude: number | null
  longitude: number | null
  notes: string | null
  changeNote: string | null
  version: number | null
}
