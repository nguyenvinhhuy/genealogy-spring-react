import type { TFunction } from 'i18next'
import { z } from 'zod'

// The server's ChangeNotes.MAX_LENGTH, so a reason stops where the server would refuse it.
export const MAX_CHANGE_NOTE = 2000

/**
 * Builds the field every edit form uses for the reason behind a change.
 *
 * @param t the translation function
 * @returns the field schema
 */
export function buildChangeNoteField(t: TFunction) {
  return z.string().max(MAX_CHANGE_NOTE, t('common.changeNoteTooLong', { max: MAX_CHANGE_NOTE }))
}
