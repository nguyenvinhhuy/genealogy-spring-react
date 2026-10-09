/** What a revision did to the record. */
export type AuditAction = 'CREATE' | 'UPDATE' | 'DELETE'

/** What kind of record one revision describes, mirroring the backend's discriminator (CLAUDE.md §4). */
export type AuditEntityType =
  | 'PERSON'
  | 'FAMILY'
  | 'EVENT'
  | 'BRANCH'
  | 'PLACE'
  | 'SOURCE'
  | 'CITATION'
  | 'GRAVE'
  | 'MEDIA'
  | 'SUGGESTION'
  | 'MEMBER'

/** One recorded change (CLAUDE.md §3.8). */
export interface Revision {
  id: number
  entityType: AuditEntityType
  entityId: number
  action: AuditAction
  // The record as it was, as a JSON string; null on CREATE.
  beforeData: string | null
  // The record as it became, as a JSON string; null on DELETE.
  afterData: string | null
  changedBy: number | null
  changedByName: string | null
  // Why the change was made — the "on what basis".
  note: string | null
  changedAt: string
}
