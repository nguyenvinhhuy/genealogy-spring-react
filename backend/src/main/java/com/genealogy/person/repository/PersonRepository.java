package com.genealogy.person.repository;

import com.genealogy.common.model.Gender;
import com.genealogy.person.domain.Person;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for persons. */
public interface PersonRepository extends JpaRepository<Person, Long> {

    /**
     * Loads a person for an edit, locking the row until the transaction ends.
     *
     * @param id person id
     * @return the person, or empty when there is none
     */
    // Locked, so two edits of one person are compared against a version neither can change under the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Person p WHERE p.id = :id")
    Optional<Person> findForEdit(@Param("id") Long id);

    /** How many words of a search box a query matches; a Vietnamese name rarely has more. */
    int SEARCH_WORDS = 5;

    // The very expression idx_person_names_full_trgm indexes (V9), so the planner can still use it.
    String FULL_NAME = "immutable_unaccent(lower(coalesce(n.surname || ' ', '') || coalesce(n.middle_name || ' ', '')"
            + " || n.given_name))";

    // A word matches at the start of a word of the name, never inside one: "yen" must not find every "Nguyễn" (§4.3).
    String WORD_1 = "(cast(:w1 AS text) IS NULL"
            + " OR " + FULL_NAME + " LIKE immutable_unaccent(lower(cast(:w1 AS text))) || '%' ESCAPE '\\'"
            + " OR " + FULL_NAME + " LIKE '% ' || immutable_unaccent(lower(cast(:w1 AS text))) || '%' ESCAPE '\\')";
    String WORD_2 = "(cast(:w2 AS text) IS NULL"
            + " OR " + FULL_NAME + " LIKE immutable_unaccent(lower(cast(:w2 AS text))) || '%' ESCAPE '\\'"
            + " OR " + FULL_NAME + " LIKE '% ' || immutable_unaccent(lower(cast(:w2 AS text))) || '%' ESCAPE '\\')";
    String WORD_3 = "(cast(:w3 AS text) IS NULL"
            + " OR " + FULL_NAME + " LIKE immutable_unaccent(lower(cast(:w3 AS text))) || '%' ESCAPE '\\'"
            + " OR " + FULL_NAME + " LIKE '% ' || immutable_unaccent(lower(cast(:w3 AS text))) || '%' ESCAPE '\\')";
    String WORD_4 = "(cast(:w4 AS text) IS NULL"
            + " OR " + FULL_NAME + " LIKE immutable_unaccent(lower(cast(:w4 AS text))) || '%' ESCAPE '\\'"
            + " OR " + FULL_NAME + " LIKE '% ' || immutable_unaccent(lower(cast(:w4 AS text))) || '%' ESCAPE '\\')";
    String WORD_5 = "(cast(:w5 AS text) IS NULL"
            + " OR " + FULL_NAME + " LIKE immutable_unaccent(lower(cast(:w5 AS text))) || '%' ESCAPE '\\'"
            + " OR " + FULL_NAME + " LIKE '% ' || immutable_unaccent(lower(cast(:w5 AS text))) || '%' ESCAPE '\\')";

    // Every filter is optional and ANDed; EXISTS, not a join: joining every name multiplies rows and skews the count.
    // Every word must match the same name, in any order, so "An Nguyễn" finds "Nguyễn Văn An" too.
    String SEARCH_WHERE = """
            WHERE (cast(:w1 AS text) IS NULL OR EXISTS (
                      SELECT 1 FROM person_names n
                      WHERE n.person_id = p.id
                        AND (n.is_primary OR NOT p.living OR :alternateNames)
            """
            + " AND " + WORD_1 + " AND " + WORD_2 + " AND " + WORD_3 + " AND " + WORD_4 + " AND " + WORD_5 + "))"
            + """

              AND (:byBranch = FALSE OR p.branch_id IN (:branchIds))
              AND (cast(:generation AS int) IS NULL OR p.generation = :generation)
              AND (cast(:living AS boolean) IS NULL OR p.living = :living)
              AND (:restrict = FALSE OR p.id IN (:ids))
            """;

