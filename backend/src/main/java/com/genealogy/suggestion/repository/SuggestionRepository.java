package com.genealogy.suggestion.repository;

import com.genealogy.suggestion.domain.Suggestion;
import com.genealogy.suggestion.domain.SuggestionKind;
import com.genealogy.suggestion.domain.SuggestionStatus;
import com.genealogy.suggestion.domain.SuggestionTargetType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for suggestions. */
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    /**
     * Lists suggestions, newest first, narrowed by state and by the member who offered them.
     *
     * @param status the state to filter by, or null for every state
     * @param owner the only member whose suggestions may be returned, or null for everyone's
     * @param pageable which page; its sort is ignored
     * @return the matching page
     */
    // One query for all four combinations, ordered here with an id tie-break, so pages never overlap (§8.9 #11).
    @Query(value = """
            SELECT s FROM Suggestion s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:owner IS NULL OR s.createdBy = :owner)
            ORDER BY s.createdAt DESC, s.id DESC
            """, countQuery = """
            SELECT count(s) FROM Suggestion s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:owner IS NULL OR s.createdBy = :owner)
            """)
    Page<Suggestion> search(
            @Param("status") SuggestionStatus status, @Param("owner") Long owner, Pageable pageable);

    /**
     * Counts suggestions in one state, narrowed by the member who offered them.
     *
     * @param status the state to count
     * @param owner the only member whose suggestions count, or null for everyone's
     * @return how many there are
     */
    @Query("""
            SELECT count(s) FROM Suggestion s
            WHERE s.status = :status AND (:owner IS NULL OR s.createdBy = :owner)
            """)
    long countInState(@Param("status") SuggestionStatus status, @Param("owner") Long owner);

    /**
     * Records a decision on a suggestion only while it is still pending, bumping its version.
     *
     * @param id the suggestion
     * @param status the decision
     * @param reviewedBy the member deciding
     * @param reviewedAt when
     * @param reviewNote why, or null
     * @return 1 when this call made the decision, 0 when someone else already had
     */
    // The row count is the lock: check and write in one statement, like refresh-token rotation (§8.2).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Suggestion s
            SET s.status = :status, s.reviewedBy = :reviewedBy, s.reviewedAt = :reviewedAt,
                s.reviewNote = :reviewNote, s.version = s.version + 1
            WHERE s.id = :id AND s.status = com.genealogy.suggestion.domain.SuggestionStatus.PENDING
            """)
    int claim(
            @Param("id") Long id,
            @Param("status") SuggestionStatus status,
            @Param("reviewedBy") Long reviewedBy,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("reviewNote") String reviewNote);

    /**
     * Lists the suggestions pointing at one record.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @return the matching suggestions
     */
    List<Suggestion> findByTargetTypeAndTargetId(SuggestionTargetType targetType, Long targetId);

    /**
     * Lists the suggestions of one kind in one state, such as every pending new person.
     *
     * @param kind what they ask for
     * @param status their state
     * @return the matching suggestions
     */
    List<Suggestion> findByKindAndStatus(SuggestionKind kind, SuggestionStatus status);
}
