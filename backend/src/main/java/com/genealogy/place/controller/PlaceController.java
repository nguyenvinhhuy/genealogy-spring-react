package com.genealogy.place.controller;

import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.place.dto.request.PlaceRequest;
import com.genealogy.place.dto.response.PlaceResponse;
import com.genealogy.place.service.PlaceService;
import com.genealogy.purge.service.PurgeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for places. */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Validated
public class PlaceController {

    // One screen of events names a handful of places; the cap keeps the batch from becoming a whole-table dump.
    private static final int MAX_BATCH = 200;

    private final PlaceService placeService;
    private final PurgeService purgeService;

    /**
     * Lists places in name order, optionally filtered by name.
     *
     * @param query accent-insensitive search text, optional
     * @param pageable which page
     * @return the matching page
     */
    @GetMapping
    @Operation(summary = "Search places by name")
    public PagedModel<PlaceResponse> search(
            @RequestParam(required = false) String query, Pageable pageable) {
        return new PagedModel<>(placeService.search(query, pageable));
    }

    /**
     * Lists every place with its path, for the page that manages the hierarchy as a tree.
     *
     * @return every place
     */
    @GetMapping("/all")
    @Operation(summary = "List every place")
    public List<PlaceResponse> findAll() {
        // A clan records hundreds of places, not millions, and a tree cannot be drawn one search page at a time.
        return placeService.findAll();
    }

    /**
     * Returns a batch of places by id, each with its full path.
     *
     * @param ids the places wanted, at most 200
     * @return the places that exist
     */
    @GetMapping("/batch")
    @Operation(summary = "Get several places at once")
    public List<PlaceResponse> findByIds(@RequestParam @Size(max = MAX_BATCH) List<Long> ids) {
        return placeService.findByIds(ids);
    }

    /**
     * Returns one place.
     *
     * @param id place id
     * @return the place
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one place")
    public PlaceResponse getById(@PathVariable Long id) {
        return placeService.getById(id);
    }

    /**
     * Creates a place.
     *
     * @param request the place to create
     * @param principal the authenticated caller
     * @return the created place
     */
    @PostMapping
    @Operation(summary = "Create a place")
    public ResponseEntity<PlaceResponse> create(
            @Valid @RequestBody PlaceRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(placeService.create(request, principal.id()));
    }

    /**
     * Updates a place.
     *
     * @param id place id
     * @param request the new values
     * @param principal the authenticated caller
     * @return the updated place
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a place")
    public PlaceResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PlaceRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return placeService.update(id, request, principal.id());
    }

    /**
     * Deletes a place that no event, grave or child place still names.
     *
     * @param id place id
     * @param changeNote why the place is being deleted, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a place")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // Through purge, not placeService.delete: only it can ask `event` and `grave` who still names the place.
        purgeService.purgePlace(id, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }
}