    /**
     * Finds persons matching every filter that was given, ignoring accents and case (F20).
     *
     * @param w1 the first word typed, escaped for LIKE, or null when nothing was typed
     * @param w2 the second word, or null
     * @param w3 the third word, or null
     * @param w4 the fourth word, or null
     * @param w5 the fifth word, or null
     * @param lastWord the last word typed, ranked first when it begins the tên gọi, or null
     * @param byBranch whether to narrow the result to {@code branchIds}
     * @param branchIds the chi to match, subtree included; never empty
     * @param generation generation filter, or null
     * @param living living-status filter, or null for both
     * @param restrict whether to narrow the result to {@code ids}
     * @param ids person ids another feature's filter already narrowed things to; never empty
     * @param alternateNames whether a living person's alternate names may match the query
     * @param pageable paging information
     * @return the matching page
     */
    // The name join is LEFT, or a person with no primary name vanishes from every list and every search.
    @Query(
            value = """
                    SELECT p.* FROM persons p
                    LEFT JOIN person_names pn ON pn.person_id = p.id AND pn.is_primary
                    """
                    + SEARCH_WHERE
                    // A match on the tên gọi first, as "Yến" is a tên; then the Vietnamese order of §5.2, never by họ.
                    + " ORDER BY CASE WHEN immutable_unaccent(lower(pn.given_name))"
                    + " LIKE immutable_unaccent(lower(cast(:lastWord AS text))) || '%' ESCAPE '\\' THEN 0 ELSE 1 END,"
                    + " pn.given_name, pn.middle_name NULLS FIRST, pn.surname NULLS FIRST, p.id",
            countQuery = """
                    SELECT count(*) FROM persons p
                    LEFT JOIN person_names pn ON pn.person_id = p.id AND pn.is_primary
                    """
                    + SEARCH_WHERE,
            nativeQuery = true)
    Page<Person> search(
            @Param("w1") String w1,
            @Param("w2") String w2,
            @Param("w3") String w3,
            @Param("w4") String w4,
            @Param("w5") String w5,
            @Param("lastWord") String lastWord,
            @Param("byBranch") boolean byBranch,
            @Param("branchIds") Collection<Long> branchIds,
            @Param("generation") Integer generation,
            @Param("living") Boolean living,
            @Param("restrict") boolean restrict,
            @Param("ids") Collection<Long> ids,
            @Param("alternateNames") boolean alternateNames,
            Pageable pageable);

    /**
     * Loads every person with their names already attached, for a whole-clan export.
     *
     * @return every person, lowest id first
     */
    @Query("SELECT DISTINCT p FROM Person p LEFT JOIN FETCH p.names ORDER BY p.id")
    List<Person> findAllWithNames();

    /**
     * Lists the people whose primary name has no họ, or who have no primary name at all.
     *
     * @return their ids, lowest first
     */
    @Query("SELECT p.id FROM Person p LEFT JOIN p.names n ON n.primary = true "
            + "WHERE n.surname IS NULL OR TRIM(n.surname) = '' ORDER BY p.id")
    List<Long> findIdsWithoutSurname();

    /**
     * Counts the people recorded in one chi, not counting its sub-branches.
     *
     * @param branchId the branch id
     * @return how many people belong to it directly
     */
    long countByBranchId(Long branchId);

    /**
     * Reads one person's recorded branch id, without loading the entity.
     *
     * @param id person id
     * @return the branch id, wrapped, or empty when the person is unknown
     */
    @Query("SELECT p.branchId FROM Person p WHERE p.id = :id")
    Optional<Long> findBranchId(@Param("id") Long id);

    /**
     * Reads every person's recorded sex, current đời and living flag, without loading the entities.
     *
     * @return one row per person
     */
    @Query("SELECT p.id AS id, p.gender AS gender, p.generation AS generation, p.living AS living FROM Person p")
    List<LineageRow> findAllLineage();

    /**
     * The derived fields a whole-clan recompute reads for each person.
     */
    interface LineageRow {

        /**
         * Returns the stored living flag.
         *
         * @return whether the person is treated as living
         */
        boolean getLiving();

        /**
         * Returns the person id.
         *
         * @return the id
         */
        Long getId();

        /**
         * Returns the recorded sex.
         *
         * @return the sex
         */
        Gender getGender();

        /**
         * Returns the stored đời.
         *
         * @return the đời, or null when unplaced
         */
        Integer getGeneration();
    }
}
