import type { TFunction } from 'i18next'
import { z } from 'zod'

import type { GenealogyDate } from '@/features/event/types'

// Matches the server's SuggestionRequest.MAX_MESSAGE, so a long story is refused here, in words.
const MAX_MESSAGE = 4000

// Matches the server's SuggestionReviewRequest.
const MAX_REVIEW_NOTE = 2000

// The gender select's value for "leave it as recorded", since Radix Select cannot hold an empty string.
export const KEEP_GENDER = 'KEEP'

// The union select's value for "a new one-parent union".
export const NEW_UNION = 'new'

// The union select's value before anyone chose: the person's first union, or a new one when they have none yet.
export const FIRST_UNION = ''

const dateField = z.custom<Partial<GenealogyDate> | null>()

/**
 * Builds the suggestion form schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildSuggestionSchema(t: TFunction) {
  return z
    .object({
      kind: z.enum(['NOTE', 'UPDATE', 'CREATE']),
      message: z
        .string()
        .trim()
        .min(1, t('suggestion.validation.messageRequired'))
        .max(MAX_MESSAGE, t('suggestion.validation.tooLong', { max: MAX_MESSAGE })),
      relation: z.enum(['CHILD', 'SPOUSE']),
      familyId: z.string(),
      gender: z.enum(['MALE', 'FEMALE', 'UNKNOWN', KEEP_GENDER]),
      surname: z.string().max(50, t('suggestion.validation.tooLong', { max: 50 })),
      middleName: z.string().max(100, t('suggestion.validation.tooLong', { max: 100 })),
      givenName: z.string().max(50, t('suggestion.validation.tooLong', { max: 50 })),
      birth: dateField,
      death: dateField,
    })
    .superRefine((values, context) => {
      // The server refuses both shapes too; saying so here saves a round trip and a lost message.
      if (values.kind === 'CREATE' && !values.givenName.trim()) {
        context.addIssue({ code: 'custom', path: ['givenName'], message: t('person.validation.givenNameRequired') })
      }
      const nothing = !values.givenName.trim() && values.gender === KEEP_GENDER && !values.birth && !values.death
      if (values.kind === 'UPDATE' && nothing) {
        context.addIssue({ code: 'custom', path: ['givenName'], message: t('suggestion.validation.nothingProposed') })
      }
    })
}

/** What the suggestion form holds. */
export type SuggestionFormValues = z.infer<ReturnType<typeof buildSuggestionSchema>>

/**
 * Builds the review form schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildReviewSchema(t: TFunction) {
  return z.object({
    reviewNote: z.string().max(MAX_REVIEW_NOTE, t('suggestion.validation.tooLong', { max: MAX_REVIEW_NOTE })),
  })
}

/** What the review form holds. */
export type ReviewFormValues = z.infer<ReturnType<typeof buildReviewSchema>>
