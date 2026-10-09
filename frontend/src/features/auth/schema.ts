import type { TFunction } from 'i18next'
import { z } from 'zod'

/**
 * Builds the sign-in schema with translated messages.
 *
 * @param t the translation function
 * @returns the schema
 */
export function buildSignInSchema(t: TFunction) {
  return z.object({
    email: z
      .string()
      .min(1, t('auth.validation.emailRequired'))
      .pipe(z.email(t('auth.validation.emailInvalid'))),
    password: z.string().min(1, t('auth.validation.passwordRequired')),
  })
}

/** What the sign-in form holds. */
export type SignInValues = z.infer<ReturnType<typeof buildSignInSchema>>
