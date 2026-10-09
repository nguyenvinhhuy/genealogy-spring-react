package com.genealogy.family.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.family.dto.request.FamilyChildRequest;
import com.genealogy.family.dto.request.FamilyChildUpdateRequest;
import com.genealogy.family.dto.request.FamilyRequest;
import com.genealogy.family.dto.response.FamilyResponse;
import com.genealogy.family.service.FamilyService;
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

/** Endpoints for unions and parentage. */
@RestController
@RequestMapping("/api/v1/families")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;
    private final PurgeService purgeService;

    /**
     * Lists the unions one person belongs to.
     *
     * @param personId the person id
     * @return that person's unions
     */
    @GetMapping
    @Operation(summary = "List a person's unions")
    public List<FamilyResponse> findByPerson(@RequestParam Long personId) {
        return familyService.findByPerson(personId);
    }

    /**
     * Returns one union.
     *
     * @param id family id
     * @return the union
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one union")
    public FamilyResponse getById(@PathVariable Long id) {
        return familyService.getById(id);
    }

    /**
     * Creates a union.
     *
     * @param request the union to create
     * @param principal the authenticated caller
     * @return the created union
     */
    @PostMapping
    @Operation(summary = "Create a union")
    public ResponseEntity<FamilyResponse> create(
            @Valid @RequestBody FamilyRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(familyService.create(request, principal.id()));
    }

    /**
     * Updates a union.
     *
     * @param id family id
     * @param request the new values
     * @param principal the authenticated caller
     * @return the updated union
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a union")
    public FamilyResponse update(
            @PathVariable Long id,
            @Valid @RequestBody FamilyRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return familyService.update(id, request, principal.id());
    }

    /**
     * Deletes a union with its child links, events, citations and media.
     *
     * @param id family id
     * @param changeNote why the union is being deleted, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a union")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // Through purge, not familyService.delete: events, citations and media name a union with no FK.
        purgeService.purgeUnion(id, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }

    /**
     * Links a child into a union.
     *
     * @param id family id
     * @param request the child to link
     * @param principal the authenticated caller
     * @return the updated union
     */
    @PostMapping("/{id}/children")
    @Operation(summary = "Link a child into a union")
    public ResponseEntity<FamilyResponse> addChild(
            @PathVariable Long id,
            @Valid @RequestBody FamilyChildRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familyService.addChild(id, request, principal.id()));
    }

    /**
     * Corrects how a linked child relates to each partner and their birth order.
     *
     * @param id family id
     * @param childId the linked child
     * @param request the corrected relations and birth order
     * @param principal the authenticated caller
     * @return the updated union
     */
    @PutMapping("/{id}/children/{childId}")
    @Operation(summary = "Correct a child's link into a union")
    public FamilyResponse updateChild(
            @PathVariable Long id,
            @PathVariable Long childId,
            @Valid @RequestBody FamilyChildUpdateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return familyService.updateChild(id, childId, request, principal.id());
    }

    /**
     * Unlinks a child from a union.
     *
     * @param id family id
     * @param childId the person to unlink
     * @param changeNote why the child is being unlinked, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/{id}/children/{childId}")
    @Operation(summary = "Unlink a child from a union")
    public ResponseEntity<Void> removeChild(
            @PathVariable Long id,
            @PathVariable Long childId,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        familyService.removeChild(id, childId, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }
}
