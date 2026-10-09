import { apiClient } from '@/shared/lib/api-client'

import type { Anniversary, EventPayload, EventSubjectType, GenealogyEvent } from './types'

/**
 * Lists the ngày giỗ falling within the next stretch of days, soonest first.
 *
 * @param days how many days ahead to look
 * @returns the upcoming anniversaries
 */
export async function fetchAnniversaries(days: number): Promise<Anniversary[]> {
  const response = await apiClient.get<Anniversary[]>('/events/anniversaries', {
    params: { days },
  })
  return response.data
}

/**
 * Lists the events of one subject, oldest first.
 *
 * @param subjectType whether the subject is a person or a union
 * @param subjectId the subject id
 * @returns the events
 */
export async function fetchEvents(
  subjectType: EventSubjectType,
  subjectId: number,
): Promise<GenealogyEvent[]> {
  const response = await apiClient.get<GenealogyEvent[]>('/events', {
    params: { subjectType, subjectId },
  })
  return response.data
}

/**
 * Creates an event.
 *
 * @param payload the event to create
 * @returns the created event
 */
export async function createEvent(payload: EventPayload): Promise<GenealogyEvent> {
  const response = await apiClient.post<GenealogyEvent>('/events', payload)
  return response.data
}

/**
 * Replaces an event's type, date, place and description.
 *
 * @param id the event id
 * @param payload the new values, carrying the version the edit was made from
 * @returns the updated event
 */
export async function updateEvent(id: number, payload: EventPayload): Promise<GenealogyEvent> {
  const response = await apiClient.put<GenealogyEvent>(`/events/${id}`, payload)
  return response.data
}

/**
 * Deletes an event.
 *
 * @param id the event id
 * @param changeNote why it is being deleted, for the change history
 */
export async function deleteEvent(id: number, changeNote?: string): Promise<void> {
  await apiClient.delete(`/events/${id}`, { params: { changeNote: changeNote || undefined } })
}
