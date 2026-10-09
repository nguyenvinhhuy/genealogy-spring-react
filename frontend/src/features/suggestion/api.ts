import type { QueryClient } from '@tanstack/react-query'

import { apiClient } from '@/shared/lib/api-client'
import type { Page } from '@/shared/types/page'

import type {
  Suggestion,
  SuggestionPayload,
  SuggestionPreview,
  SuggestionReviewPayload,
  SuggestionStatus,
} from './types'

// The first element of every suggestion query key, the pending count included.
export const SUGGESTION_QUERY_KEY = 'suggestions'

// What an applied suggestion can change: a name, a sex, a date, or a whole new person in a union.
const AFFECTED_BY_AN_APPROVAL = [
  'person',
  'persons',
  'families',
  'events',
  'anniversaries',
  'grave',
  'tree',
  'kinship',
  'quality',
] as const

/**
 * Lists suggestions newest first, optionally filtered by review state; a MEMBER gets only their own.
 *
 * @param status the state to filter by, omit for all of them
 * @param page which page, from 0
 * @param size how many per page
 * @returns one page of suggestions
 */
export async function fetchSuggestions(
  status: SuggestionStatus | undefined,
  page: number,
  size: number,
): Promise<Page<Suggestion>> {
  const response = await apiClient.get<Page<Suggestion>>('/suggestions', { params: { status, page, size } })
  return response.data
}

/**
 * Counts the suggestions still waiting for a reviewer; a MEMBER's own only.
 *
 * @returns how many are pending
 */
export async function fetchPendingCount(): Promise<number> {
  const response = await apiClient.get<number>('/suggestions/pending-count')
  return response.data
}

/**
 * Shows what approving a suggestion would change, field by field (EDITOR+).
 *
 * @param id suggestion id
 * @returns the person as they are and as approval would leave them
 */
export async function fetchSuggestionPreview(id: number): Promise<SuggestionPreview> {
  const response = await apiClient.get<SuggestionPreview>(`/suggestions/${id}/preview`)
  return response.data
}

/**
 * Offers a suggestion.
 *
 * @param payload what is being suggested
 * @returns the recorded suggestion
 */
export async function createSuggestion(payload: SuggestionPayload): Promise<Suggestion> {
  const response = await apiClient.post<Suggestion>('/suggestions', payload)
  return response.data
}

/**
 * Approves or rejects a suggestion, applying its proposal when approved.
 *
 * @param id suggestion id
 * @param payload the decision and its reason
 * @returns the reviewed suggestion
 */
export async function reviewSuggestion(id: number, payload: SuggestionReviewPayload): Promise<Suggestion> {
  const response = await apiClient.post<Suggestion>(`/suggestions/${id}/review`, payload)
  return response.data
}

/**
 * Refetches what a review changed: the queue always, the gia phả only when something was written to it.
 *
 * @param queryClient the app's query client
 * @param reviewed the suggestion as the review left it
 */
export async function invalidateAfterReview(queryClient: QueryClient, reviewed: Suggestion): Promise<void> {
  // A rejection or a note writes nothing, so refetching every chart and the quality page for it was waste (#34).
  const applied = reviewed.status === 'APPROVED' && reviewed.kind !== 'NOTE'
  const keys = [SUGGESTION_QUERY_KEY, 'revisions', ...(applied ? AFFECTED_BY_AN_APPROVAL : [])]
  await Promise.all(keys.map((key) => queryClient.invalidateQueries({ queryKey: [key] })))
}
