package com.genealogy.media.repository;

import com.genealogy.common.model.MediaTargetType;
import com.genealogy.media.domain.Media;
import com.genealogy.media.domain.MediaKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for photos and scans. */
public interface MediaRepository extends JpaRepository<Media, Long> {

    /**
     * Lists the files attached to one record, in the order the family arranged them.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @return the matching files
     */
    List<Media> findByTargetTypeAndTargetIdOrderBySortOrderAscIdAsc(
            MediaTargetType targetType, Long targetId);

    /**
     * Finds the one file of a kind attached to a record.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param kind which kind to look for
     * @return the file, or empty when there is none
     */
    Optional<Media> findFirstByTargetTypeAndTargetIdAndKind(
            MediaTargetType targetType, Long targetId, MediaKind kind);

    /**
     * Lists every portrait of one kind of record, for a feature that renders the whole clan in one pass.
     *
     * @param targetType what kind of record
     * @param kind which kind of file counts as a portrait
     * @return every matching file, oldest first
     */
    List<Media> findByTargetTypeAndKindOrderByIdAsc(MediaTargetType targetType, MediaKind kind);

    /**
     * Returns the highest gallery position used on one record.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @return the highest position, or -1 when the record has no files
     */
    // One aggregate, not every row loaded to find a max: uploading 100 scans loaded ~10,000 rows (§8.9 #33).
    @Query("""
            SELECT coalesce(max(m.sortOrder), -1) FROM Media m
            WHERE m.targetType = :targetType AND m.targetId = :targetId
            """)
    int findMaxSortOrder(@Param("targetType") MediaTargetType targetType, @Param("targetId") Long targetId);

    /**
     * Counts the files attached to one record.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @return how many there are
     */
    int countByTargetTypeAndTargetId(MediaTargetType targetType, Long targetId);
}
