import type { QueryClient } from '@tanstack/react-query'

import type { EventSubjectType } from '@/features/event/types'

// An event moves `living`, the giỗ list, the chart's †, quality and history; deleting one takes its citations too.
const AFFECTED_BY_ANY_EVENT = [
  'person',
  'persons',
  'anniversaries',
  'tree',
  'quality',
  'revisions',
  'citations',
  'sources',
] as const

/**
 * Refetches everything an event write can change.
 *
 * @param queryClient the app's query client
 * @param subjectType whether the event belongs to a person or a union
 * @param subjectId the subject id
 */
export async function invalidateAfterEventChange(
  queryClient: QueryClient,
  subjectType: EventSubjectType,
  subjectId: number,
): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: ['events', subjectType, subjectId] })
  await Promise.all(AFFECTED_BY_ANY_EVENT.map((key) => queryClient.invalidateQueries({ queryKey: [key] })))
}
