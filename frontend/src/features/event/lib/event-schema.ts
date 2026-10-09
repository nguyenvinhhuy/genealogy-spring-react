import type { TFunction } from 'i18next'
import { z } from 'zod'

import type { EventSubjectType, EventType } from '@/features/event/types'
import { buildChangeNoteField } from '@/shared/lib/change-note'

// Each subject kind has its own events: a person is born, a union is married.
export const EVENT_TYPES_BY_SUBJECT: Record<EventSubjectType, readonly EventType[]> = {
  PERSON: ['BIRTH', 'DEATH', 'BURIAL', 'REBURIAL', 'RESIDENCE', 'OCCUPATION', 'EDUCATION', 'OTHER_PERSON'],
  FAMILY: ['MARRIAGE', 'DIVORCE', 'OTHER_FAMILY'],
}

/**
 * Builds the event form schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildEventSchema(t: TFunction) {
  return z.object({
    type: z.string().min(1, t('event.validation.typeRequired')),
    // Kept as the parsed object the date field emits; the server validates the parts (§3.2).
    date: z.custom<Record<string, unknown> | null>(),
    placeId: z.number().nullable(),
    description: z.string(),
    changeNote: buildChangeNoteField(t),
  })
}

/** What the event form holds. */
export type EventFormValues = z.infer<ReturnType<typeof buildEventSchema>>
