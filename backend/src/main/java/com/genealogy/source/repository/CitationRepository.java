package com.genealogy.source.repository;

import com.genealogy.common.model.CitationTargetType;
import com.genealogy.source.domain.Citation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for citations. */
public interface CitationRepository extends JpaRepository<Citation, Long> {

    /**
     * Lists the citations backing up one record.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @return the citations
     */
    List<Citation> findByTargetTypeAndTargetIdOrderByIdAsc(CitationTargetType targetType, Long targetId);

    /**
     * Lists the citations backing up any of several records of one kind.
     *
     * @param targetType what kind of record
     * @param targetIds the record ids, never empty
     * @return the citations, ordered by id
     */
    List<Citation> findByTargetTypeAndTargetIdInOrderByIdAsc(
            CitationTargetType targetType, Collection<Long> targetIds);

    /**
     * Lists the citations of one source.
     *
     * @param sourceId the source
     * @return the citations, ordered by id
     */
    List<Citation> findBySourceIdOrderByIdAsc(Long sourceId);

    /**
     * Counts how many facts a source has been used to back up.
     *
     * @param sourceId the source
     * @return the citation count
     */
    long countBySourceId(Long sourceId);

    /**
     * Finds the citation that {@code uq_citations} would collide with, reading two null locators as equal.
     *
     * @param sourceId the source
     * @param targetType what kind of record
     * @param targetId the record id
     * @param locator where inside the source, or null
     * @return the colliding citation's id, or none
     */
    // IS NOT DISTINCT FROM is what NULLS NOT DISTINCT means; a plain `=` never matches two nulls.
    @Query(value = """
            SELECT c.id FROM citations c
            WHERE c.source_id = :sourceId AND c.target_type = :targetType AND c.target_id = :targetId
              AND c.locator IS NOT DISTINCT FROM CAST(:locator AS VARCHAR)
            """, nativeQuery = true)
    List<Long> findColliding(
            @Param("sourceId") Long sourceId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("locator") String locator);

    /**
     * Counts the citations of each of a batch of sources, in one query.
     *
     * @param sourceIds the sources, never empty
     * @return each source id paired with how many times it is cited, for those cited at all
     */
    // Only the page's ids: counting over the whole table for a page of twenty read every citation (§8.8 #30).
    @Query("SELECT c.sourceId, count(c) FROM Citation c WHERE c.sourceId IN :sourceIds GROUP BY c.sourceId")
    List<Object[]> countGroupedBySource(@Param("sourceIds") Collection<Long> sourceIds);

    /**
     * Counts the citations of every source that has any, in one query.
     *
     * @return each source id paired with how many times it is cited
     */
    @Query("SELECT c.sourceId, count(c) FROM Citation c GROUP BY c.sourceId")
    List<Object[]> countGroupedByAllSources();
}
