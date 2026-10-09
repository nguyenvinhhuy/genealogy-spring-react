package com.genealogy.member.service;

import com.genealogy.common.model.Role;
import com.genealogy.member.dto.request.CreateMemberRequest;
import com.genealogy.member.dto.request.UpdateMemberRequest;
import com.genealogy.member.dto.response.MemberResponse;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Application-account operations. */
public interface MemberService {

    /**
     * Returns the account with the given id.
     *
     * @param id member id
     * @return the member
     */
    MemberResponse getById(Long id);

    /**
     * Creates an app account with a hashed password, refusing callers below ADMIN.
     *
     * @param actorId the ADMIN creating it
     * @param callerRole the calling member's access level
     * @param request the account to create
     * @return the created member
     */
    MemberResponse create(Long actorId, Role callerRole, CreateMemberRequest request);

    /**
     * Renames an account, changes its role or disables it, refusing callers below ADMIN.
     *
     * @param actorId the ADMIN making the change
     * @param callerRole the calling member's access level
     * @param memberId the account to change
     * @param request the account's new state
     * @return the account as it is now
     */
    MemberResponse update(Long actorId, Role callerRole, Long memberId, UpdateMemberRequest request);

    /**
     * Checks a presented email and password, without letting the stored hash leave this feature.
     *
     * @param email the email presented, in any case
     * @param rawPassword the password presented
     * @return the account when the credentials are good and it is active, empty otherwise
     */
    Optional<MemberResponse> verifyCredentials(String email, String rawPassword);

    /**
     * Returns an account only while it is still active, for a caller re-checking a live session.
     *
     * @param id member id
     * @return the member, or empty when unknown or disabled
     */
    Optional<MemberResponse> findActive(Long id);

    /**
     * Makes every access token issued to an account stop working at once.
     *
     * @param memberId the account whose tokens end
     */
    void endAccessTokens(Long memberId);

    /**
     * Replaces a member's own password, after checking the one they currently hold, and ends their sessions.
     *
     * @param memberId the account changing its password
     * @param currentPassword the password they hold now
     * @param newPassword the password to set
     */
    void changeOwnPassword(Long memberId, String currentPassword, String newPassword);

    /**
     * Sets another member's password without knowing the old one, refusing callers below ADMIN.
     *
     * @param actorId the ADMIN doing the reset
     * @param callerRole the calling member's access level
     * @param memberId the account whose password is being reset
     * @param newPassword the password to set
     */
    void resetPassword(Long actorId, Role callerRole, Long memberId, String newPassword);

    /**
     * Lists every account by name, for an ADMIN managing who may sign in.
     *
     * @param pageable the page to return
     * @param callerRole the calling member's access level
     * @return one page of accounts
     */
    Page<MemberResponse> search(Pageable pageable, Role callerRole);
}
