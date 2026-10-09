import type { TFunction } from 'i18next'
import { describe, expect, it } from 'vitest'

import { buildChangePasswordSchema, buildCreateMemberSchema, buildResetPasswordSchema } from './schemas'

// Returns the key itself, so a test can assert which message a rule chose.
const t = ((key: string) => key) as unknown as TFunction

describe('buildChangePasswordSchema', () => {
  const schema = buildChangePasswordSchema(t)
  const current = 'Cu@12345'

  it('accepts a different, repeated new password', () => {
    const fresh = 'Moi@12345'
    const result = schema.safeParse({ currentPassword: current, newPassword: fresh, confirmPassword: fresh })
    expect(result.success).toBe(true)
  })

  it('refuses a mismatched repeat on the repeat field', () => {
    const result = schema.safeParse({ currentPassword: current, newPassword: 'Moi@12345', confirmPassword: 'Moi@1234' })
    expect(result.error?.issues).toContainEqual(
      expect.objectContaining({ path: ['confirmPassword'], message: 'member.validation.mismatch' }),
    )
  })

  it('refuses the current password again', () => {
    const result = schema.safeParse({ currentPassword: current, newPassword: current, confirmPassword: current })
    expect(result.error?.issues).toContainEqual(
      expect.objectContaining({ path: ['newPassword'], message: 'member.validation.sameAsCurrent' }),
    )
  })
})

describe('buildResetPasswordSchema', () => {
  const schema = buildResetPasswordSchema(t)

  it('counts bytes, not characters, as BCrypt does', () => {
    // 30 letters is well under 72 characters and 90 bytes, which the server refuses.
    const accented = 'ặ'.repeat(30)
    const result = schema.safeParse({ newPassword: accented, confirmPassword: accented })
    expect(result.error?.issues[0]?.message).toBe('member.validation.tooLong')
  })

  it('accepts exactly 72 bytes', () => {
    const ascii = 'a'.repeat(72)
    expect(schema.safeParse({ newPassword: ascii, confirmPassword: ascii }).success).toBe(true)
  })

  it('refuses fewer than 8 characters', () => {
    const result = schema.safeParse({ newPassword: 'short', confirmPassword: 'short' })
    expect(result.error?.issues[0]?.message).toBe('member.validation.tooShort')
  })
})

describe('buildCreateMemberSchema', () => {
  const schema = buildCreateMemberSchema(t)
  const password = 'Matkhau@1'

  it('sends the email trimmed and lower-cased, as the server stores it', () => {
    const result = schema.safeParse({
      fullName: ' Nguyễn Văn An ',
      email: '  An.Nguyen@Example.VN ',
      role: 'MEMBER',
      newPassword: password,
      confirmPassword: password,
    })
    expect(result.data).toMatchObject({ fullName: 'Nguyễn Văn An', email: 'an.nguyen@example.vn' })
  })

  it('refuses a malformed email with its own message', () => {
    const result = schema.safeParse({
      fullName: 'An',
      email: 'not-an-email',
      role: 'MEMBER',
      newPassword: password,
      confirmPassword: password,
    })
    expect(result.error?.issues).toContainEqual(
      expect.objectContaining({ path: ['email'], message: 'member.validation.emailInvalid' }),
    )
  })
})
