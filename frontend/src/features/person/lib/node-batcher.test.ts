import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchPersonNodes } from '@/features/person/api'
import type { PersonNode } from '@/features/person/types'

import { loadPersonNode } from './node-batcher'

vi.mock('@/features/person/api', () => ({
  MAX_NODES_PER_REQUEST: 200,
  fetchPersonNodes: vi.fn(),
}))

const fetchMock = vi.mocked(fetchPersonNodes)

/**
 * Builds the node the server would return for an id.
 *
 * @param id the person id
 * @returns the node
 */
function node(id: number): PersonNode {
  return { id, displayName: `Người ${id}`, gender: 'UNKNOWN', generation: null, living: false }
}

describe('loadPersonNode', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockImplementation((ids) => Promise.resolve(ids.map(node)))
  })

  it('shares one request among every name asked for in the same tick', async () => {
    const names = await Promise.all([loadPersonNode(1), loadPersonNode(2), loadPersonNode(1)])

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith([1, 2])
    expect(names.map((found) => found?.displayName)).toEqual(['Người 1', 'Người 2', 'Người 1'])
  })

  it('splits a batch larger than the server accepts', async () => {
    await Promise.all(Array.from({ length: 250 }, (_, at) => loadPersonNode(at + 1)))

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0][0]).toHaveLength(200)
    expect(fetchMock.mock.calls[1][0]).toHaveLength(50)
  })

  it('answers null for someone the server did not return', async () => {
    fetchMock.mockResolvedValue([node(1)])

    const [known, gone] = await Promise.all([loadPersonNode(1), loadPersonNode(99)])

    expect(known?.id).toBe(1)
    expect(gone).toBeNull()
  })

  it('fails every waiter of a chunk whose request failed', async () => {
    fetchMock.mockRejectedValue(new Error('offline'))

    const results = await Promise.allSettled([loadPersonNode(1), loadPersonNode(2)])

    expect(results.map((result) => result.status)).toEqual(['rejected', 'rejected'])
  })
})
