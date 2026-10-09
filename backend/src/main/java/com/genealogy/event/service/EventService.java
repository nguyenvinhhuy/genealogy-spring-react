package com.genealogy.event.service;

import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Role;
import com.genealogy.event.dto.request.EventRequest;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.event.dto.response.AnniversaryResponse;
import com.genealogy.event.dto.response.EventFactResponse;
import com.genealogy.event.dto.response.EventResponse;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Event operations. */
public interface EventService {

    /**
     * Lists the events of one subject, oldest first, hiding a living person's from callers below EDITOR.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @param role the calling member's access level
     * @return the events, or nothing when the subject is a living person the caller may not see (§3.6)
     */
    List<EventResponse> findBySubject(EventSubjectType subjectType, Long subjectId, Role role);

    /**
     * Lists the ids of one subject's events, for a caller that needs to forget rows keyed on them.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @return the event ids
     */
    List<Long> findIdsBySubject(EventSubjectType subjectType, Long subjectId);

    /**
     * Returns one event, provided the caller may see its subject (CLAUDE.md §3.6).
     *
     * @param id event id
     * @param role the calling member's access level
     * @return the event
     */
    EventResponse getById(Long id, Role role);

    /**
     * Reports whether an event's subject involves anyone still treated as living, failing closed for an unknown event.
     *
     * @param id event id
     * @return true when the person or either partner lives, and true as well when there is no such event
     */
    // The one event guard (§9), shaped like FamilyService.involvesLiving: a caller asks about living, not about a role.
    boolean involvesLiving(Long id);

    /**
     * Reports whether an event exists.
     *
     * @param id event id
     * @return true if it exists
     */
    boolean exists(Long id);

    /**
     * Counts the events recorded at one place.
     *
     * @param placeId the place id
     * @return how many events name it
     */
    long countByPlace(Long placeId);

    /**
     * Creates an event.
     *
     * @param request the event to create
     * @param actorId the member making the change, may be null for a system change
     * @return the created event
     */
    EventResponse create(EventRequest request, Long actorId);

    /**
     * Updates an event.
     *
     * @param id event id
     * @param request the new values
     * @param actorId the member making the change, may be null for a system change
     * @return the updated event
     */
    EventResponse update(Long id, EventRequest request, Long actorId);

    /**
     * Deletes an event.
     *
     * @param id event id
     * @param actorId the member making the change, may be null for a system change
     * @param changeNote why the event was deleted, or null
     */
    void delete(Long id, Long actorId, String changeNote);

    /**
     * Lists the ngày giỗ falling within a window, soonest first.
     *
     * @param from the first day to consider
     * @param days how many days ahead to look
     * @return the upcoming anniversaries
     */
    List<AnniversaryResponse> upcomingAnniversaries(LocalDate from, int days);

    /**
     * Loads every dated event as a flat projection.
     *
     * @return every event that carries a year
     */
    List<EventFactResponse> findAllFacts();

    /**
     * Finds which of a batch of people have a recorded death or burial, in one query.
     *
     * @param personIds the people to check
     * @return the ids of those recorded as dead
     */
    Set<Long> findRecordedDead(Collection<Long> personIds);

    /**
     * Recomputes every person's living flag, for the lifespan rule that moves with the calendar (§3.4).
     *
     * @return how many flags changed
     */
    int recomputeAllLiving();

    /**
     * Writes a proposed date out the way it will read once it is stored, defaults for an omitted modifier included.
     *
     * @param date the date as a request carries it
     * @return the Vietnamese text
     */
    String renderDate(GenealogyDateRequest date);

    /**
     * Loads every event in full.
     *
     * @return every event, ordered by subject then id so an export is reproducible
     */
    List<EventResponse> findAll();

    /**
     * Moves every event off one union onto another, for a merge that folded the two together.
     *
     * @param fromFamilyId the union being folded away
     * @param toFamilyId the union being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason, recorded on every event it moves
     * @return how many events were repointed
     */
    int reassignFamily(Long fromFamilyId, Long toFamilyId, Long actorId, String changeNote);

    /**
     * Deletes the events of a subject that is itself being deleted, recording each one.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @param actorId the member deleting the subject
     * @param changeNote why the subject is being deleted, recorded on every event deleted with it
     * @return how many events were deleted
     */
    int forgetSubject(EventSubjectType subjectType, Long subjectId, Long actorId, String changeNote);

    /**
     * Moves every event off one person onto another, for a merge (F11).
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason, recorded on every event it moves
     * @return how many events were repointed
     */
    int reassignPerson(Long fromId, Long toId, Long actorId, String changeNote);

    /**
     * Finds the people whose recorded events match a date range or a place.
     *
     * @param type which event to look at, or null for any of them
     * @param yearFrom earliest year, or null for no lower bound
     * @param yearTo latest year, or null for no upper bound
     * @param placeIds the places to accept, or null to ignore where it happened
     * @return the ids of the people whose event matches
     */
    Set<Long> findPersonIdsByEvent(EventType type, Integer yearFrom, Integer yearTo, Set<Long> placeIds);
}
