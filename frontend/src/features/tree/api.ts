import { apiClient } from '@/shared/lib/api-client'

import type { Kinship, Subgraph, TreeDirection } from './types'

/**
 * Works out what one person is to another, in Vietnamese.
 *
 * @param fromId the speaker
 * @param toId the person being named
 * @returns the relationship
 */
export async function fetchKinship(fromId: number, toId: number): Promise<Kinship> {
  const response = await apiClient.get<Kinship>('/tree/kinship', { params: { fromId, toId } })
  return response.data
}

/**
 * Fetches the clan's thuỷ tổ, whom the tree opens on when nobody has been picked.
 *
 * @returns the founder's person id, or null when no union is recorded yet
 */
export async function fetchFounderId(): Promise<number | null> {
  const response = await apiClient.get<{ personId: number | null }>('/tree/founder')
  return response.data.personId
}

/**
 * Fetches the slice of the gia phả around one person.
 *
 * @param focusId the person to centre on
 * @param direction which way to expand
 * @param depth how many generations to walk; the server clamps it
 * @returns the subgraph
 */
export async function fetchSubgraph(
  focusId: number,
  direction: TreeDirection,
  depth: number,
): Promise<Subgraph> {
  // A focus person and a depth are always required, so neither the query nor the chart can grow unbounded.
  const response = await apiClient.get<Subgraph>('/tree', {
    params: { focusId, direction, depth },
  })
  return response.data
}
