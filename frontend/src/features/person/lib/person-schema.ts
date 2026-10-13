import type { TFunction } from 'i18next'
import { z } from 'zod'

import type { Gender } from '@/features/person/types'
import { buildChangeNoteField } from '@/shared/lib/change-note'

// The server's PersonNameRequest limits, so a name stops where the column would refuse it.
export const MAX_SURNAME = 50
export const MAX_MIDDLE_NAME = 100
export const MAX_GIVEN_NAME = 50

export const NAME_TYPES = ['BIRTH', 'HUY', 'TU', 'HIEU', 'THUY', 'SAINT', 'ALIAS'] as const
export const GENDERS = ['MALE', 'FEMALE', 'UNKNOWN'] as const

// The sex select's value before anyone chose, so "Chưa rõ" is a choice and not something nobody looked at.
export const NO_GENDER = ''

/**
 * Builds the sex field of a form that types a person in by hand: one of the three, never left unchosen.
 *
 * @param t the translation function
 * @returns the field schema
 */
export function buildGenderField(t: TFunction) {
  return z
    .enum([...GENDERS, NO_GENDER])
    // Typed boolean, not a predicate, or zod narrows the form's type and an unchosen default no longer fits it.
    .refine((value): boolean => value !== NO_GENDER, t('person.validation.genderRequired'))
}

/**
 * Narrows a validated sex field to the value sent to the server.
 *
 * @param gender the field's value, which validation has already refused when unchosen
 * @returns the sex
 */
export function chosenGender(gender: Gender | typeof NO_GENDER): Gender {
  return gender === NO_GENDER ? 'UNKNOWN' : gender
}

/**
 * Builds the person form schema with translated messages.
 *
 * @param t the translation function
 * @param creating whether a new person is being typed in, who must have a họ
 * @returns the schema
 */
export function buildPersonSchema(t: TFunction, creating: boolean) {
  return z
    .object({
      gender: buildGenderField(t),
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
    .superRefine((values, context) => {
      const primary = values.names.find((name) => name.primary) ?? values.names.at(0)
      if (!primary) {
        return
      }
      // Each message under the field it is about, on the primary row, where the other two required fields are.
      const index = values.names.indexOf(primary)
      if (!values.names.some((name) => name.givenName.trim())) {
        context.addIssue({
          code: 'custom',
          path: ['names', index, 'givenName'],
          message: t('person.validation.givenNameRequired'),
        })
      }
      // Only on create: an edit of someone recorded without a họ is not refused, the quality page lists them (§6.1).
      if (creating && !primary.surname.trim()) {
        context.addIssue({
          code: 'custom',
          path: ['names', index, 'surname'],
          message: t('person.validation.surnameRequired'),
        })
      }
    })
}

/** What the person form holds. */
export type PersonFormValues = z.infer<ReturnType<typeof buildPersonSchema>>
