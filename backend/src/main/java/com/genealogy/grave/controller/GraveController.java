package com.genealogy.grave.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.grave.dto.request.GraveRequest;
import com.genealogy.grave.dto.response.GraveResponse;
import com.genealogy.grave.dto.response.GraveSaved;
import com.genealogy.grave.service.GraveService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for mộ phần. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GraveController {

    private final GraveService graveService;
    private final PurgeService purgeService;

    /**
     * Returns the grave recorded for one person.
     *
     * @param personId the person id
     * @param principal the authenticated caller
     * @return the grave
     */
    @GetMapping("/persons/{personId}/grave")
    @Operation(summary = "Get a person's grave")
    public GraveResponse getByPerson(
            @PathVariable Long personId, @AuthenticationPrincipal AuthPrincipal principal) {
        return graveService.getByPerson(personId, principal.role());
    }

    /**
     * Records or replaces a person's grave.
     *
     * @param personId the person id
     * @param request the grave details
     * @param principal the authenticated caller
     * @return the saved grave, 201 when it is recorded for the first time
     */
    @PutMapping("/persons/{personId}/grave")
    @Operation(summary = "Record or update a person's grave")
    public ResponseEntity<GraveResponse> save(
            @PathVariable Long personId,
            @Valid @RequestBody GraveRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        GraveSaved saved = graveService.save(personId, request, principal.id());
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(saved.grave());
    }

    /**
     * Removes a person's grave record, with the photos and citations that name it.
     *
     * @param personId the person id
     * @param changeNote why the grave is being removed, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/persons/{personId}/grave")
    @Operation(summary = "Remove a person's grave record")
    public ResponseEntity<Void> delete(
            @PathVariable Long personId,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // Through purge, not graveService.delete: photos and citations name a grave by id with no FK.
        purgeService.purgeGrave(personId, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists every grave that has coordinates, for a tảo mộ map.
     *
     * @param principal the authenticated caller
     * @return the located graves
     */
    @GetMapping("/graves")
    @Operation(summary = "List graves that have coordinates")
    public List<GraveResponse> findLocated(@AuthenticationPrincipal AuthPrincipal principal) {
        return graveService.findLocated(principal.role());
    }
}
