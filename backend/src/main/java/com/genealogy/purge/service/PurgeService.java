package com.genealogy.purge.service;

import com.genealogy.purge.dto.response.PurgeResponse;
import com.genealogy.purge.dto.response.RowsForgotten;

/** Deleting a person, a union, an event, a chi, a place or a grave together with everything that points at them. */
public interface PurgeService {

    /**
     * Deletes a person and every row that names them, refusing when the delete would orphan children.
     *
     * @param personId the person to delete
     * @param actorId id of the member making the change
     * @param changeNote why the person is being deleted, or null
     * @return what was deleted along the way
     */
    PurgeResponse purgePerson(Long personId, Long actorId, String changeNote);

    /**
     * Deletes a union together with its events, citations and media, refusing when it still has children.
     *
     * @param familyId the union to delete
     * @param actorId id of the member making the change
     * @param changeNote why the union is being deleted, or null
     */
    void purgeUnion(Long familyId, Long actorId, String changeNote);

    /**
     * Deletes an event and the citations that name it, since a citation keys on an event id with no foreign key.
     *
     * @param eventId the event to delete
     * @param actorId id of the member making the change
     * @param changeNote why the event is being deleted, or null
     */
    void purgeEvent(Long eventId, Long actorId, String changeNote);

    /**
     * Deletes a chi, refusing when anyone still belongs to it or a sub-branch still sits beneath it.
     *
     * @param branchId the chi to delete
     * @param actorId id of the member making the change
     * @param changeNote why the chi is being deleted, or null
     */
    void purgeBranch(Long branchId, Long actorId, String changeNote);

    /**
     * Deletes a place, refusing while an event, a grave or a smaller place still names it.
     *
     * @param placeId the place to delete
     * @param actorId id of the member making the change
     * @param changeNote why the place is being deleted, or null
     */
    void purgePlace(Long placeId, Long actorId, String changeNote);

    /**
     * Deletes a source, refusing while anything cites it or a scan still hangs off it.
     *
     * @param sourceId the source to delete
     * @param actorId id of the member making the change
     * @param changeNote why the source is being deleted, or null
     */
    void purgeSource(Long sourceId, Long actorId, String changeNote);

    /**
     * Deletes a person's grave record together with the photos and citations that name it.
     *
     * @param personId whose grave it is
     * @param actorId id of the member making the change
     * @param changeNote why the grave is being removed, or null
     */
    void purgeGrave(Long personId, Long actorId, String changeNote);

    /**
     * Deletes every row that names a union by id, for a union that is about to go or has just been folded away.
     *
     * @param familyId the union
     * @param actorId id of the member making the change
     * @param changeNote why, recorded on every row deleted
     * @return what was deleted
     */
    // One place for the list of tables that point at a union with no foreign key, so a fifth cannot be missed.
    RowsForgotten forgetUnionRows(Long familyId, Long actorId, String changeNote);

    /**
     * Deletes every row that names a grave by id, for a grave that is about to go or has just been dropped.
     *
     * @param graveId the grave
     * @param actorId id of the member making the change
     * @param changeNote why, recorded on every row deleted
     * @return what was deleted
     */
    RowsForgotten forgetGraveRows(Long graveId, Long actorId, String changeNote);
}
