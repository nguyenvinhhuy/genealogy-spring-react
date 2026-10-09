/** The shape a record needs to be laid out as a tree: its own id, its parent's, and a name to sort by. */
export interface TreeRecord {
  id: number
  parentId: number | null
  name: string
}

/** One record flattened for a list or a picker, with its full path and how deep it sits. */
export interface HierarchyOption {
  id: number
  depth: number
  // "Chi 1 › Phái 2", built from every ancestor's name so two records named alike are never confused.
  path: string
}

/**
 * Flattens a tree into a depth-first, name-ordered list with each record's root-first path.
 *
 * @param records every record, in any order
 * @returns the options, top-level records first and each one's children directly beneath it
 */
export function flattenHierarchy(records: readonly TreeRecord[]): HierarchyOption[] {
  const byParent = new Map<number | null, TreeRecord[]>()
  const ids = new Set(records.map((record) => record.id))
  for (const record of records) {
    // A parent missing from the list would hide its whole subtree, so such a record is shown at the top.
    const parentId = record.parentId != null && ids.has(record.parentId) ? record.parentId : null
    const siblings = byParent.get(parentId) ?? []
    siblings.push(record)
    byParent.set(parentId, siblings)
  }
  for (const siblings of byParent.values()) {
    siblings.sort((a, b) => a.name.localeCompare(b.name, 'vi'))
  }

  const options: HierarchyOption[] = []
  const seen = new Set<number>()
  const walk = (parentId: number | null, depth: number, parentPath: string) => {
    for (const record of byParent.get(parentId) ?? []) {
      // Bounded by `seen`: a parent cycle is refused on write, and would otherwise recurse for ever.
      if (seen.has(record.id)) {
        continue
      }
      seen.add(record.id)
      const path = parentPath ? `${parentPath} › ${record.name}` : record.name
      options.push({ id: record.id, depth, path })
      walk(record.id, depth + 1, path)
    }
  }
  walk(null, 0, '')
  return options
}

/**
 * Finds a record and every record beneath it, so a parent picker can exclude its own subtree.
 *
 * @param records every record
 * @param rootId the record whose subtree to collect
 * @returns the ids of that record and all its descendants
 */
export function subtreeOf(records: readonly TreeRecord[], rootId: number): Set<number> {
  const children = new Map<number, number[]>()
  for (const record of records) {
    if (record.parentId != null) {
      const siblings = children.get(record.parentId) ?? []
      siblings.push(record.id)
      children.set(record.parentId, siblings)
    }
  }
  const ids = new Set<number>([rootId])
  const queue = [rootId]
  while (queue.length > 0) {
    const current = queue.shift()!
    for (const childId of children.get(current) ?? []) {
      if (!ids.has(childId)) {
        ids.add(childId)
        queue.push(childId)
      }
    }
  }
  return ids
}
