/** Access level of an app account; mirrors the backend enum. */
export type Role = 'ADMIN' | 'EDITOR' | 'MEMBER'

/** An app account as returned by the backend. */
// Here and not in features/member: the session store and the API client are shared, and shared imports no feature.
export interface Member {
  id: number
  fullName: string
  email: string
  role: Role
  avatarUrl: string | null
  active: boolean
  createdAt: string
}

/** The backend's reply to a successful sign-in or refresh. */
export interface LoginResponse {
  accessToken: string
  member: Member
}
