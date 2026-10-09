import type { TFunction } from 'i18next'
import { z } from 'zod'

import { PLACE_TYPES } from '@/features/place/types'
import { buildChangeNoteField } from '@/shared/lib/change-note'
import { isCoordinateValid, MAX_LATITUDE, MAX_LONGITUDE, parseCoordinate } from '@/shared/lib/coordinates'

// Matches PlaceRequest.MAX_NAME on the server.
const MAX_NAME = 200

/**
 * Builds the place create/edit schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildPlaceSchema(t: TFunction) {
  return z
    .object({
      name: z
        .string()
        .trim()
        .min(1, t('place.validation.nameRequired'))
        .max(MAX_NAME, t('place.validation.nameTooLong')),
      type: z.enum(PLACE_TYPES),
      parentId: z.number().nullable(),
      latitude: z
        .string()
        .refine((text) => isCoordinateValid(parseCoordinate(text), MAX_LATITUDE), t('coordinates.latitudeInvalid')),
      longitude: z
        .string()
        .refine((text) => isCoordinateValid(parseCoordinate(text), MAX_LONGITUDE), t('coordinates.longitudeInvalid')),
      changeNote: buildChangeNoteField(t),
    })
    .refine((values) => (parseCoordinate(values.latitude) == null) === (parseCoordinate(values.longitude) == null), {
      message: t('coordinates.bothOrNeither'),
      path: ['longitude'],
    })
}

/** What the place form holds. */
export type PlaceFormValues = z.infer<ReturnType<typeof buildPlaceSchema>>
