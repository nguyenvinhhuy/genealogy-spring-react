package com.genealogy.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials presented at sign-in.
 *
 * @param email the account email
 * @param password the plaintext password
 */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
}
