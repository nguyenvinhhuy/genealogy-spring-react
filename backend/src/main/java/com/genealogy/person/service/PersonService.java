package com.genealogy.person.service;

import com.genealogy.common.model.Gender;
import com.genealogy.common.model.Role;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.dto.request.PersonSearchCriteria;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.dto.response.PersonSummaryResponse;
import com.genealogy.person.dto.response.PersonView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Person operations. */
public interface PersonService {

    /**
     * Lists persons matching every filter that was given, ordered by tên then tên đệm then họ (§5.2).
     *
     * @param criteria the filters to apply
     * @param pageable paging information
     * @return the matching page
     */
    Page<PersonSummaryResponse> search(PersonSearchCriteria criteria, Pageable pageable);

    /**
     * Returns one person, redacted when the caller may not see a living person's details (CLAUDE.md §3.6).
     *
     * @param id person id
     * @param role the calling member's access level
     * @return the full person, or the redacted view
     */
    PersonView getById(Long id, Role role);

    /**
     * Reports whether a person is treated as living, for the living-person guard (CLAUDE.md §3.6).
     *
     * @param id person id
     * @return true when living, and true as well when the person is unknown, so a bad id cannot leak
     */
    boolean isLiving(Long id);

    /**
     * Returns a person's current field values in the shape an update takes.
     *
     * @param id person id
     * @return the current values, with no change note
     */
    PersonRequest currentState(Long id);

    /**
     * Creates a person together with their names.
     *
     * @param request the person to create
     * @param createdBy id of the member making the change
     * @return the created person
     */
    PersonDetailResponse create(PersonRequest request, Long createdBy);

    /**
     * Replaces a person's fields and name list, refusing an edit made from an older version.
     *
     * @param id person id
     * @param request the new values
     * @param changedBy id of the member making the change
     * @return the updated person
     */
    PersonDetailResponse update(Long id, PersonRequest request, Long changedBy);

    /**
     * Deletes a person.
     *
     * @param id person id
     * @param changedBy id of the member making the change
     * @param changeNote why the person was deleted, or null
     */
    void delete(Long id, Long changedBy, String changeNote);

    /**
     * Reports whether a person exists.
     *
     * @param id person id
     * @return true if it exists
     */
    boolean exists(Long id);

    /**
     * Fails with a 400 when a person a request body names does not exist.
     *
     * @param id the person id
     */
    // 400, not 404: the id came from a body, not the path (§8.7 #17), and seven features each wrote this check.
    void requireExists(Long id);

    /**
     * Counts the people recorded in one chi, not counting its sub-branches.
     *
     * @param branchId the branch id
     * @return how many people belong to it directly
     */
    long countInBranch(Long branchId);

    /**
     * Returns the chi a person is recorded under.
     *
     * @param personId the person id
     * @return the branch id, or null when they have none or the person is unknown
     */
    Long branchIdOf(Long personId);

    /**
     * Sets every person's đời to the given value, clearing it for anyone the map leaves out.
     *
     * @param generationByPersonId the đời of each person the parentage graph places
     */
    void applyGenerations(Map<Long, Integer> generationByPersonId);

    /**
     * Reads every person's recorded sex in one light query, for the đời recompute to find each family line's founder.
     *
     * @return each person's recorded sex
     */
    Map<Long, Gender> findAllGenders();

    /**
     * Reads every person's stored living flag in one light query, for the nightly lifespan sweep.
     *
     * @return each person's living flag
     */
    Map<Long, Boolean> findAllLiving();

    /**
     * Writes the recomputed living flag, which the event feature owns the dates to derive.
     *
     * @param personId the person whose flag changed
     * @param living whether the person is still treated as living
     */
    void applyLiving(Long personId, boolean living);

    /**
     * Loads the node projection for a batch of persons in one query (CLAUDE.md §4).
     *
     * @param ids the persons to load
     * @return their node projections, in no particular order
     */
    List<PersonNodeResponse> findNodes(Collection<Long> ids);

    /**
     * Loads the node projection of every person in the clan in one query.
     *
     * @return every person's node projection, lowest id first
     */
    List<PersonNodeResponse> findAllNodes();

    /**
     * Loads every person with every name.
     *
     * @return every person, ordered by id so an export is reproducible
     */
    List<PersonDetailResponse> findAllDetails();

    /**
     * Folds one person's names and fields into another and deletes them, for a merge (F11).
     *
     * @param targetId the person to keep
     * @param duplicateId the person to absorb and delete
     * @param actorId id of the member running the merge
     * @param reason why the two are the same person, recorded as the basis in the audit trail
     * @return how many of the duplicate's names were kept as alternates
     */
    int absorb(Long targetId, Long duplicateId, Long actorId, String reason);
}
