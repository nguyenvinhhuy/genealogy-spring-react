import type { TFunction } from 'i18next'
import { z } from 'zod'

import { buildChangeNoteField } from '@/shared/lib/change-note'

/**
 * Builds the branch create/edit schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildBranchSchema(t: TFunction) {
  return z.object({
    name: z.string().trim().min(1, t('branch.validation.nameRequired')).max(100, t('branch.validation.nameTooLong')),
    parentId: z.string(),
    description: z.string().max(2000, t('branch.validation.descriptionTooLong')),
    changeNote: buildChangeNoteField(t),
  })
}

/** What the chi form holds. */
export type BranchFormValues = z.infer<ReturnType<typeof buildBranchSchema>>
