import type { QueryClient } from '@tanstack/react-query'

import { apiClient } from '@/shared/lib/api-client'
import type { Page } from '@/shared/types/page'

import type { Citation, CitationPayload, CitationTargetType, Source, SourceMergeResult, SourcePayload } from './types'

// The prefix of every source list and picker query, refreshed by any write that changes a citation count.
export const SOURCE_QUERY_KEY = ['sources'] as const

/**
 * Searches sources by title, ignoring accents, in title order (EDITOR+).
 *
 * @param query the search text, or empty for every source
 * @param page which page, from zero
 * @param size how many rows per page
 * @returns one page of matching sources
 */
export async function searchSources(query: string, page: number, size: number): Promise<Page<Source>> {
  const response = await apiClient.get<Page<Source>>('/sources', {
    params: { query: query || undefined, page, size },
  })
  return response.data
}

/**
 * Creates a source.
 *
 * @param payload the source to create
 * @returns the created source
 */
export async function createSource(payload: SourcePayload): Promise<Source> {
  const response = await apiClient.post<Source>('/sources', payload)
  return response.data
}

/**
 * Updates a source.
 *
 * @param id the source id
 * @param payload the new values
 * @returns the updated source
 */
export async function updateSource(id: number, payload: SourcePayload): Promise<Source> {
  const response = await apiClient.put<Source>(`/sources/${id}`, payload)
  return response.data
}

/**
 * Deletes a source that nothing cites.
 *
 * @param id the source id
 * @param changeNote why the source is being deleted, or null
 */
export async function deleteSource(id: number, changeNote: string | null): Promise<void> {
  await apiClient.delete(`/sources/${id}`, { params: { changeNote: changeNote || undefined } })
}

/**
 * Folds one source into another: its citations move onto the kept one, and it is deleted (ADMIN).
 *
 * @param duplicateId the source to fold away
 * @param targetId the source to keep
 * @param reason why the two are the same source
 * @returns the kept source and what moved
 */
export async function mergeSources(duplicateId: number, targetId: number, reason: string): Promise<SourceMergeResult> {
  const response = await apiClient.post<SourceMergeResult>('/sources/merge', { duplicateId, targetId, reason })
  return response.data
}

/**
 * Lists the citations backing up one record.
 *
 * @param targetType what kind of record
 * @param targetId the record id
 * @returns the citations, or none when the record involves a living person the caller may not see
 */
export async function fetchCitations(targetType: CitationTargetType, targetId: number): Promise<Citation[]> {
  const response = await apiClient.get<Citation[]>('/citations', { params: { targetType, targetId } })
  return response.data
}

/**
 * Cites a source against one recorded fact, creating the source in the same request when it is new.
 *
 * @param payload the citation to add
 * @returns the created citation
 */
export async function addCitation(payload: CitationPayload): Promise<Citation> {
  const response = await apiClient.post<Citation>('/citations', payload)
  return response.data
}

/**
 * Changes a citation's source, locator or quote.
 *
 * @param id the citation id
 * @param payload the new values, naming the citation's own target
 * @returns the updated citation
 */
export async function updateCitation(id: number, payload: CitationPayload): Promise<Citation> {
  const response = await apiClient.put<Citation>(`/citations/${id}`, payload)
  return response.data
}

/**
 * Removes one citation, leaving its source alone (ADMIN).
 *
 * @param id the citation id
 * @param changeNote why the citation is being removed, or null
 */
export async function removeCitation(id: number, changeNote: string | null): Promise<void> {
  await apiClient.delete(`/citations/${id}`, { params: { changeNote: changeNote || undefined } })
}

/**
 * Refetches one record's citations and every source list after a citation or source write.
 *
 * @param queryClient the app's query client
 * @param targetType the kind of record whose citations changed, or null for a write that touches many
 * @param targetId that record's id
 */
export async function invalidateCitationQueries(
  queryClient: QueryClient,
  targetType: CitationTargetType | null,
  targetId?: number,
): Promise<void> {
  // Sources too: every add or remove changes a citation count, which the source list shows (§8.8 #49).
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: targetType ? ['citations', targetType, targetId] : ['citations'] }),
    queryClient.invalidateQueries({ queryKey: SOURCE_QUERY_KEY }),
    queryClient.invalidateQueries({ queryKey: ['revisions'] }),
  ])
}
