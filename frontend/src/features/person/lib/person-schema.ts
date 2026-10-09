import type { TFunction } from 'i18next'
import { z } from 'zod'

import { buildChangeNoteField } from '@/shared/lib/change-note'

// The server's PersonNameRequest limits, so a name stops where the column would refuse it.
export const MAX_SURNAME = 50
export const MAX_MIDDLE_NAME = 100
export const MAX_GIVEN_NAME = 50

export const NAME_TYPES = ['BIRTH', 'HUY', 'TU', 'HIEU', 'THUY', 'SAINT', 'ALIAS'] as const
export const GENDERS = ['MALE', 'FEMALE', 'UNKNOWN'] as const

/**
 * Builds the person form schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildPersonSchema(t: TFunction) {
  return z
    .object({
      gender: z.enum(GENDERS),
      branchId: z.string(),
      notes: z.string(),
      names: z.array(
        z.object({
          type: z.enum(NAME_TYPES),
          surname: z.string().max(MAX_SURNAME, t('common.tooLong', { max: MAX_SURNAME })),
          middleName: z.string().max(MAX_MIDDLE_NAME, t('common.tooLong', { max: MAX_MIDDLE_NAME })),
          givenName: z.string().max(MAX_GIVEN_NAME, t('common.tooLong', { max: MAX_GIVEN_NAME })),
          primary: z.boolean(),
        }),
      ),
      changeNote: buildChangeNoteField(t),
    })
    // Every field but one given name is optional: a person with only a name is a valid save (§6.1).
    .refine((values) => values.names.some((name) => name.givenName.trim()), {
      message: t('person.validation.givenNameRequired'),
      path: ['names'],
    })
}

/** What the person form holds. */
export type PersonFormValues = z.infer<ReturnType<typeof buildPersonSchema>>
