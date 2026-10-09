package com.genealogy.member.dto.request;

import com.genealogy.common.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating an app account.
 *
 * @param fullName display name
 * @param email email address, unique across accounts whatever its case
 * @param password plaintext password, hashed before storage
 * @param role access level to grant
 */
public record CreateMemberRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_BYTES) String password,
        @NotNull Role role) {
}
