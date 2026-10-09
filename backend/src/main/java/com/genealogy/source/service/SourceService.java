package com.genealogy.source.service;

import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.Role;
import com.genealogy.source.dto.request.CitationRequest;
import com.genealogy.source.dto.request.SourceMergeRequest;
import com.genealogy.source.dto.request.SourceRequest;
import com.genealogy.source.dto.response.CitationResponse;
import com.genealogy.source.dto.response.CitationsMoved;
import com.genealogy.source.dto.response.SourceMergeResponse;
import com.genealogy.source.dto.response.SourceResponse;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Sources and the citations that tie them to recorded facts (F7). */
public interface SourceService {

    /**
     * Lists sources ordered by title, optionally filtered by an accent-insensitive title search, for EDITOR+ only.
     *
     * @param query the search text, or null for no filter
     * @param pageable paging information; its sort is ignored
     * @param role the calling member's access level
     * @return the matching page
     */
    Page<SourceResponse> search(String query, Pageable pageable, Role role);

    /**
     * Returns one source, for EDITOR+ only.
     *
     * @param id source id
     * @param role the calling member's access level
     * @return the source
     */
    SourceResponse getById(Long id, Role role);

    /**
     * Reports whether a source exists.
     *
     * @param id source id
     * @return true if it exists
     */
    boolean exists(Long id);

    /**
     * Creates a source and records it in the audit trail.
     *
     * @param request the source to create
     * @param actorId id of the member adding it
     * @return the created source
     */
    SourceResponse create(SourceRequest request, Long actorId);

    /**
     * Updates a source and records it in the audit trail.
     *
     * @param id source id
     * @param request the new values
     * @param actorId id of the member making the change
     * @return the updated source
     */
    SourceResponse update(Long id, SourceRequest request, Long actorId);

    /**
     * Deletes a source, refusing while anything still cites it.
     *
     * @param id source id
     * @param actorId id of the member making the change
     * @param changeNote why the source is being deleted, or null
     */
    void delete(Long id, Long actorId, String changeNote);

    /**
     * Folds one source into another: its citations move onto the kept source and it is deleted.
     *
     * @param request which source is folded into which, and why
     * @param actorId id of the member running the merge
     * @return the kept source and what moved
     */
    SourceMergeResponse merge(SourceMergeRequest request, Long actorId);

    /**
     * Lists the citations backing up one record, hiding a living person's from callers below EDITOR.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param role the calling member's access level
     * @return the citations, or nothing when the record involves a living person the caller may not see
     */
    List<CitationResponse> findCitations(CitationTargetType targetType, Long targetId, Role role);

    /**
     * Cites a source against one recorded fact, creating the source first when the request describes a new one.
     *
     * @param request the citation to add
     * @param actorId id of the member making the change
     * @return the created citation
     */
    CitationResponse addCitation(CitationRequest request, Long actorId);

    /**
     * Changes which source a citation names, where in it, and what it quotes.
     *
     * @param id citation id
     * @param request the new values; its target must be the citation's own
     * @param actorId id of the member making the change
     * @return the updated citation
     */
    CitationResponse updateCitation(Long id, CitationRequest request, Long actorId);

    /**
     * Removes one citation, leaving its source alone.
     *
     * @param id citation id
     * @param actorId id of the member making the change
     * @param changeNote why the citation is being removed, or null
     */
    void removeCitation(Long id, Long actorId, String changeNote);

    /**
     * Lists every source in the clan.
     *
     * @return every source, ordered by id so an export is reproducible
     */
    List<SourceResponse> findAllSources();

    /**
     * Lists every citation in the clan.
     *
     * @return every citation, ordered by id
     */
    List<CitationResponse> findAllCitations();

    /**
     * Moves every citation off one target onto another, for a merge (F11), folding any that would collide.
     *
     * @param targetType what kind of record is being merged
     * @param fromId the record being absorbed
     * @param toId the record being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason, recorded on every citation it moves
     * @return how many moved, and a line for each that was folded into one already on the kept record
     */
    CitationsMoved reassignTarget(
            CitationTargetType targetType, Long fromId, Long toId, Long actorId, String changeNote);

    /**
     * Deletes the citations of a record that is itself being deleted, recording each one.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param actorId the member deleting the record
     * @param changeNote why the record is being deleted
     * @return how many citations were deleted
     */
    int forgetTarget(CitationTargetType targetType, Long targetId, Long actorId, String changeNote);

    /**
     * Deletes the citations of several records of one kind that are being deleted, in one read.
     *
     * @param targetType what kind of record
     * @param targetIds the record ids
     * @param actorId the member deleting the records
     * @param changeNote why the records are being deleted
     * @return how many citations were deleted
     */
    int forgetTargets(CitationTargetType targetType, Collection<Long> targetIds, Long actorId, String changeNote);
}
