package com.genealogy.merge.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.merge.dto.request.MergeRequest;
import com.genealogy.merge.dto.response.MergeResponse;
import com.genealogy.merge.service.MergeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for folding a duplicate person into the one being kept. */
@RestController
@RequestMapping("/api/v1/merges")
@RequiredArgsConstructor
public class MergeController {

    private final MergeService mergeService;

    /**
     * Merges one person into another; restricted to ADMIN in {@code SecurityConfig}, never EDITOR.
     *
     * @param request which two people, and on what basis
     * @param principal the member running the merge
     * @return what was moved, folded or dropped
     */
    @PostMapping("/persons")
    @Operation(summary = "Merge a duplicate person into the one being kept")
    public MergeResponse mergePersons(
            @Valid @RequestBody MergeRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return mergeService.mergePersons(request, principal.id());
    }
}
