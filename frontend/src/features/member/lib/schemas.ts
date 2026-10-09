import type { TFunction } from 'i18next'
import { z } from 'zod'

import { buildChangeNoteField } from '@/shared/lib/change-note'

// The server's limits: @Size(min = 8) on the request, and the 72 bytes BCrypt can read.
export const MIN_PASSWORD_LENGTH = 8
export const MAX_PASSWORD_BYTES = 72

const encoder = new TextEncoder()

/**
 * Builds the new-password field shared by both forms, measured in UTF-8 bytes as the server measures it.
 *
 * @param t the translation function
 * @returns the field schema
 */
function buildNewPasswordField(t: TFunction) {
  return z
    .string()
    .min(MIN_PASSWORD_LENGTH, t('member.validation.tooShort', { min: MIN_PASSWORD_LENGTH }))
    .refine(
      (value) => encoder.encode(value).length <= MAX_PASSWORD_BYTES,
      t('member.validation.tooLong', { max: MAX_PASSWORD_BYTES }),
    )
}

/**
 * Builds the change-own-password schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildChangePasswordSchema(t: TFunction) {
  return z
    .object({
      currentPassword: z.string().min(1, t('member.validation.currentRequired')),
      newPassword: buildNewPasswordField(t),
      confirmPassword: z.string(),
    })
    .refine((values) => values.newPassword === values.confirmPassword, {
      message: t('member.validation.mismatch'),
      path: ['confirmPassword'],
    })
    .refine((values) => values.newPassword !== values.currentPassword, {
      message: t('member.validation.sameAsCurrent'),
      path: ['newPassword'],
    })
}

/** What the change-own-password form holds. */
export type ChangePasswordValues = z.infer<ReturnType<typeof buildChangePasswordSchema>>

/**
 * Builds the reset-someone-else's-password schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildResetPasswordSchema(t: TFunction) {
  return z
    .object({
      newPassword: buildNewPasswordField(t),
      confirmPassword: z.string(),
    })
    .refine((values) => values.newPassword === values.confirmPassword, {
      message: t('member.validation.mismatch'),
      path: ['confirmPassword'],
    })
}

/** What the reset-someone-else's-password form holds. */
export type ResetPasswordValues = z.infer<ReturnType<typeof buildResetPasswordSchema>>

// The roles an account can hold, in the order the picker lists them.
export const ROLES = ['MEMBER', 'EDITOR', 'ADMIN'] as const

// The server's column widths: members.full_name and members.email.
const MAX_NAME_LENGTH = 100
const MAX_EMAIL_LENGTH = 255

/**
 * Builds the account-creation schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildCreateMemberSchema(t: TFunction) {
  return z
    .object({
      fullName: z
        .string()
        .trim()
        .min(1, t('member.validation.nameRequired'))
        .max(MAX_NAME_LENGTH, t('common.tooLong', { max: MAX_NAME_LENGTH })),
      // Trimmed and lower-cased here as the server stores it, so "An@x.vn " is not refused as malformed.
      email: z
        .string()
        .trim()
        .toLowerCase()
        .max(MAX_EMAIL_LENGTH, t('common.tooLong', { max: MAX_EMAIL_LENGTH }))
        .pipe(z.email(t('member.validation.emailInvalid'))),
      role: z.enum(ROLES),
      newPassword: buildNewPasswordField(t),
      confirmPassword: z.string(),
    })
    .refine((values) => values.newPassword === values.confirmPassword, {
      message: t('member.validation.mismatch'),
      path: ['confirmPassword'],
    })
}

/** What the open-an-account form holds. */
export type CreateMemberValues = z.infer<ReturnType<typeof buildCreateMemberSchema>>

/**
 * Builds the account-edit schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildUpdateMemberSchema(t: TFunction) {
  return z.object({
    fullName: z
      .string()
      .trim()
      .min(1, t('member.validation.nameRequired'))
      .max(MAX_NAME_LENGTH, t('common.tooLong', { max: MAX_NAME_LENGTH })),
    role: z.enum(ROLES),
    active: z.boolean(),
    changeNote: buildChangeNoteField(t),
  })
}

/** What the edit-an-account form holds. */
export type UpdateMemberValues = z.infer<ReturnType<typeof buildUpdateMemberSchema>>
