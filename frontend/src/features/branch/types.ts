/** One chi, phái or nhánh of the clan. */
export interface Branch {
  id: number
  name: string
  // The branch this one splits from, or null for a top-level chi.
  parentId: number | null
  description: string | null
  version: number
}

/** Payload for creating or updating a branch. */
export interface BranchPayload {
  name: string
  parentId: number | null
  description: string | null
  changeNote: string | null
  version: number | null
}
