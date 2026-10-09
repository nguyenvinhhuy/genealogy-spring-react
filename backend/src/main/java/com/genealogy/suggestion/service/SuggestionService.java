package com.genealogy.suggestion.service;

import com.genealogy.common.model.Role;
import com.genealogy.suggestion.domain.SuggestionStatus;
import com.genealogy.suggestion.dto.request.SuggestionRequest;
import com.genealogy.suggestion.dto.request.SuggestionReviewRequest;
import com.genealogy.suggestion.dto.response.SuggestionPreview;
import com.genealogy.suggestion.dto.response.SuggestionResponse;
import com.genealogy.suggestion.dto.response.SuggestionsMoved;
import java.util.Collection;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Edit suggestions: con cháu góp thông tin, trưởng tộc duyệt (docs/analysis.md F17). */
public interface SuggestionService {

    /**
     * Lists suggestions newest first, optionally by state, only the caller's own below EDITOR.
     *
     * @param status the state to filter by, or null for all of them
     * @param role the calling member's access level
     * @param memberId the calling member's id
     * @param pageable which page; its size is capped and its sort ignored
     * @return the matching page
     */
    Page<SuggestionResponse> search(SuggestionStatus status, Role role, Long memberId, Pageable pageable);

    /**
     * Returns one suggestion, answering "no such suggestion" below EDITOR when it is someone else's.
     *
     * @param id suggestion id
     * @param role the calling member's access level
     * @param memberId the calling member's id
     * @return the suggestion
     */
    SuggestionResponse getById(Long id, Role role, Long memberId);

    /**
     * Shows what approving a suggestion would change, field by field (EDITOR+).
     *
     * @param id suggestion id
     * @param role the calling member's access level
     * @return the person as they are and as approval would leave them
     */
    SuggestionPreview preview(Long id, Role role);

    /**
     * Records a suggestion offered by a member.
     *
     * @param request what is being suggested
     * @param createdBy id of the member offering it
     * @return the recorded suggestion
     */
    SuggestionResponse create(SuggestionRequest request, Long createdBy);

    /**
     * Approves or rejects a suggestion, applying its proposal when approved (EDITOR+).
     *
     * @param id suggestion id
     * @param request the decision and its reason
     * @param role the calling member's access level
     * @param reviewedBy id of the member deciding
     * @return the reviewed suggestion
     */
    SuggestionResponse review(Long id, SuggestionReviewRequest request, Role role, Long reviewedBy);

    /**
     * Counts the suggestions still waiting for a reviewer, only the caller's own below EDITOR.
     *
     * @param role the calling member's access level
     * @param memberId the calling member's id
     * @return how many are pending
     */
    long countPending(Role role, Long memberId);

    /**
     * Moves every suggestion off one person onto another, for a merge (F11), recording each change.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason
     * @return how many moved, and how many pending edits became notes
     */
    SuggestionsMoved reassignTarget(Long fromId, Long toId, Long actorId, String changeNote);

    /**
     * Points each pending new person's union at the one that replaced it, or at a new union when it is gone.
     *
     * @param foldedInto each folded union's id mapped to the union that survived it
     * @param goneUnions the ids of unions deleted outright
     * @param actorId the member whose merge or delete did it
     * @param changeNote why
     * @return how many suggestions were re-anchored
     */
    int reanchorUnions(Map<Long, Long> foldedInto, Collection<Long> goneUnions, Long actorId, String changeNote);

    /**
     * Deletes the suggestions about a person who is themselves being deleted, recording each one.
     *
     * @param personId the person being deleted
     * @param actorId the member deleting them
     * @param changeNote why they are being deleted
     * @return how many suggestions were deleted
     */
    int forgetTarget(Long personId, Long actorId, String changeNote);
}
