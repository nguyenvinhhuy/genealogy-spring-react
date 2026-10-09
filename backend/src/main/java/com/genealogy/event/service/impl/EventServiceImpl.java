package com.genealogy.event.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.CalendarType;
import com.genealogy.common.model.DateModifier;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.GenealogyDate;
import com.genealogy.common.model.GenealogyDateText;
import com.genealogy.common.model.Role;
import com.genealogy.common.util.LunarCalendar;
import com.genealogy.common.util.LunarDate;
import com.genealogy.common.util.StaleEdit;
import com.genealogy.common.util.VietnamTime;
import com.genealogy.event.domain.Event;
import com.genealogy.event.dto.request.EventRequest;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.event.dto.response.AnniversaryResponse;
import com.genealogy.event.dto.response.EventFactResponse;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.event.mapper.EventMapper;
import com.genealogy.event.repository.EventRepository;
import com.genealogy.event.service.EventService;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveChanged;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link EventService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private static final AuditEntityType ENTITY_TYPE = AuditEntityType.EVENT;
    // The events `living` is derived from (§3.4): a burial or a cải táng is as sure a sign of death as a death.
    private static final List<EventType> LIFE_EVENTS =
            List.of(EventType.BIRTH, EventType.DEATH, EventType.BURIAL, EventType.REBURIAL);
    private static final List<EventType> DEATH_EVENTS =
            List.of(EventType.DEATH, EventType.BURIAL, EventType.REBURIAL);
    // A BEFORE, AFTER or BETWEEN death day is only a bound; reminding the family of it would invent a giỗ (§3.2).
    private static final Set<DateModifier> DAY_IS_THE_DAY =
            Set.of(DateModifier.EXACT, DateModifier.ABOUT, DateModifier.ESTIMATED, DateModifier.CALCULATED);
    private static final int LUNAR_MONTH_DAYS = 30;

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final AuditService auditService;
    // Cross-feature by interface only (CLAUDE.md §4): no other feature's entity is imported here.
    private final PersonService personService;
    private final PlaceService placeService;
    private final FamilyService familyService;
    private final GraveService graveService;

    /**
     * Lists the events of one subject, oldest first, hiding a living person's from callers below EDITOR.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @param role the calling member's access level
     * @return the events, or nothing when the subject is a living person the caller may not see
     */
    @Override
    public List<EventResponse> findBySubject(EventSubjectType subjectType, Long subjectId, Role role) {
        // Redacting the person but serving their birth date next door would make §3.6 decorative.
        if (!Role.maySeeLivingDetails(role) && subjectIsLiving(subjectType, subjectId)) {
            return List.of();
        }
        return eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(subjectType, subjectId).stream()
                .map(eventMapper::toResponse)
                .toList();
    }

    /**
     * Lists the ids of one subject's events, for a caller that needs to forget rows keyed on them.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @return the event ids
     */
    @Override
    public List<Long> findIdsBySubject(EventSubjectType subjectType, Long subjectId) {
        return eventRepository.findIdsBySubject(subjectType, subjectId);
    }

    /**
     * Moves every event off one union onto another, for a merge that folded the two together.
     *
     * @param fromFamilyId the union being folded away
     * @param toFamilyId the union being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason, recorded on every event it moves
     * @return how many events were repointed
     */
    @Override
    @Transactional
    public int reassignFamily(Long fromFamilyId, Long toFamilyId, Long actorId, String changeNote) {
        // `events.subject_id` has no FK, so a folded union's ngày cưới would otherwise point at nothing.
        List<Event> events = eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(
                EventSubjectType.FAMILY, fromFamilyId);
        repoint(events, toFamilyId, actorId, changeNote);
        return events.size();
    }

    /**
     * Deletes the events of a subject that is itself being deleted, recording each one.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @param actorId the member deleting the subject
     * @param changeNote why the subject is being deleted, recorded on every event deleted with it
     * @return how many events were deleted
     */
    @Override
    @Transactional
    public int forgetSubject(EventSubjectType subjectType, Long subjectId, Long actorId, String changeNote) {
        List<Event> events =
                eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(subjectType, subjectId);
        List<EventResponse> before = events.stream().map(eventMapper::toResponse).toList();
        eventRepository.deleteAll(events);
        eventRepository.flush();
        // Until 2026-09-24 a purge deleted cụ's ngày mất with no revision at all: the trail must outlive it (§3.8).
        before.forEach(event ->
                auditService.record(ENTITY_TYPE, event.id(), AuditAction.DELETE, event, null, actorId, changeNote));
        return events.size();
    }

    /**
     * Moves events onto another subject of the same kind and records each move.
     *
     * @param events the events to move
     * @param toSubjectId the subject they now belong to
     * @param actorId the member making the change
     * @param changeNote why they are moving
     */
    private void repoint(List<Event> events, Long toSubjectId, Long actorId, String changeNote) {
        List<EventResponse> before = events.stream().map(eventMapper::toResponse).toList();
        events.forEach(event -> event.setSubjectId(toSubjectId));
        eventRepository.flush();
        for (int at = 0; at < events.size(); at++) {
            EventResponse was = before.get(at);
            auditService.record(ENTITY_TYPE, was.id(), AuditAction.UPDATE, was,
                    eventMapper.toResponse(events.get(at)), actorId, changeNote);
        }
    }

    /**
     * Reports whether an event's subject involves anyone still treated as living, failing closed for an unknown event.
     *
     * @param id event id
     * @return true when the person or either partner lives, and true as well when there is no such event
     */
    @Override
    public boolean involvesLiving(Long id) {
        return eventRepository.findById(id)
                .map(event -> subjectIsLiving(event.getSubjectType(), event.getSubjectId()))
                .orElse(true);
    }

    /**
     * Reports whether an event exists.
     *
     * @param id event id
     * @return true if it exists
     */
    @Override
    public boolean exists(Long id) {
        return eventRepository.existsById(id);
    }

    /**
     * Counts the events recorded at one place.
     *
     * @param placeId the place id
     * @return how many events name it
     */
    @Override
    public long countByPlace(Long placeId) {
        return eventRepository.countByPlaceId(placeId);
    }

    /**
     * Reports whether an event's subject involves anyone still treated as living.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @return true when the person, or either partner of the union, is living
     */
    private boolean subjectIsLiving(EventSubjectType subjectType, Long subjectId) {
        return subjectType == EventSubjectType.PERSON
                ? personService.isLiving(subjectId)
                : familyService.involvesLiving(subjectId);
    }

    /**
     * Returns one event, provided the caller may see its subject.
     *
     * @param id event id
     * @param role the calling member's access level
     * @return the event
     */
    @Override
    public EventResponse getById(Long id, Role role) {
        Event event = require(id);
        // Answering "no such event" rather than refusing: a refusal would confirm the event exists (§3.6).
        if (!Role.maySeeLivingDetails(role) && subjectIsLiving(event.getSubjectType(), event.getSubjectId())) {
            throw new NotFoundException("Không có sự kiện với id " + id);
        }
        return eventMapper.toResponse(event);
    }

    /**
     * Creates an event.
     *
     * @param request the event to create
     * @param actorId the member making the change, may be null for a system change
     * @return the created event
     */
    @Override
    @Transactional
    public EventResponse create(EventRequest request, Long actorId) {
        validate(request);

        Event event = new Event();
        applyRequest(event, request);
        Event saved = eventRepository.save(event);

        recomputeLiving(saved);
        EventResponse created = eventMapper.toResponse(saved);
        auditService.record(
                ENTITY_TYPE, created.id(), AuditAction.CREATE, null, created, actorId, request.changeNote());
        return created;
    }

    /**
     * Updates an event.
     *
     * @param id event id
     * @param request the new values
     * @param actorId the member making the change, may be null for a system change
     * @return the updated event
     */
    @Override
    @Transactional
    public EventResponse update(Long id, EventRequest request, Long actorId) {
        validate(request);

        Event event = require(id);
        StaleEdit.refuseIfStale(request.version(), event.getVersion(), AuditEntityType.EVENT);
        EventResponse before = eventMapper.toResponse(event);
        EventSubjectType formerType = event.getSubjectType();
        Long formerSubject = event.getSubjectId();
        applyRequest(event, request);

        recomputeLiving(event);
        // A death moved to the right person also stops being the wrong one's, or they stay "dead" and exposed.
        boolean moved = formerType != event.getSubjectType() || !Objects.equals(formerSubject, event.getSubjectId());
        if (moved && formerType == EventSubjectType.PERSON && formerSubject != null) {
            recomputeLiving(formerSubject);
        }
        // Flushed so the response carries the bumped version, or the form's next save is refused as stale.
        eventRepository.flush();
        EventResponse after = eventMapper.toResponse(event);
        auditService.record(ENTITY_TYPE, id, AuditAction.UPDATE, before, after, actorId, request.changeNote());
        return after;
    }

    /**
     * Deletes an event.
     *
     * @param id event id
     * @param actorId the member making the change, may be null for a system change
     * @param changeNote why the event was deleted, or null
     */
    @Override
    @Transactional
    public void delete(Long id, Long actorId, String changeNote) {
        Event event = require(id);
        EventResponse before = eventMapper.toResponse(event);
        eventRepository.delete(event);
        eventRepository.flush();
        recomputeLiving(event);
        // The trail outlives its subject (§3.8): a deleted ngày giỗ keeps the date it used to name.
        auditService.record(ENTITY_TYPE, id, AuditAction.DELETE, before, null, actorId, changeNote);
    }

    /**
     * Lists the ngày giỗ falling within a window, soonest first.
     *
     * @param from the first day to consider
     * @param days how many days ahead to look
     * @return the upcoming anniversaries
     */
    @Override
    public List<AnniversaryResponse> upcomingAnniversaries(LocalDate from, int days) {
        LocalDate until = from.plusDays(days);
        List<Event> deaths = eventRepository.findDatedEventsOfType(EventType.DEATH).stream()
                .filter(death -> DAY_IS_THE_DAY.contains(death.getDate().getModifier()))
                .toList();

        Map<Long, String> nameById = personService
                .findNodes(deaths.stream().map(Event::getSubjectId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(PersonNodeResponse::id, PersonNodeResponse::displayName));

        return deaths.stream()
                .map(event -> toAnniversary(event, from, nameById))
                .filter(Objects::nonNull)
                .filter(anniversary -> !anniversary.nextOccurrence().isAfter(until))
                .sorted(Comparator.comparing(AnniversaryResponse::nextOccurrence))
                .toList();
    }

    /**
     * Loads every dated event as a flat projection, for whole-graph analysis.
     *
     * @return every event that carries a year
     */
    @Override
    public List<EventFactResponse> findAllFacts() {
        // Filtered in the query: an undated fact can prove nothing, and loading it cost a row per event.
        return eventRepository.findAllDated().stream().map(eventMapper::toFact).toList();
    }

    /**
     * Finds which of a batch of people have a recorded death, burial, cải táng or mộ, in two queries.
     *
     * @param personIds the people to check
     * @return the ids of those recorded as dead
     */
    @Override
    public Set<Long> findRecordedDead(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> dead = new HashSet<>(eventRepository.findPersonIdsWithEventTypes(personIds, DEATH_EVENTS));
        // A recorded mộ is a recorded death (§8.8 D2); a sinh phần is not, and findBuried leaves it out.
        dead.addAll(graveService.findBuried(personIds));
        return dead;
    }

    /**
     * Loads every event in full.
     *
     * @return every event, ordered by subject then id so an export is reproducible
     */
    @Override
    public List<EventResponse> findAll() {
        return eventRepository.findAll(Sort.by("subjectType", "subjectId", "id")).stream()
                .map(eventMapper::toResponse)
                .toList();
    }

    /**
     * Moves every event off one person onto another, for a merge.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason, recorded on every event it moves
     * @return how many events were repointed
     */
    @Override
    @Transactional
    public int reassignPerson(Long fromId, Long toId, Long actorId, String changeNote) {
        List<Event> events =
                eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(EventSubjectType.PERSON, fromId);
        // Kept, not deduplicated: two birth dates is a contradiction for `quality`, not one for a merge.
        repoint(events, toId, actorId, changeNote);
        // The duplicate's death date now belongs to the survivor, and `living` is derived from it (§3.4).
        if (!events.isEmpty()) {
            recomputeLiving(toId);
        }
        return events.size();
    }

    /**
     * Finds the people whose recorded events match a date range or a place.
     *
     * @param type which event to look at, or null for any of them
     * @param yearFrom earliest year, or null for no lower bound
     * @param yearTo latest year, or null for no upper bound
     * @param placeIds the places to accept, or null to ignore where it happened
     * @return the ids of the people whose event matches
     */
    @Override
    public Set<Long> findPersonIdsByEvent(
            EventType type, Integer yearFrom, Integer yearTo, Set<Long> placeIds) {
        // In SQL, not a stream over findAll(): three filters used to read the whole events table three times.
        boolean byPlace = placeIds != null;
        return Set.copyOf(eventRepository.findPersonIdsMatching(
                type, yearFrom, yearTo, byPlace ? placeIds : List.of(-1L), byPlace));
    }

    /**
     * Works out when one death's anniversary next falls.
     *
     * @param event the death event
     * @param from the first day to consider
     * @param nameById the display name of each person
     * @return the anniversary, or null when the person is gone or the date cannot be resolved
     */
    private AnniversaryResponse toAnniversary(Event event, LocalDate from, Map<Long, String> nameById) {
        String name = nameById.get(event.getSubjectId());
        if (name == null) {
            return null;
        }
        GenealogyDate date = event.getDate();

        // A death recorded on the solar calendar still has a lunar giỗ; the family keeps it by âm lịch.
        LunarDate lunar = date.getCalendar() == CalendarType.LUNAR
                ? new LunarDate(
                        date.getDay(), date.getMonth(), date.getYear() == null ? 0 : date.getYear(), date.isLeapMonth())
                : toLunarFromSolar(date);
        if (lunar == null) {
            return null;
        }

        // A leap-month death is kept in the ordinary month of the same number, which every year has.
        LocalDate next = LunarCalendar.nextAnniversary(lunar.day(), lunar.month(), from);
        Integer yearsSince = lunar.year() > 0 ? LunarCalendar.toLunar(next).year() - lunar.year() : null;

        return new AnniversaryResponse(
                event.getId(),
                event.getSubjectId(),
                name,
                lunar.day(),
                lunar.month(),
                lunar.leapMonth(),
                date.getModifier() != DateModifier.EXACT,
                next,
                ChronoUnit.DAYS.between(from, next),
                yearsSince);
    }

    /**
     * Reads a solar-recorded death date as its lunar equivalent.
     *
     * @param date the recorded date
     * @return the lunar date, or null when the year is missing so no exact day can be located
     */
    private static LunarDate toLunarFromSolar(GenealogyDate date) {
        if (date.getYear() == null) {
            return null;
        }
        // Clamped like sort_date (§3.2): 31 February is in real records, and it is still somebody's giỗ.
        LocalDate firstOfMonth = LocalDate.of(date.getYear(), date.getMonth(), 1);
        int day = Math.min(date.getDay(), firstOfMonth.lengthOfMonth());
        return LunarCalendar.toLunar(firstOfMonth.withDayOfMonth(day));
    }

    /**
     * Writes a proposed date out the way it will read once it is stored, defaults for an omitted modifier included.
     *
     * @param date the date as a request carries it
     * @return the Vietnamese text
     */
    @Override
    public String renderDate(GenealogyDateRequest date) {
        return GenealogyDateText.of(datedFrom(date));
    }

    /**
     * Builds the stored date a request describes, with the defaults an omitted modifier or calendar takes.
     *
     * @param request the date as a request carries it, or null for no date
     * @return the embeddable value, its sort key not yet derived
     */
    private GenealogyDate datedFrom(GenealogyDateRequest request) {
        GenealogyDate date = request == null ? new GenealogyDate() : eventMapper.toDate(request);
        // MapStruct writes every mapped field, so an omitted modifier/calendar nulls a NOT NULL column.
        if (date.getModifier() == null) {
            date.setModifier(DateModifier.EXACT);
        }
        if (date.getCalendar() == null) {
            date.setCalendar(CalendarType.SOLAR);
        }
        return date;
    }

    /**
     * Copies a request onto an event entity, deriving the date's sort key.
     *
     * @param event the entity to update
     * @param request the inbound payload
     */
    private void applyRequest(Event event, EventRequest request) {
        event.setSubjectType(request.type().subjectType());
        event.setSubjectId(request.subjectId());
        event.setType(request.type());
        event.setPlaceId(request.placeId());
        event.setDescription(request.description());

        GenealogyDate date = datedFrom(request.date());
        // The sort key is derived here rather than on persist, so the rule stays testable (CLAUDE.md 3.2).
        date.deriveSortDate();
        event.setDate(date);
    }

    /**
     * Rejects an event whose subject or place does not exist.
     *
     * @param request the inbound payload
     */
    private void validate(EventRequest request) {
        if (request.type().subjectType() == EventSubjectType.PERSON
                && !personService.exists(request.subjectId())) {
            throw new BadRequestException("Không có người với id " + request.subjectId());
        }
        if (request.type().subjectType() == EventSubjectType.FAMILY && !familyService.exists(request.subjectId())) {
            throw new BadRequestException("Không có quan hệ vợ chồng với id " + request.subjectId());
        }
        placeService.requireExists(request.placeId());
        if (request.date() != null) {
            validateDate(request.date());
        }
    }

    /**
     * Rejects a date whose parts do not describe any date, before the database or the sort key trips on it.
     *
     * @param date the inbound date
     */
    private static void validateDate(GenealogyDateRequest date) {
        boolean lunar = date.calendar() == CalendarType.LUNAR;
        // A lunar day and month with no year is a giỗ the family keeps without knowing the year (§8.10 D1).
        if (date.year() == null && (date.month() != null || date.day() != null) && !lunar) {
            throw new BadRequestException("Ngày dương lịch có tháng hoặc ngày thì phải có năm");
        }
        if (date.year() == null && date.modifier() == DateModifier.BETWEEN) {
            throw new BadRequestException("Một khoảng thời gian cần năm bắt đầu");
        }
        if (date.day() != null && date.month() == null) {
            throw new BadRequestException("Có ngày thì phải có tháng");
        }
        boolean range = date.modifier() == DateModifier.BETWEEN;
        if (range && date.year2() == null) {
            throw new BadRequestException("Một khoảng thời gian cần năm kết thúc");
        }
        if (!range && (date.year2() != null || date.month2() != null || date.day2() != null)) {
            throw new BadRequestException("Chỉ khoảng thời gian mới có mốc thứ hai");
        }
        if (date.day2() != null && date.month2() == null) {
            throw new BadRequestException("Có ngày thì phải có tháng");
        }
        if (Boolean.TRUE.equals(date.leapMonth()) && (!lunar || date.month() == null)) {
            throw new BadRequestException("Tháng nhuận chỉ có ở một tháng âm lịch");
        }
        if (Boolean.TRUE.equals(date.leapMonth2()) && (!lunar || date.month2() == null)) {
            throw new BadRequestException("Tháng nhuận chỉ có ở một tháng âm lịch");
        }
        if (lunar && (isOver(date.day(), LUNAR_MONTH_DAYS) || isOver(date.day2(), LUNAR_MONTH_DAYS))) {
            throw new BadRequestException("Tháng âm lịch có nhiều nhất 30 ngày");
        }
    }

    /**
     * Reports whether a nullable number exceeds a limit.
     *
     * @param value the number, or null
     * @param limit the largest allowed value
     * @return true only when the value is present and too large
     */
    private static boolean isOver(Integer value, int limit) {
        return value != null && value > limit;
    }

    /**
     * Recomputes the living flag of the person an event belongs to.
     *
     * @param event the event that changed
     */
    private void recomputeLiving(Event event) {
        if (event.getSubjectType() != EventSubjectType.PERSON) {
            return;
        }
        recomputeLiving(event.getSubjectId());
    }

    /**
     * Recomputes one person's living flag from the events now recorded against them.
     *
     * @param personId the person to recompute
     */
    private void recomputeLiving(Long personId) {
        List<Event> events = eventRepository.findBySubjectTypeAndSubjectIdAndTypeIn(
                EventSubjectType.PERSON, personId, LIFE_EVENTS);
        boolean buried = !graveService.findBuried(List.of(personId)).isEmpty();
        personService.applyLiving(personId, livingFrom(events, buried, VietnamTime.today()));
    }

    /**
     * Recomputes the living flag of a person whose grave was recorded, changed, moved or removed.
     *
     * @param changed which person's grave changed
     */
    @EventListener
    @Transactional
    public void onGraveChanged(GraveChanged changed) {
        // Published by `grave`, which cannot call `event` back without closing a bean cycle (§8.8).
        if (personService.exists(changed.personId())) {
            recomputeLiving(changed.personId());
        }
    }

    /**
     * Recomputes every person's living flag, for the lifespan rule that moves with the calendar.
     *
     * @return how many flags changed
     */
    @Override
    @Transactional
    public int recomputeAllLiving() {
        // "Born over a century ago" turns true on a date nobody edits, so nothing but a sweep ever catches it.
        Map<Long, List<Event>> byPerson = eventRepository.findPersonEventsOfTypes(LIFE_EVENTS).stream()
                .filter(event -> event.getSubjectId() != null)
                .collect(Collectors.groupingBy(Event::getSubjectId));
        Set<Long> buried = graveService.findAllBuried();
        LocalDate today = VietnamTime.today();
        int changed = 0;
        for (Map.Entry<Long, Boolean> person : personService.findAllLiving().entrySet()) {
            boolean living = livingFrom(
                    byPerson.getOrDefault(person.getKey(), List.of()), buried.contains(person.getKey()), today);
            if (living != person.getValue()) {
                personService.applyLiving(person.getKey(), living);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Derives the living flag from one person's life events and whether a mộ is recorded for them.
     *
     * @param events their BIRTH, DEATH, BURIAL and REBURIAL events
     * @param buried whether a mộ (not a sinh phần) is recorded for them
     * @param today the current date in Vietnam
     * @return whether they are treated as living
     */
    private static boolean livingFrom(List<Event> events, boolean buried, LocalDate today) {
        boolean dead = buried || events.stream().anyMatch(event -> event.getType() != EventType.BIRTH);
        List<GenealogyDate> births = events.stream()
                .filter(event -> event.getType() == EventType.BIRTH)
                .map(Event::getDate)
                .toList();
        return LivingRule.isLiving(dead, births, today);
    }

    /**
     * Loads an event or fails.
     *
     * @param id event id
     * @return the entity
     */
    private Event require(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có sự kiện với id " + id));
    }
}
