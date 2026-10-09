/** What kind of record a photo or scan belongs to. */
export type MediaTargetType = 'PERSON' | 'FAMILY' | 'GRAVE' | 'SOURCE'

/** What a stored file is, which decides where it is shown. */
export type MediaKind = 'PORTRAIT' | 'SCAN' | 'PHOTO' | 'DOCUMENT'

/** One photo or scan attached to a record. */
export interface Media {
  id: number
  targetType: MediaTargetType
  targetId: number
  kind: MediaKind
  // Where a browser can fetch the original; short-lived when the provider signs its URLs.
  url: string
  // A small copy for the gallery; for an old upload with none, the original again.
  thumbnailUrl: string
  contentType: string
  sizeBytes: number
  filename: string
  caption: string | null
  sortOrder: number
  uploadedBy: number | null
  uploadedByName: string | null
  createdAt: string
  // The row version an edit must be sent back with, so a stale form is refused rather than overwriting.
  version: number
}

/** Payload for editing what is recorded about an already-uploaded file. */
export interface MediaUpdatePayload {
  kind?: MediaKind | null
  caption?: string | null
  sortOrder?: number | null
  // Why the edit was made, for the change history (§3.8).
  changeNote?: string | null
  version?: number
}
