import type { TFunction } from 'i18next'
import { z } from 'zod'

// Matches the server's MediaUpdateRequest.MAX_CAPTION, so a long caption is refused here, in words.
const MAX_CAPTION = 1000

// Matches the server's @Size on every change note.
const MAX_CHANGE_NOTE = 2000

export const MEDIA_KINDS = ['PHOTO', 'SCAN', 'DOCUMENT', 'PORTRAIT'] as const

/**
 * Builds the upload schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildUploadSchema(t: TFunction) {
  return z.object({
    kind: z.enum(MEDIA_KINDS),
    caption: z.string().max(MAX_CAPTION, t('media.validation.tooLong', { max: MAX_CAPTION })),
  })
}

/** What the upload form holds. */
export type UploadFormValues = z.infer<ReturnType<typeof buildUploadSchema>>

/**
 * Builds the edit schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildMediaEditSchema(t: TFunction) {
  return z.object({
    kind: z.enum(MEDIA_KINDS),
    caption: z.string().max(MAX_CAPTION, t('media.validation.tooLong', { max: MAX_CAPTION })),
    // A text field, because an empty box must read as "keep the position", not as position 0.
    sortOrder: z.string().regex(/^\d*$/, t('media.validation.sortOrder')),
    changeNote: z.string().max(MAX_CHANGE_NOTE, t('media.validation.tooLong', { max: MAX_CHANGE_NOTE })),
  })
}

/** What the edit-a-file form holds. */
export type MediaEditFormValues = z.infer<ReturnType<typeof buildMediaEditSchema>>
