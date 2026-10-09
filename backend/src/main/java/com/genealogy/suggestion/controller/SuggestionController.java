package com.genealogy.suggestion.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.suggestion.domain.SuggestionStatus;
import com.genealogy.suggestion.dto.request.SuggestionRequest;
import com.genealogy.suggestion.dto.request.SuggestionReviewRequest;
import com.genealogy.suggestion.dto.response.SuggestionPreview;
import com.genealogy.suggestion.dto.response.SuggestionResponse;
import com.genealogy.suggestion.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the edit-suggestion queue. */
@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    /**
     * Lists suggestions newest first, optionally by state, only the caller's own below EDITOR.
     *
     * @param status the state to filter by, or null for all of them
     * @param principal the authenticated caller
     * @param pageable which page; its size is capped and its sort ignored
     * @return the matching page
     */
    @GetMapping
    @Operation(summary = "List edit suggestions")
    public PagedModel<SuggestionResponse> search(
            @RequestParam(required = false) SuggestionStatus status,
            @AuthenticationPrincipal AuthPrincipal principal,
            Pageable pageable) {
        return new PagedModel<>(
                suggestionService.search(status, principal.role(), principal.id(), pageable));
    }

    /**
     * Counts the suggestions still waiting for a reviewer, only the caller's own below EDITOR.
     *
     * @param principal the authenticated caller
     * @return how many are pending
     */
    @GetMapping("/pending-count")
    @Operation(summary = "Count suggestions awaiting review")
    public long countPending(@AuthenticationPrincipal AuthPrincipal principal) {
        return suggestionService.countPending(principal.role(), principal.id());
    }

    /**
     * Returns one suggestion; kept as API surface for a link to one suggestion, though no screen calls it yet.
     *
     * @param id suggestion id
     * @param principal the authenticated caller
     * @return the suggestion
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one suggestion")
    public SuggestionResponse getById(
            @PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return suggestionService.getById(id, principal.role(), principal.id());
    }

    /**
     * Shows what approving a suggestion would change, field by field.
     *
     * @param id suggestion id
     * @param principal the authenticated caller
     * @return the person as they are and as approval would leave them
     */
    @GetMapping("/{id}/preview")
    @Operation(summary = "Preview what approving a suggestion would change")
    public SuggestionPreview preview(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return suggestionService.preview(id, principal.role());
    }

    /**
     * Records a suggestion offered by a member.
     *
     * @param request what is being suggested
     * @param principal the member offering it
     * @return the recorded suggestion
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Offer an edit suggestion")
    public SuggestionResponse create(
            @Valid @RequestBody SuggestionRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return suggestionService.create(request, principal.id());
    }

    /**
     * Approves or rejects a suggestion.
     *
     * @param id suggestion id
     * @param request the decision and its reason
     * @param principal the member deciding
     * @return the reviewed suggestion
     */
    @PostMapping("/{id}/review")
    @Operation(summary = "Approve or reject a suggestion")
    public SuggestionResponse review(
            @PathVariable Long id,
            @Valid @RequestBody SuggestionReviewRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return suggestionService.review(id, request, principal.role(), principal.id());
    }
}
