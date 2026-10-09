export type { LoginResponse } from '@/shared/types/session'

/** Credentials posted at sign-in. */
export interface LoginRequest {
  email: string
  password: string
}
