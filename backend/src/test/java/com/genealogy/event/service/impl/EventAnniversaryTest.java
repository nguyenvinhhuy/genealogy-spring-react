package com.genealogy.event.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.model.CalendarType;
import com.genealogy.common.model.DateModifier;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.util.LunarCalendar;
import com.genealogy.common.util.LunarDate;
import com.genealogy.event.domain.Event;
import com.genealogy.event.dto.response.AnniversaryResponse;
import com.genealogy.event.mapper.EventMapper;
import com.genealogy.event.repository.EventRepository;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the ngày giỗ list, which every role may read (CLAUDE.md §3.3, §3.6). */
@ExtendWith(MockitoExtension.class)
class EventAnniversaryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 24);
    private static final int A_YEAR = 400;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private PersonService personService;

    @Mock
    private PlaceService placeService;

    @Mock
    private FamilyService familyService;

    @Mock
    private GraveService graveService;

    @Mock
    private AuditService auditService;

    private EventServiceImpl events;
    private long nextId;

    @BeforeEach
    void setUp() {
        events = new EventServiceImpl(
                eventRepository, Mappers.getMapper(EventMapper.class), auditService,
                personService, placeService, familyService, graveService);
        lenient().when(personService.findNodes(any())).thenAnswer(call -> ((Collection<?>) call.getArgument(0))
                .stream()
                .map(id -> new PersonNodeResponse((Long) id, "Cụ " + id, Gender.MALE, 1, false))
                .toList());
    }

    /**
     * Builds one recorded death.
     *
     * @param modifier how precisely the day is known
     * @param calendar which calendar the parts are in
     * @param y year
     * @param m month
     * @param d day
     * @param leap whether the month is the leap month
     * @return the event
     */
    private Event death(DateModifier modifier, CalendarType calendar, int y, int m, int d, boolean leap) {
        Event event = new Event();
        event.setId(++nextId);
        event.setSubjectType(EventSubjectType.PERSON);
        event.setSubjectId(100 + nextId);
        event.setType(EventType.DEATH);
        event.getDate().setModifier(modifier);
        event.getDate().setCalendar(calendar);
        event.getDate().setYear(y);
        event.getDate().setMonth(m);
        event.getDate().setDay(d);
        event.getDate().setLeapMonth(leap);
        return event;
    }

    /**
     * Lists the anniversaries of the given deaths over the next year.
     *
     * @param deaths the recorded deaths
     * @return the anniversaries
     */
    private List<AnniversaryResponse> anniversariesOf(Event... deaths) {
        when(eventRepository.findDatedEventsOfType(EventType.DEATH)).thenReturn(List.of(deaths));
        return events.upcomingAnniversaries(FROM, A_YEAR);
    }

    @Test
    @DisplayName("a lunar death gives its giỗ on the next day of that lunar date, with the năm thứ")
    void lunarDeathGivesItsAnniversary() {
        List<AnniversaryResponse> found =
                anniversariesOf(death(DateModifier.EXACT, CalendarType.LUNAR, 1950, 3, 10, false));

        assertThat(found).singleElement().satisfies(anniversary -> {
            assertThat(anniversary.lunarDay()).isEqualTo(10);
            assertThat(anniversary.lunarMonth()).isEqualTo(3);
            assertThat(anniversary.nextOccurrence()).isEqualTo(LunarCalendar.nextAnniversary(10, 3, FROM));
            assertThat(anniversary.yearsSince())
                    .isEqualTo(LunarCalendar.toLunar(anniversary.nextOccurrence()).year() - 1950);
            assertThat(anniversary.approximate()).isFalse();
        });
    }

    @Test
    @DisplayName("a giỗ known only by lunar day and month is reminded every year, with no năm thứ")
    void lunarDeathWithoutAYearIsStillReminded() {
        Event founder = death(DateModifier.EXACT, CalendarType.LUNAR, 1, 3, 12, false);
        founder.getDate().setYear(null);

        // The thuỷ tổ's giỗ is the one date every branch keeps, and nobody knows the year (§8.10 D1).
        assertThat(anniversariesOf(founder)).singleElement().satisfies(anniversary -> {
            assertThat(anniversary.nextOccurrence()).isEqualTo(LunarCalendar.nextAnniversary(12, 3, FROM));
            assertThat(anniversary.yearsSince()).isNull();
        });
    }

    @Test
    @DisplayName("a solar death date is converted to its lunar day, because the family keeps giỗ by âm lịch")
    void solarDeathIsReadAsLunar() {
        List<AnniversaryResponse> found =
                anniversariesOf(death(DateModifier.EXACT, CalendarType.SOLAR, 1990, 1, 15, false));

        LunarDate lunar = LunarCalendar.toLunar(LocalDate.of(1990, 1, 15));
        assertThat(found).singleElement().satisfies(anniversary -> {
            assertThat(anniversary.lunarDay()).isEqualTo(lunar.day());
            assertThat(anniversary.lunarMonth()).isEqualTo(lunar.month());
        });
    }

    @Test
    @DisplayName("only a known day is reminded: 'trước', 'sau' and a range are bounds, not a giỗ")
    void boundsAreNotReminded() {
        List<AnniversaryResponse> found = anniversariesOf(
                death(DateModifier.BEFORE, CalendarType.LUNAR, 1950, 3, 10, false),
                death(DateModifier.AFTER, CalendarType.LUNAR, 1950, 4, 10, false),
                death(DateModifier.BETWEEN, CalendarType.LUNAR, 1950, 5, 10, false),
                death(DateModifier.ABOUT, CalendarType.LUNAR, 1950, 6, 10, false));

        // An approximate day is still the day the family keeps, so it stays, and says it is approximate.
        assertThat(found).singleElement().satisfies(anniversary -> {
            assertThat(anniversary.lunarMonth()).isEqualTo(6);
            assertThat(anniversary.approximate()).isTrue();
        });
    }

    @Test
    @DisplayName("a death in a tháng nhuận keeps the leap flag, and is reminded in the month every year has")
    void leapMonthDeathIsFlagged() {
        List<AnniversaryResponse> found =
                anniversariesOf(death(DateModifier.EXACT, CalendarType.LUNAR, 1995, 8, 26, true));

        assertThat(found).singleElement().satisfies(anniversary -> {
            assertThat(anniversary.leapMonth()).isTrue();
            assertThat(anniversary.nextOccurrence()).isEqualTo(LunarCalendar.nextAnniversary(26, 8, FROM));
        });
    }

    @Test
    @DisplayName("a death whose person no longer exists is left out rather than listed without a name")
    void deletedPersonIsLeftOut() {
        Event orphan = death(DateModifier.EXACT, CalendarType.LUNAR, 1950, 3, 10, false);
        // doReturn, not when(): re-stubbing through when() would run setUp's answer with a null argument.
        doReturn(List.of()).when(personService).findNodes(any());

        assertThat(anniversariesOf(orphan)).isEmpty();
    }

    @Test
    @DisplayName("an anniversary carries no field a MEMBER may not see: it names only people recorded as dead")
    void anniversaryShapeIsSafeForEveryRole() {
        // Read by every role; a birth date, a note or a chi joining this record would be read by every role too.
        assertThat(AnniversaryResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactlyInAnyOrder("eventId", "personId", "personName", "lunarDay", "lunarMonth",
                        "leapMonth", "approximate", "nextOccurrence", "daysUntil", "yearsSince");
    }
}
