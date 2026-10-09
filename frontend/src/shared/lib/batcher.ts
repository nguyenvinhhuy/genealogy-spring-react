/** One caller waiting for a record. */
interface Waiter<T> {
  resolve: (record: T | null) => void
  reject: (reason: unknown) => void
}

/** Loads records one id at a time from the caller's side, and in batches from the server's. */
export interface Batcher<T> {
  load: (id: number) => Promise<T | null>
}

/**
 * Builds a loader that shares one request among every id asked for in the same tick.
 *
 * @param fetchMany fetches the records for a list of ids
 * @param idOf reads a record's id
 * @param maxPerRequest the most ids the server accepts in one request
 * @returns the loader
 */
export function createBatcher<T>(
  fetchMany: (ids: number[]) => Promise<T[]>,
  idOf: (record: T) => number,
  maxPerRequest: number,
): Batcher<T> {
  // A page listing twelve people or twelve places asked twelve times; now it asks once.
  let pending = new Map<number, Waiter<T>[]>()
  let scheduled = false

  const flush = async () => {
    const batch = pending
    pending = new Map()
    scheduled = false
    const ids = [...batch.keys()]

    for (let start = 0; start < ids.length; start += maxPerRequest) {
      const chunk = ids.slice(start, start + maxPerRequest)
      try {
        const found = new Map((await fetchMany(chunk)).map((record) => [idOf(record), record]))
        chunk.forEach((id) => batch.get(id)?.forEach((waiter) => waiter.resolve(found.get(id) ?? null)))
      } catch (failure) {
        chunk.forEach((id) => batch.get(id)?.forEach((waiter) => waiter.reject(failure)))
      }
    }
  }

  return {
    load: (id) =>
      new Promise((resolve, reject) => {
        const waiters = pending.get(id) ?? []
        waiters.push({ resolve, reject })
        pending.set(id, waiters)
        if (!scheduled) {
          scheduled = true
          setTimeout(() => void flush(), 0)
        }
      }),
  }
}
