/** Payload for merging one person into another. */
export interface MergePayload {
  // The person to keep — their id, primary name and đời survive.
  targetId: number
  // The person to absorb and then delete.
  duplicateId: number
  // Why the two are the same person; recorded in the audit trail as the basis.
  reason: string
}

/** What a merge moved, so the result can be read rather than trusted. */
export interface MergeResult {
  targetId: number
  targetName: string
  duplicateId: number
  namesMoved: number
  unionsMoved: number
  unionsCollapsed: number
  childLinksMoved: number
  eventsMoved: number
  citationsMoved: number
  // What was dropped rather than moved, one line each.
  notes: string[]
}
