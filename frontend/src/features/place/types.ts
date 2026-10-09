// Every level of the place hierarchy (CLAUDE.md §3.7), narrowest first, in the order a picker offers them.
export const PLACE_TYPES = ['VILLAGE', 'WARD', 'DISTRICT', 'PROVINCE', 'COUNTRY', 'OTHER'] as const

/** The level of a place; OTHER is a tổng, phủ or trấn with no modern rank, and fits anywhere. */
export type PlaceType = (typeof PLACE_TYPES)[number]

/** One place: a thôn, xã, huyện, tỉnh, country, or an old unit such as a tổng. */
export interface Place {
  id: number
  name: string
  type: PlaceType
  // The place this one lies within, or null for a place at the top of the tree.
  parentId: number | null
  latitude: number | null
  longitude: number | null
  // The place and every place above it, most specific first: "Xã A, Huyện B, Tỉnh C".
  path: string
  // The row version an edit must be sent back with, so a stale form is refused rather than overwriting.
  version: number
}

/** Payload for creating or updating a place. */
export interface PlacePayload {
  name: string
  type: PlaceType
  parentId: number | null
  latitude: number | null
  longitude: number | null
  // Why the edit was made, for the change history (§3.8).
  changeNote: string | null
  // The version the edit was made from; null on create.
  version: number | null
}
