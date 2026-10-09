package com.genealogy.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An ADMIN setting a password for someone who cannot sign in.
 *
 * @param newPassword the password to set
 */
public record ResetPasswordRequest(
        @NotBlank @Size(min = Passwords.MIN_LENGTH, max = Passwords.MAX_BYTES) String newPassword) {
}
