// Every kind of evidence a source can be, in the order a picker offers them.
export const SOURCE_TYPES = ['CLAN_BOOK', 'ORAL', 'DOCUMENT', 'HEADSTONE', 'PHOTO', 'WEBSITE', 'OTHER'] as const

/** What kind of evidence a source is. */
export type SourceType = (typeof SOURCE_TYPES)[number]

/** What kind of record a citation backs up; a GRAVE is cited from its bia mộ. */
export type CitationTargetType = 'PERSON' | 'FAMILY' | 'EVENT' | 'GRAVE'

/** Where a recorded claim came from. */
export interface Source {
  id: number
  title: string
  type: SourceType
  author: string | null
  // Free text: "1923", "khoảng đời Bảo Đại" and "không rõ" are all real answers.
  dateText: string | null
  repository: string | null
  notes: string | null
  citationCount: number
  // The row version an edit must be sent back with, so a stale form is refused rather than overwriting.
  version: number
}

/** One citation, carrying enough of its source to render without a second call. */
export interface Citation {
  id: number
  sourceId: number
  sourceTitle: string | null
  sourceType: SourceType | null
  targetType: CitationTargetType
  targetId: number
  // Where inside the source: "trang 12", "mặt sau bia".
  locator: string | null
  quote: string | null
  version: number
}

/** Payload for creating or updating a source. */
export interface SourcePayload {
  title: string
  type?: SourceType
  author?: string | null
  dateText?: string | null
  repository?: string | null
  notes?: string | null
  changeNote?: string | null
  version?: number | null
}

/** Payload for citing a source, naming either an existing source or a new one to create with it. */
export interface CitationPayload {
  sourceId: number | null
  newSource: SourcePayload | null
  targetType: CitationTargetType
  targetId: number
  locator: string | null
  quote: string | null
  changeNote: string | null
  version: number | null
}

/** What folding one source into another did. */
export interface SourceMergeResult {
  source: Source
  citationsMoved: number
  // How many scan files moved onto the kept source along with its citations.
  mediaMoved: number
  // One line per citation that duplicated one the kept source already had, and was folded into it.
  folded: string[]
}
