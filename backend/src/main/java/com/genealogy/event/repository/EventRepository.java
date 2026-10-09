package com.genealogy.event.repository;

import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.event.domain.Event;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for events. */
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Lists the events of one subject, oldest first.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @return the events
     */
    List<Event> findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(
            EventSubjectType subjectType, Long subjectId);

    /**
     * Lists the ids of one subject's events, with no other column loaded.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @return the event ids
     */
    @Query("SELECT e.id FROM Event e WHERE e.subjectType = :subjectType AND e.subjectId = :subjectId")
    List<Long> findIdsBySubject(
            @Param("subjectType") EventSubjectType subjectType, @Param("subjectId") Long subjectId);

    /**
     * Counts the events recorded at one place.
     *
     * @param placeId the place id
     * @return how many events name it
     */
    long countByPlaceId(Long placeId);

    /**
     * Loads every death that carries both a day and a month, which a ngày giỗ needs.
     *
     * @param type the event type to match, always DEATH here
     * @return the events with both a day and a month recorded
     */
    @Query("""
            SELECT e FROM Event e
            WHERE e.type = :type
              AND e.date.day IS NOT NULL
              AND e.date.month IS NOT NULL
            """)
    List<Event> findDatedEventsOfType(@Param("type") EventType type);

    /**
     * Finds the people whose events match a type, a recorded-year range and a set of places (F20).
     *
     * @param type the event type to match, or null for any
     * @param yearFrom earliest recorded year, or null for no lower bound
     * @param yearTo latest recorded year, or null for no upper bound
     * @param placeIds the places to match, or null for any
     * @param byPlace whether the place filter applies, since an empty list is not valid SQL
     * @return the ids of the people whose events match
     */
    // The recorded year, not sort_date: "khoảng 1890" is a 1890 birth to whoever is searching (§3.2).
    @Query("""
            SELECT DISTINCT e.subjectId FROM Event e
            WHERE e.subjectType = com.genealogy.common.model.EventSubjectType.PERSON
              AND (:type IS NULL OR e.type = :type)
              AND (:yearFrom IS NULL OR (e.date.year IS NOT NULL AND e.date.year >= :yearFrom))
              AND (:yearTo IS NULL OR (e.date.year IS NOT NULL AND e.date.year <= :yearTo))
              AND (:byPlace = FALSE OR e.placeId IN :placeIds)
            """)
    List<Long> findPersonIdsMatching(
            @Param("type") EventType type,
            @Param("yearFrom") Integer yearFrom,
            @Param("yearTo") Integer yearTo,
            @Param("placeIds") Collection<Long> placeIds,
            @Param("byPlace") boolean byPlace);

    /**
     * Lists one subject's events of the given types.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @param types the event types to include
     * @return the matching events
     */
    List<Event> findBySubjectTypeAndSubjectIdAndTypeIn(
            EventSubjectType subjectType, Long subjectId, Collection<EventType> types);

    /**
     * Loads every event of the given types for every person, for a whole-clan recompute.
     *
     * @param types the event types to include
     * @return the matching events
     */
    @Query("SELECT e FROM Event e WHERE e.subjectType = com.genealogy.common.model.EventSubjectType.PERSON"
            + " AND e.type IN :types")
    List<Event> findPersonEventsOfTypes(@Param("types") Collection<EventType> types);

    /**
     * Loads every event that carries a year.
     *
     * @return the dated events
     */
    @Query("SELECT e FROM Event e WHERE e.date.year IS NOT NULL")
    List<Event> findAllDated();

    /**
     * Finds which of a batch of people have at least one event of the given types.
     *
     * @param personIds the people to check, never empty
     * @param types the event types to look for
     * @return the ids of the people who have one
     */
    @Query("""
            SELECT DISTINCT e.subjectId FROM Event e
            WHERE e.subjectType = com.genealogy.common.model.EventSubjectType.PERSON
              AND e.subjectId IN :personIds
              AND e.type IN :types
            """)
    List<Long> findPersonIdsWithEventTypes(
            @Param("personIds") Collection<Long> personIds, @Param("types") Collection<EventType> types);
}
