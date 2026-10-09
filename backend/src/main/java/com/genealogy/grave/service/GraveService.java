package com.genealogy.grave.service;

import com.genealogy.common.model.Role;
import com.genealogy.grave.dto.request.GraveRequest;
import com.genealogy.grave.dto.response.DroppedGrave;
import com.genealogy.grave.dto.response.GraveResponse;
import com.genealogy.grave.dto.response.GraveSaved;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Mộ phần operations. */
public interface GraveService {

    /**
     * Returns the grave recorded for one person, hidden from callers below EDITOR while its owner lives.
     *
     * @param personId the person id
     * @param role the calling member's access level
     * @return the grave, or empty when none is recorded or the caller may not see it
     */
    Optional<GraveResponse> findByPerson(Long personId, Role role);

    /**
     * Returns the grave recorded for one person, failing with a 404 when there is none the caller may see.
     *
     * @param personId the person id
     * @param role the calling member's access level
     * @return the grave
     */
    GraveResponse getByPerson(Long personId, Role role);

    /**
     * Lists every grave that has coordinates, hiding a living owner's from callers below EDITOR.
     *
     * @param role the calling member's access level
     * @return the located graves
     */
    List<GraveResponse> findLocated(Role role);

    /**
     * Lists every grave in the clan.
     *
     * @return every grave, ordered by person id so an export is reproducible
     */
    List<GraveResponse> findAll();

    /**
     * Records or replaces a person's grave and records it in the audit trail.
     *
     * @param personId the person id
     * @param request the grave details
     * @param actorId the member making the change
     * @return the saved grave, and whether it is new
     */
    GraveSaved save(Long personId, GraveRequest request, Long actorId);

    /**
     * Removes a person's grave record and records it in the audit trail.
     *
     * @param personId the person id
     * @param actorId the member making the change
     * @param changeNote why the grave is being removed, or null
     */
    // The grave's photos and citations name it with no FK, so callers go through `purge`, which forgets them first.
    void delete(Long personId, Long actorId, String changeNote);

    /**
     * Moves a grave off one person onto another, for a merge (F11), recording each change.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason
     * @return the grave that had to be dropped, or empty when it moved or there was none
     */
    Optional<DroppedGrave> reassignPerson(Long fromId, Long toId, Long actorId, String changeNote);

    /**
     * Reports whether a grave exists.
     *
     * @param graveId grave id
     * @return true if it exists
     */
    boolean exists(Long graveId);

    /**
     * Returns whose grave one record is.
     *
     * @param graveId the grave id
     * @return the person id, or empty when there is no such grave
     */
    Optional<Long> personIdOf(Long graveId);

    /**
     * Returns the id of one person's grave record.
     *
     * @param personId the person id
     * @return the grave id, or empty when none is recorded
     */
    Optional<Long> idOf(Long personId);

    /**
     * Reports whether a grave belongs to someone still treated as living, failing closed for an unknown grave.
     *
     * @param graveId the grave id
     * @return true when the owner lives, and true as well when there is no such grave
     */
    // The one grave guard (§9): media and source each had their own copy of "resolve the owner, then ask person".
    boolean involvesLiving(Long graveId);

    /**
     * Counts the graves recorded at one place.
     *
     * @param placeId the place id
     * @return how many graves name it
     */
    long countByPlace(Long placeId);

    /**
     * Finds which of a batch of people have a mộ (not a sinh phần) recorded, which counts as a recorded death.
     *
     * @param personIds the people to check
     * @return the ids of those who do
     */
    Set<Long> findBuried(Collection<Long> personIds);

    /**
     * Lists every person with a mộ (not a sinh phần) recorded, for a whole-clan living sweep.
     *
     * @return their ids
     */
    Set<Long> findAllBuried();
}
