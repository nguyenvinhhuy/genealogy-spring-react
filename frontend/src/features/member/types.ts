import type { Role } from '@/shared/types/session'

export type { Member, Role } from '@/shared/types/session'

/** A member changing their own password. */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** The clan head setting a password for someone who cannot sign in. */
export interface ResetPasswordRequest {
  newPassword: string
}

/** The clan head opening an account for someone. */
export interface CreateMemberRequest {
  fullName: string
  email: string
  password: string
  role: Role
}

/** The clan head renaming an account, changing its role, or disabling it. */
export interface UpdateMemberRequest {
  fullName: string
  role: Role
  active: boolean
  changeNote: string | null
}
