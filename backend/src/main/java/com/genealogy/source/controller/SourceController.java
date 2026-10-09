package com.genealogy.source.controller;

import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.merge.service.MergeService;
import com.genealogy.purge.service.PurgeService;
import com.genealogy.source.dto.request.CitationRequest;
import com.genealogy.source.dto.request.SourceMergeRequest;
import com.genealogy.source.dto.request.SourceRequest;
import com.genealogy.source.dto.response.CitationResponse;
import com.genealogy.source.dto.response.SourceMergeResponse;
import com.genealogy.source.dto.response.SourceResponse;
import com.genealogy.source.service.SourceService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for sources and citations. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SourceController {

    private final SourceService sourceService;
    // Delete and merge go through the orchestrators: a source's scans belong to media, which source may not call.
    private final PurgeService purgeService;
    private final MergeService mergeService;

    /**
     * Lists sources ordered by title, optionally filtered by title.
     *
     * @param query accent-insensitive search text, optional
     * @param pageable paging information
     * @param principal the authenticated caller
     * @return the matching page
     */
    @GetMapping("/sources")
    @Operation(summary = "Search sources by title")
    public PagedModel<SourceResponse> search(
            @RequestParam(required = false) String query,
            Pageable pageable,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return new PagedModel<>(sourceService.search(query, pageable, principal.role()));
    }

    /**
     * Returns one source.
     *
     * @param id source id
     * @param principal the authenticated caller
     * @return the source
     */
    @GetMapping("/sources/{id}")
    @Operation(summary = "Get one source")
    public SourceResponse getById(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return sourceService.getById(id, principal.role());
    }

    /**
     * Creates a source.
     *
     * @param request the source to create
     * @param principal the authenticated caller
     * @return the created source
     */
    @PostMapping("/sources")
    @Operation(summary = "Create a source")
    public ResponseEntity<SourceResponse> create(
            @Valid @RequestBody SourceRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sourceService.create(request, principal.id()));
    }

    /**
     * Updates a source.
     *
     * @param id source id
     * @param request the new values
     * @param principal the authenticated caller
     * @return the updated source
     */
    @PutMapping("/sources/{id}")
    @Operation(summary = "Update a source")
    public SourceResponse update(
            @PathVariable Long id,
            @Valid @RequestBody SourceRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return sourceService.update(id, request, principal.id());
    }

    /**
     * Deletes a source that nothing cites.
     *
     * @param id source id
     * @param changeNote why the source is being deleted, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/sources/{id}")
    @Operation(summary = "Delete a source that nothing cites")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        purgeService.purgeSource(id, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }

    /**
     * Folds one source into another.
     *
     * @param request which source is folded into which, and why
     * @param principal the authenticated caller
     * @return the kept source and what moved
     */
    @PostMapping("/sources/merge")
    @Operation(summary = "Fold one source into another")
    public SourceMergeResponse merge(
            @Valid @RequestBody SourceMergeRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return mergeService.mergeSources(request, principal.id());
    }

    /**
     * Lists the citations backing up one record.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param principal the authenticated caller
     * @return the citations
     */
    @GetMapping("/citations")
    @Operation(summary = "List the citations backing up one record")
    public List<CitationResponse> findCitations(
            @RequestParam CitationTargetType targetType,
            @RequestParam Long targetId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return sourceService.findCitations(targetType, targetId, principal.role());
    }

    /**
     * Cites a source against one recorded fact, creating the source in the same request when it is new.
     *
     * @param request the citation to add
     * @param principal the authenticated caller
     * @return the created citation
     */
    @PostMapping("/citations")
    @Operation(summary = "Cite a source against a record")
    public ResponseEntity<CitationResponse> addCitation(
            @Valid @RequestBody CitationRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.addCitation(request, principal.id()));
    }

    /**
     * Changes a citation's source, locator or quote.
     *
     * @param id citation id
     * @param request the new values
     * @param principal the authenticated caller
     * @return the updated citation
     */
    @PutMapping("/citations/{id}")
    @Operation(summary = "Update a citation")
    public CitationResponse updateCitation(
            @PathVariable Long id,
            @Valid @RequestBody CitationRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return sourceService.updateCitation(id, request, principal.id());
    }

    /**
     * Removes one citation, leaving its source alone.
     *
     * @param id citation id
     * @param changeNote why the citation is being removed, for the change history
     * @param principal the authenticated caller
     * @return an empty 204 response
     */
    @DeleteMapping("/citations/{id}")
    @Operation(summary = "Remove a citation")
    public ResponseEntity<Void> removeCitation(
            @PathVariable Long id,
            @RequestParam(required = false) @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
            @AuthenticationPrincipal AuthPrincipal principal) {
        sourceService.removeCitation(id, principal.id(), changeNote);
        return ResponseEntity.noContent().build();
    }
}
