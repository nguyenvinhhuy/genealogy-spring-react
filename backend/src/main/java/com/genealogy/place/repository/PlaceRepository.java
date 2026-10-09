package com.genealogy.place.repository;

import com.genealogy.place.domain.Place;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for places. */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * Lists a place and every place beneath it in the hierarchy, in one query (§3.7).
     *
     * @param placeId the place at the top of the subtree
     * @return the ids of that place and all its descendants
     */
    // UNION, not UNION ALL: a cycle is refused on write, but a query that loops for ever on one is not worth the risk.
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM places WHERE id = :placeId
                UNION
                SELECT p.id FROM places p JOIN subtree s ON p.parent_id = s.id
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<Long> findIdsWithDescendants(@Param("placeId") Long placeId);

    /**
     * Reads each given place's name and every ancestor's name, most specific first, in one query.
     *
     * @param ids the places whose paths are wanted
     * @return one row per place and level, ordered by place then depth
     */
    // Bounded by depth, not by UNION: the same ancestor legitimately appears in two places' chains.
    @Query(value = """
            WITH RECURSIVE chain AS (
                SELECT id AS start_id, name, parent_id, 0 AS depth FROM places WHERE id IN (:ids)
                UNION ALL
                SELECT c.start_id, p.name, p.parent_id, c.depth + 1
                FROM places p JOIN chain c ON p.id = c.parent_id
                WHERE c.depth < 16
            )
            SELECT start_id AS startId, name AS name, depth AS depth FROM chain ORDER BY start_id, depth
            """, nativeQuery = true)
    List<PathRow> findPathRows(@Param("ids") Collection<Long> ids);

    /**
     * Lists places in name order, lowest id first among equal names.
     *
     * @param pageable which page
     * @return the page
     */
    Page<Place> findAllByOrderByNameAscIdAsc(Pageable pageable);

    /**
     * Finds places whose name contains the query, ignoring accents and case, in name order.
     *
     * @param query the search text, already escaped for LIKE
     * @param pageable which page; its sort is ignored in favour of the query's own order
     * @return the matching page
     */
    @Query(
            value = """
                    SELECT * FROM places p
                    WHERE immutable_unaccent(lower(p.name))
                          LIKE '%' || immutable_unaccent(lower(:query)) || '%' ESCAPE '\\'
                    ORDER BY p.name, p.id
                    """,
            countQuery = """
                    SELECT count(*) FROM places p
                    WHERE immutable_unaccent(lower(p.name))
                          LIKE '%' || immutable_unaccent(lower(:query)) || '%' ESCAPE '\\'
                    """,
            nativeQuery = true)
    Page<Place> searchByName(@Param("query") String query, Pageable pageable);

    /**
     * Finds places of a given name at any level, ignoring accents and case.
     *
     * @param name the place name to match
     * @return the matching place ids, oldest first
     */
    // Through immutable_unaccent (§4.3), or "Ha Noi" from a foreign GEDCOM never matches "Hà Nội".
    @Query(value = """
            SELECT id FROM places p
            WHERE immutable_unaccent(lower(p.name)) = immutable_unaccent(lower(:name))
            ORDER BY id
            """, nativeQuery = true)
    List<Long> findIdsByName(@Param("name") String name);

    /**
     * Finds a top-level place of a given name, ignoring accents and case.
     *
     * @param name the place name to match
     * @return the matching place ids, oldest first
     */
    @Query(value = """
            SELECT id FROM places p
            WHERE immutable_unaccent(lower(p.name)) = immutable_unaccent(lower(:name)) AND p.parent_id IS NULL
            ORDER BY id
            """, nativeQuery = true)
    List<Long> findTopLevelIdsByName(@Param("name") String name);

    /**
     * Finds a place of a given name directly under one parent, ignoring accents and case.
     *
     * @param name the place name to match
     * @param parentId the parent to look under
     * @return the matching place ids, oldest first
     */
    // Two queries rather than one `IS NOT DISTINCT FROM`, which Postgres cannot answer from idx_places_parent.
    @Query(value = """
            SELECT id FROM places p
            WHERE immutable_unaccent(lower(p.name)) = immutable_unaccent(lower(:name)) AND p.parent_id = :parentId
            ORDER BY id
            """, nativeQuery = true)
    List<Long> findIdsByNameUnder(@Param("name") String name, @Param("parentId") Long parentId);

    /**
     * Counts the places directly under one place.
     *
     * @param parentId the parent place id
     * @return how many child places it has
     */
    long countByParentId(Long parentId);

    /**
     * Lists the places directly under one place.
     *
     * @param parentId the parent place id
     * @return its child places
     */
    List<Place> findByParentId(Long parentId);

    /** One level of one place's path, as {@link #findPathRows} reads it. */
    interface PathRow {

        /**
         * Returns the place whose path this row belongs to.
         *
         * @return that place's id
         */
        Long getStartId();

        /**
         * Returns this level's name.
         *
         * @return the name
         */
        String getName();

        /**
         * Returns how far above the starting place this level sits.
         *
         * @return 0 for the place itself, 1 for its parent, and so on
         */
        Integer getDepth();
    }
}
