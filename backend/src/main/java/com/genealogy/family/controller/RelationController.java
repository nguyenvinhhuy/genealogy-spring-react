package com.genealogy.family.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.family.dto.request.AddRelationRequest;
import com.genealogy.family.dto.request.LinkRelationRequest;
import com.genealogy.family.dto.response.AddRelationResponse;
import com.genealogy.family.service.FamilyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The endpoints that add a new relative to a person, or link two existing people, in one step (§6.1). */
// In `family` although the path is under /persons: the union is what it creates, and person cannot call family.
@RestController
@RequestMapping("/api/v1/persons/{personId}")
@RequiredArgsConstructor
public class RelationController {

    private final FamilyService familyService;

    /**
     * Creates a new person and links them to an existing one as spouse or child.
     *
     * @param personId the person being added to
     * @param request the new person and the kind of relation
     * @param principal the authenticated caller
     * @return the new person and the union they joined
     */
    @PostMapping("/relations")
    @Operation(summary = "Add a new spouse or child to a person")
    public ResponseEntity<AddRelationResponse> add(
            @PathVariable Long personId,
            @Valid @RequestBody AddRelationRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familyService.addRelation(personId, request, principal.id()));
    }

    /**
     * Links a person to someone already recorded, as a spouse, a child or a parent.
     *
     * @param personId the person the link is made from
     * @param request who the other person is, what they are to them, and which union
     * @param principal the authenticated caller
     * @return the other person and the union that now links them
     */
    @PostMapping("/links")
    @Operation(summary = "Link two people who are both already recorded")
    public ResponseEntity<AddRelationResponse> link(
            @PathVariable Long personId,
            @Valid @RequestBody LinkRelationRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familyService.linkExisting(personId, request, principal.id()));
    }
}
