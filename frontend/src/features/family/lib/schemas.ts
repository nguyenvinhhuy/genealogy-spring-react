import type { TFunction } from 'i18next'
import { z } from 'zod'

import { buildChangeNoteField } from '@/shared/lib/change-note'

export const FAMILY_STATUSES = ['MARRIED', 'DIVORCED', 'PARTNER', 'UNKNOWN'] as const
export const RELATION_TYPES = ['BIRTH', 'ADOPTED', 'STEP', 'FOSTER'] as const

/**
 * Builds the union edit schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildUnionSchema(t: TFunction) {
  return z.object({
    status: z.enum(FAMILY_STATUSES),
    orderIndex: z.number(t('family.validation.orderNumber')).int().min(0, t('family.validation.orderNumber')),
    changeNote: buildChangeNoteField(t),
  })
}

/** What the union form holds. */
export type UnionFormValues = z.infer<ReturnType<typeof buildUnionSchema>>

/**
 * Builds the child-link edit schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildChildLinkSchema(t: TFunction) {
  return z.object({
    relationToP1: z.enum(RELATION_TYPES),
    relationToP2: z.enum(RELATION_TYPES),
    // Left empty when unrecorded: an invented order makes the kinship answer "chú" with false confidence (§5.3).
    birthOrder: z.number(t('family.validation.birthOrder')).int().min(1, t('family.validation.birthOrder')).nullable(),
    changeNote: buildChangeNoteField(t),
  })
}

/** What the child-link form holds. */
export type ChildLinkFormValues = z.infer<ReturnType<typeof buildChildLinkSchema>>
