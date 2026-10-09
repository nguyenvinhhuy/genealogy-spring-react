import type { TFunction } from 'i18next'
import { z } from 'zod'

import { SOURCE_TYPES } from '@/features/source/types'
import { buildChangeNoteField } from '@/shared/lib/change-note'

// Each limit matches the server's SourceRequest / CitationRequest, so a long field is refused here, in words.
const MAX_TITLE = 300
const MAX_AUTHOR = 200
const MAX_DATE_TEXT = 100
const MAX_REPOSITORY = 300
const MAX_NOTES = 10000
const MAX_LOCATOR = 200
const MAX_QUOTE = 10000

// The source picker's value for "a source not recorded yet", typed in below it.
export const NEW_SOURCE = '__new__'

/**
 * Builds the source create/edit schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildSourceSchema(t: TFunction) {
  const tooLong = (max: number) => t('source.validation.tooLong', { max })
  return z.object({
    title: z.string().trim().min(1, t('source.validation.titleRequired')).max(MAX_TITLE, tooLong(MAX_TITLE)),
    type: z.enum(SOURCE_TYPES),
    author: z.string().max(MAX_AUTHOR, tooLong(MAX_AUTHOR)),
    dateText: z.string().max(MAX_DATE_TEXT, tooLong(MAX_DATE_TEXT)),
    repository: z.string().max(MAX_REPOSITORY, tooLong(MAX_REPOSITORY)),
    notes: z.string().max(MAX_NOTES, tooLong(MAX_NOTES)),
    changeNote: buildChangeNoteField(t),
  })
}

/** What the source form holds. */
export type SourceFormValues = z.infer<ReturnType<typeof buildSourceSchema>>

/**
 * Builds the citation add/edit schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildCitationSchema(t: TFunction) {
  const tooLong = (max: number) => t('source.validation.tooLong', { max })
  return z
    .object({
      // An existing source's id as a string, NEW_SOURCE, or empty before anything is picked.
      source: z.string(),
      newTitle: z.string().max(MAX_TITLE, tooLong(MAX_TITLE)),
      newType: z.enum(SOURCE_TYPES),
      locator: z.string().max(MAX_LOCATOR, tooLong(MAX_LOCATOR)),
      quote: z.string().max(MAX_QUOTE, tooLong(MAX_QUOTE)),
      changeNote: buildChangeNoteField(t),
    })
    .refine((values) => values.source !== '' && (values.source !== NEW_SOURCE || values.newTitle.trim() !== ''), {
      message: t('source.validation.sourceRequired'),
      path: ['source'],
    })
}

/** What the citation form holds. */
export type CitationFormValues = z.infer<ReturnType<typeof buildCitationSchema>>
