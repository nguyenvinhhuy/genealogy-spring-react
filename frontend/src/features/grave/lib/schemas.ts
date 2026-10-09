import type { TFunction } from 'i18next'
import { z } from 'zod'

import { GRAVE_KINDS } from '@/features/grave/types'
import { buildChangeNoteField } from '@/shared/lib/change-note'
import { isCoordinateValid, MAX_LATITUDE, MAX_LONGITUDE, parseCoordinate } from '@/shared/lib/coordinates'

// Match GraveRequest.MAX_PLOT and MAX_NOTES on the server.
const MAX_PLOT = 200
const MAX_NOTES = 10000

/**
 * Builds the grave form schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildGraveSchema(t: TFunction) {
  return z
    .object({
      kind: z.enum(GRAVE_KINDS),
      placeId: z.number().nullable(),
      plot: z.string().max(MAX_PLOT, t('grave.validation.plotTooLong')),
      latitude: z
        .string()
        .refine((text) => isCoordinateValid(parseCoordinate(text), MAX_LATITUDE), t('coordinates.latitudeInvalid')),
      longitude: z
        .string()
        .refine((text) => isCoordinateValid(parseCoordinate(text), MAX_LONGITUDE), t('coordinates.longitudeInvalid')),
      notes: z.string().max(MAX_NOTES, t('grave.validation.notesTooLong')),
      changeNote: buildChangeNoteField(t),
    })
    .refine((values) => (parseCoordinate(values.latitude) == null) === (parseCoordinate(values.longitude) == null), {
      message: t('coordinates.bothOrNeither'),
      path: ['longitude'],
    })
}

/** What the grave form holds. */
export type GraveFormValues = z.infer<ReturnType<typeof buildGraveSchema>>
