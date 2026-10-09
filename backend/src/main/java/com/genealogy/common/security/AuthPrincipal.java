package com.genealogy.common.security;

import com.genealogy.common.model.Role;

/**
 * The authenticated caller, held as the Spring Security principal.
 *
 * @param id member id
 * @param email member email
 * @param role member role
 */
public record AuthPrincipal(Long id, String email, Role role) {
}
