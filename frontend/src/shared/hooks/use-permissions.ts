import { useAuthStore } from '@/shared/store/auth-store'

/** What the signed-in member is allowed to do, mirroring the rules in the backend's SecurityConfig. */
export interface Permissions {
  // May add and edit records — EDITOR and ADMIN.
  mayEdit: boolean
  // May delete records — ADMIN only.
  mayDelete: boolean
  // May read the audit trail, the quality page, and the whole-clan exports — EDITOR and ADMIN.
  mayAudit: boolean
  // May approve or reject a suggestion — EDITOR and ADMIN.
  mayReview: boolean
  // May import a GEDCOM file into the gia phả — ADMIN only.
  mayImport: boolean
  // May list every account and reset someone else's password — ADMIN only.
  mayManageMembers: boolean
}

/**
 * Reports what the signed-in member may do.
 *
 * @returns the permission flags
 */
export function usePermissions(): Permissions {
  // Only hides affordances that would 403 anyway; SecurityConfig decides, and this mirrors it in one place.
  const role = useAuthStore((state) => state.member?.role)
  const editing = role === 'ADMIN' || role === 'EDITOR'
  return {
    mayEdit: editing,
    mayDelete: role === 'ADMIN',
    mayAudit: editing,
    mayReview: editing,
    mayImport: role === 'ADMIN',
    mayManageMembers: role === 'ADMIN',
  }
}
