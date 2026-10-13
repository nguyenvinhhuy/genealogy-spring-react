import type { TFunction } from 'i18next'
import { z } from 'zod'

import type { GenealogyDate } from '@/features/event/types'
import { buildGenderField, MAX_GIVEN_NAME, MAX_MIDDLE_NAME, MAX_SURNAME } from '@/features/person/lib/person-schema'

const dateField = z.custom<Partial<GenealogyDate> | null>()

/**
 * Builds the add-relation schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildRelationSchema(t: TFunction) {
  return z
    .object({
      kind: z.enum(['SPOUSE', 'CHILD']),
      // A union's id, "new" for a new one-parent union, or empty while a parent with several has not chosen.
      familyId: z.string(),
      gender: buildGenderField(t),
      // Họ, tên and giới tính are the three a member typing someone in must give (§6.1).
      surname: z
        .string()
        .trim()
        .min(1, t('person.validation.surnameRequired'))
        .max(MAX_SURNAME, t('common.tooLong', { max: MAX_SURNAME })),
      middleName: z.string().max(MAX_MIDDLE_NAME, t('common.tooLong', { max: MAX_MIDDLE_NAME })),
      givenName: z
        .string()
        .trim()
        .min(1, t('person.validation.givenNameRequired'))
        .max(MAX_GIVEN_NAME, t('common.tooLong', { max: MAX_GIVEN_NAME })),
      // Optional, so a cụ entered from the tree can be marked deceased and is not redacted as living (§8.12 #23).
      birth: dateField,
      death: dateField,
      deceased: z.boolean(),
    })
    .refine((values) => values.kind !== 'CHILD' || values.familyId !== '', {
      message: t('family.pickUnionRequired'),
      path: ['familyId'],
    })
}

/** What the add-relation form holds. */
export type RelationFormValues = z.infer<ReturnType<typeof buildRelationSchema>>
