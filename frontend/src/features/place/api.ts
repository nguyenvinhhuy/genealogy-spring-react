import type { QueryClient } from '@tanstack/react-query'

import { apiClient } from '@/shared/lib/api-client'
import { createBatcher } from '@/shared/lib/batcher'
import type { Page } from '@/shared/types/page'

import type { Place, PlacePayload } from './types'

// The most ids `GET /places/batch` accepts in one request.
export const MAX_PLACES_PER_REQUEST = 200

// The prefix every place query key starts with, so one write refreshes every list, picker and name (§9).
export const PLACE_QUERY_KEY = ['places'] as const

/**
 * Finds places by name, accent-insensitively, in name order.
 *
 * @param query the text to search for, or empty for any place
 * @param size how many rows to return
 * @returns one page of matching places
 */
export async function searchPlaces(query: string, size: number): Promise<Page<Place>> {
  const response = await apiClient.get<Page<Place>>('/places', {
    params: { query: query || undefined, size },
  })
  return response.data
}

/**
 * Lists every place of the clan, for the page that shows them as a tree.
 *
 * @returns every place
 */
export async function fetchAllPlaces(): Promise<Place[]> {
  const response = await apiClient.get<Place[]>('/places/all')
  return response.data
}

/**
 * Fetches several places at once.
 *
 * @param ids the places wanted, at most {@link MAX_PLACES_PER_REQUEST}
 * @returns the places that exist
 */
export async function fetchPlaces(ids: number[]): Promise<Place[]> {
  const response = await apiClient.get<Place[]>('/places/batch', { params: { ids: ids.join(',') } })
  return response.data
}

const placeBatcher = createBatcher(fetchPlaces, (place) => place.id, MAX_PLACES_PER_REQUEST)

/**
 * Loads one place, sharing one request with every other place asked for in the same tick.
 *
 * @param id the place id
 * @returns the place, or null when there is no such place
 */
export function loadPlace(id: number): Promise<Place | null> {
  return placeBatcher.load(id)
}

/**
 * Creates a place.
 *
 * @param payload the place to create
 * @returns the created place
 */
export async function createPlace(payload: PlacePayload): Promise<Place> {
  const response = await apiClient.post<Place>('/places', payload)
  return response.data
}

/**
 * Renames, re-levels or moves a place.
 *
 * @param id the place id
 * @param payload the new values
 * @returns the updated place
 */
export async function updatePlace(id: number, payload: PlacePayload): Promise<Place> {
  const response = await apiClient.put<Place>(`/places/${id}`, payload)
  return response.data
}

/**
 * Deletes a place that no event, grave or smaller place still names.
 *
 * @param id the place id
 * @param changeNote why the place is being deleted, or null
 */
export async function deletePlace(id: number, changeNote: string | null): Promise<void> {
  await apiClient.delete(`/places/${id}`, { params: { changeNote: changeNote || undefined } })
}

/**
 * Refetches every place list, picker and rendered path after a place write.
 *
 * @param queryClient the app's query client
 */
export async function invalidatePlaceQueries(queryClient: QueryClient): Promise<void> {
  // A rename changes the path of every place beneath it, so single-place entries go too.
  await queryClient.invalidateQueries({ queryKey: PLACE_QUERY_KEY })
}
