package com.genealogy.member.dto.response;

import com.genealogy.common.model.Role;
import java.time.Instant;

/**
 * An app account as returned to clients.
 *
 * @param id member id
 * @param fullName display name
 * @param email email address
 * @param role access level
 * @param avatarUrl avatar image URL, or null
 * @param active whether the account may sign in
 * @param createdAt when the account was created
 */
public record MemberResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        String avatarUrl,
        boolean active,
        Instant createdAt) {
}
