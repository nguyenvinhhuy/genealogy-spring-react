package com.genealogy.common.security;

import com.genealogy.common.model.Role;

/**
 * What the request filter needs to know about an account right now, read from the database.
 *
 * @param id the member id
 * @param email the member's email
 * @param role the member's role as it is now, not as the token remembers it
 * @param active whether the account may sign in at all
 * @param credentialVersion bumped on every password change or reset, so older tokens stop working
 */
public record MemberAccess(Long id, String email, Role role, boolean active, int credentialVersion) {
}
