package com.genealogy.member.dto.request;

import com.genealogy.common.model.Role;
import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An ADMIN renaming an account, changing its email or role, or disabling or enabling it.
 *
 * @param fullName display name
 * @param email the address the account signs in with, in any case
 * @param role access level
 * @param active whether the account may sign in
 * @param changeNote why the change was made, may be null
 */
public record UpdateMemberRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Role role,
        boolean active,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote) {
}
