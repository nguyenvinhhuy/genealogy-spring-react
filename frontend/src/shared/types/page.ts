/** Paging metadata, mirroring Spring's `PagedModel` JSON exactly. */
export interface PageMetadata {
  size: number
  number: number
  totalElements: number
  totalPages: number
}

/** One page of results. */
export interface Page<T> {
  content: T[]
  page: PageMetadata
}
