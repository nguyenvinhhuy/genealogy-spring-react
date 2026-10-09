package com.genealogy.source.repository;

import com.genealogy.source.domain.Source;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for sources. */
public interface SourceRepository extends JpaRepository<Source, Long> {

    /**
     * Lists every source, ordered by title.
     *
     * @param pageable paging information, whose sort is ignored
     * @return the page
     */
    Page<Source> findAllByOrderByTitleAscIdAsc(Pageable pageable);

    /**
     * Finds sources whose title matches an already-escaped query, ignoring accents and case.
     *
     * @param query the search text, with LIKE wildcards escaped
     * @param pageable paging information, whose sort is ignored
     * @return the matching page, ordered by title
     */
    // Ordered here: a native query takes a client's sort key raw, and an unknown column is a 500 (§8.8 #45).
    @Query(
            value = """
                    SELECT * FROM sources s
                    WHERE immutable_unaccent(lower(s.title))
                          LIKE '%' || immutable_unaccent(lower(:query)) || '%' ESCAPE '\\'
                    ORDER BY s.title, s.id
                    """,
            countQuery = """
                    SELECT count(*) FROM sources s
                    WHERE immutable_unaccent(lower(s.title))
                          LIKE '%' || immutable_unaccent(lower(:query)) || '%' ESCAPE '\\'
                    """,
            nativeQuery = true)
    Page<Source> searchByTitle(@Param("query") String query, Pageable pageable);
}
