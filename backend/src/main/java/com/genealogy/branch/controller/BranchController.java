package com.genealogy.branch.controller;

import com.genealogy.branch.dto.request.BranchRequest;
import com.genealogy.branch.dto.response.BranchResponse;
import com.genealogy.branch.service.BranchService;
import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.purge.service.PurgeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for clan branches. */
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;
    private final PurgeService purgeService;

    /**
     * Lists every branch.
     *
     * @return all branches, by name
     */
    @GetMapping
    @Operation(summary = "List clan branches")
    public List<BranchResponse> findAll() {
        return branchService.findAll();
    }

    /**
     * Returns one branch.
     *
     * @param id branch id
     * @return the branch
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one clan branch")
    public BranchResponse getById(@PathVariable Long id) {
        return branchService.getById(id);
    }

    /**
     * Creates a branch.
     *
     * @param request the branch to create
     * @param principal the authenticated caller
     * @return the created branch
     */
    @PostMapping
    @Operation(summary = "Create a clan branch")
    public ResponseEntity<BranchResponse> create(
            @Valid @RequestBody BranchRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(request, principal.id()));
    }

    /**
     * Updates a branch.
     *
     * @param id branch id
     * @param request the new values
     * @param principal the authenticated caller
     * @return the updated branch
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a clan branch")
    public BranchResponse update(
            @PathVariable Long id,
            @Valid @RequestBody BranchRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return branchService.update(id, request, principal.id());
    }

    /**
     * Deletes a branch that no one belongs to and nothing sits beneath.
     *
     * @param id branch id
     * @param changeNote why the branch is being deleted, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a clan branch")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // Through purge, not branchService.delete: only it can ask `person` who still belongs to the chi.
        purgeService.purgeBranch(id, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }
}
