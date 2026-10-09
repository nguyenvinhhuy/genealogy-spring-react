package com.genealogy.member.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.member.dto.request.ChangePasswordRequest;
import com.genealogy.member.dto.request.CreateMemberRequest;
import com.genealogy.member.dto.request.ResetPasswordRequest;
import com.genealogy.member.dto.request.UpdateMemberRequest;
import com.genealogy.member.dto.response.MemberResponse;
import com.genealogy.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for app accounts. */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * Returns the signed-in caller's own account.
     *
     * @param principal the authenticated caller
     * @return the caller's member record
     */
    @GetMapping("/me")
    @Operation(summary = "Get the signed-in member")
    public MemberResponse getCurrentMember(@AuthenticationPrincipal AuthPrincipal principal) {
        return memberService.getById(principal.id());
    }

    /**
     * Lists every account by name.
     *
     * @param pageable the page to return
     * @param principal the authenticated caller
     * @return one page of accounts
     */
    @GetMapping
    @Operation(summary = "List member accounts")
    public PagedModel<MemberResponse> search(Pageable pageable, @AuthenticationPrincipal AuthPrincipal principal) {
        return new PagedModel<>(memberService.search(pageable, principal.role()));
    }

    /**
     * Creates an app account.
     *
     * @param request the account to create
     * @param principal the authenticated caller
     * @return the created member
     */
    @PostMapping
    @Operation(summary = "Create a member account")
    public ResponseEntity<MemberResponse> create(
            @Valid @RequestBody CreateMemberRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        MemberResponse created = memberService.create(principal.id(), principal.role(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Renames an account, changes its role, or disables or enables it.
     *
     * @param id the account to change
     * @param request the account's new state
     * @param principal the authenticated caller
     * @return the account as it is now
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a member account")
    public MemberResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMemberRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return memberService.update(principal.id(), principal.role(), id, request);
    }

    /**
     * Changes the caller's own password and ends their sessions.
     *
     * @param request the current and new passwords
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @PostMapping("/me/password")
    @Operation(summary = "Change your own password")
    public ResponseEntity<Void> changeOwnPassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        memberService.changeOwnPassword(principal.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets another member's password, for an ADMIN helping someone who cannot sign in.
     *
     * @param id the account whose password is being reset
     * @param request the password to set
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @PostMapping("/{id}/password")
    @Operation(summary = "Reset a member's password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        memberService.resetPassword(principal.id(), principal.role(), id, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
