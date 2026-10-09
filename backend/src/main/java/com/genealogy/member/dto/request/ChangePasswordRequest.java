package com.genealogy.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A member changing their own password.
 *
 * @param currentPassword the password they hold now
 * @param newPassword the password to set
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_BYTES) String newPassword) {
}
