import { fetchPersonNodes, MAX_NODES_PER_REQUEST } from '@/features/person/api'
import type { PersonNode } from '@/features/person/types'
import { type Batcher, createBatcher } from '@/shared/lib/batcher'

// Built on first use rather than at import, so it is created after a test has mocked the api module.
let batcher: Batcher<PersonNode> | null = null

/**
 * Loads one person's node, sharing a single request with every other node asked for in the same tick.
 *
 * @param id the person id
 * @returns the node, or null when there is no such person
 */
export function loadPersonNode(id: number): Promise<PersonNode | null> {
  // A cụ with three wives and twelve children put fifteen requests on one page; now it is one.
  batcher ??= createBatcher(fetchPersonNodes, (node) => node.id, MAX_NODES_PER_REQUEST)
  return batcher.load(id)
}
