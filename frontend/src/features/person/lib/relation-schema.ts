import type { TFunction } from 'i18next'
import { z } from 'zod'

import type { GenealogyDate } from '@/features/event/types'
import { MAX_GIVEN_NAME, MAX_MIDDLE_NAME, MAX_SURNAME } from '@/features/person/lib/person-schema'

const dateField = z.custom<Partial<GenealogyDate> | null>()

/**
 * Builds the add-relation schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildRelationSchema(t: TFunction) {
  return z.object({
    kind: z.enum(['SPOUSE', 'CHILD']),
    // Empty for "a new one-parent union"; the id of an existing union otherwise.
    familyId: z.string(),
    gender: z.enum(['MALE', 'FEMALE', 'UNKNOWN']),
    surname: z.string().max(MAX_SURNAME, t('common.tooLong', { max: MAX_SURNAME })),
    middleName: z.string().max(MAX_MIDDLE_NAME, t('common.tooLong', { max: MAX_MIDDLE_NAME })),
    // The only required field: a person with nothing but a name is a valid save (§6.1).
    givenName: z
      .string()
      .trim()
      .min(1, t('person.validation.givenNameRequired'))
      .max(MAX_GIVEN_NAME, t('common.tooLong', { max: MAX_GIVEN_NAME })),
    // Optional, so a cụ entered from the tree can be marked deceased at once and is not redacted as living (§8.12 #23).
    birth: dateField,
    death: dateField,
    deceased: z.boolean(),
  })
}

/** What the add-relation form holds. */
export type RelationFormValues = z.infer<ReturnType<typeof buildRelationSchema>>
