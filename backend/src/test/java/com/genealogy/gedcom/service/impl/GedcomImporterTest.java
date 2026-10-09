package com.genealogy.gedcom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.branch.service.BranchService;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.GraveKind;
import com.genealogy.common.model.PlaceType;
import com.genealogy.common.model.SourceType;
import com.genealogy.event.dto.request.EventRequest;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.event.service.EventService;
import com.genealogy.family.service.FamilyService;
import com.genealogy.gedcom.domain.GedcomNode;
import com.genealogy.gedcom.dto.response.GedcomImportResponse;
import com.genealogy.grave.dto.request.GraveRequest;
import com.genealogy.grave.dto.response.GraveResponse;
import com.genealogy.grave.dto.response.GraveSaved;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.dto.request.PlacePath;
import com.genealogy.place.service.PlaceService;
import com.genealogy.source.dto.request.CitationRequest;
import com.genealogy.source.dto.request.SourceRequest;
import com.genealogy.source.dto.response.CitationResponse;
import com.genealogy.source.dto.response.SourceResponse;
import com.genealogy.source.service.SourceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** Unit tests for reading GEDCOM into the clan: chi, places, graves, cải táng and citations (§9). */
@ExtendWith(MockitoExtension.class)
class GedcomImporterTest {

    private static final Long ACTOR = 9L;
    private static final Long PERSON_ID = 1L;
    private static final Long EVENT_ID = 70L;
    private static final Long GRAVE_ID = 40L;

    @Mock
    private PersonService personService;

    @Mock
    private FamilyService familyService;

    @Mock
    private EventService eventService;

    @Mock
    private PlaceService placeService;

    @Mock
    private SourceService sourceService;

    @Mock
    private BranchService branchService;

    @Mock
    private GraveService graveService;

    private GedcomImporter importer;

    @BeforeEach
    void setUp() {
        importer = new GedcomImporter(personService, familyService, eventService, placeService, sourceService,
                branchService, graveService);
        lenient().when(personService.create(any(), any())).thenAnswer(call -> {
            PersonRequest request = call.getArgument(0);
            return new PersonDetailResponse(
                    PERSON_ID, "Người Test", Gender.MALE, null, request.branchId(), true, null, List.of(),
                    Instant.now(), 0);
        });
        lenient().when(eventService.create(any(), any())).thenAnswer(call -> {
            EventRequest request = call.getArgument(0);
            return new EventResponse(EVENT_ID, EventSubjectType.PERSON, request.subjectId(), request.type(), null,
                    request.placeId(), request.description(), 0);
        });
        lenient().when(sourceService.create(any(), any())).thenAnswer(call -> {
            SourceRequest request = call.getArgument(0);
            return new SourceResponse(3L, request.title(), request.type(), null, null, null, null, 0, 0);
        });
        lenient().when(sourceService.addCitation(any(), any())).thenReturn(
                new CitationResponse(1L, 3L, null, null, CitationTargetType.PERSON, PERSON_ID, null, null, 0));
        lenient().when(graveService.save(any(), any(), any())).thenReturn(new GraveSaved(
                new GraveResponse(GRAVE_ID, PERSON_ID, GraveKind.GRAVE, null, null, null, null, null, 0), true));
    }

    @Test
    @DisplayName("an INDI's chi line resolves the whole path, root-first, into a branch id")
    void resolvesTheBranchPath() {
        when(branchService.findOrCreatePath(List.of("Chi Hai", "Chi Hai - Phái Hai"), ACTOR))
                .thenReturn(Optional.of(42L));
        List<GedcomNode> records = GedcomNode.parse("""
                0 HEAD
                1 GEDC
                2 VERS 7.0
                0 @I1@ INDI
                1 NAME Nguyễn /Văn An/
                1 _BRANCH Chi Hai > Chi Hai - Phái Hai
                0 TRLR
                """);

        GedcomImportResponse result = importer.run(records, ACTOR);

        assertThat(result.personsCreated()).isEqualTo(1);
        ArgumentCaptor<PersonRequest> captor = ArgumentCaptor.forClass(PersonRequest.class);
        verify(personService).create(captor.capture(), any());
        assertThat(captor.getValue().branchId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("an INDI with no chi line is created with none, not a guessed one")
    void leavesTheBranchNullWhenUnrecorded() {
        List<GedcomNode> records = GedcomNode.parse("""
                0 HEAD
                1 GEDC
                2 VERS 7.0
                0 @I1@ INDI
                1 NAME Nguyễn /Văn An/
                0 TRLR
                """);

        importer.run(records, ACTOR);

        ArgumentCaptor<PersonRequest> captor = ArgumentCaptor.forClass(PersonRequest.class);
        verify(personService).create(captor.capture(), any());
        assertThat(captor.getValue().branchId()).isNull();
    }

    @Test
    @DisplayName("an EVEN typed REBURIAL is read back as a cải táng, not as an ordinary other event")
    void readsReburial() {
        List<GedcomNode> records = GedcomNode.parse("""
                0 @I1@ INDI
                1 NAME Nguyễn /Văn An/
                1 EVEN
                2 TYPE REBURIAL
                2 NOTE Cải táng về quê
                0 TRLR
                """);

        importer.run(records, ACTOR);

        ArgumentCaptor<EventRequest> captor = ArgumentCaptor.forClass(EventRequest.class);
        verify(eventService).create(captor.capture(), any());
        assertThat(captor.getValue().type()).isEqualTo(EventType.REBURIAL);
    }

    @Test
    @DisplayName("a PLAC's FORM gives each component its level, and its MAP the most specific place's coordinates")
    void readsPlaceLevelsAndCoordinates() {
        when(placeService.findOrCreatePath(any(), eq(ACTOR))).thenReturn(Optional.of(5L));
        List<GedcomNode> records = GedcomNode.parse("""
                0 @I1@ INDI
                1 NAME Nguyễn /Văn An/
                1 BIRT
                2 PLAC Xã A, Tỉnh C
                3 FORM WARD, PROVINCE
                3 MAP
                4 LATI N21.02
                4 LONG E105.8
                0 TRLR
                """);

        importer.run(records, ACTOR);

        ArgumentCaptor<PlacePath> captor = ArgumentCaptor.forClass(PlacePath.class);
        verify(placeService).findOrCreatePath(captor.capture(), eq(ACTOR));
        assertThat(captor.getValue().levels()).containsExactly(PlaceType.WARD, PlaceType.PROVINCE);
        assertThat(captor.getValue().latitude()).isEqualByComparingTo("21.02");
        assertThat(captor.getValue().longitude()).isEqualByComparingTo("105.8");
    }

    @Test
    @DisplayName("an event's own SOUR is cited against the event just created, not dropped")
    void readsEventCitations() {
        List<GedcomNode> records = GedcomNode.parse("""
                0 @S1@ SOUR
                1 TITL Gia phả cũ
                0 @I1@ INDI
                1 NAME Nguyễn /Văn An/
                1 DEAT Y
                2 SOUR @S1@
                3 PAGE trang 12
                0 TRLR
                """);

        GedcomImportResponse result = importer.run(records, ACTOR);

        ArgumentCaptor<CitationRequest> captor = ArgumentCaptor.forClass(CitationRequest.class);
        verify(sourceService).addCitation(captor.capture(), eq(ACTOR));
        assertThat(captor.getValue().targetType()).isEqualTo(CitationTargetType.EVENT);
        assertThat(captor.getValue().targetId()).isEqualTo(EVENT_ID);
        assertThat(captor.getValue().locator()).isEqualTo("trang 12");
        assertThat(result.dropped()).isZero();
    }

    @Test
    @DisplayName("a grave with no kind is read as a sinh phần, so a guess never makes its owner dead and public")
    void aGraveWithNoKindStaysPrivate() {
        List<GedcomNode> records = GedcomNode.parse("""
                0 @I1@ INDI
                1 NAME Nguyễn /Văn An/
                1 _GRAVE khu B
                0 TRLR
                """);

        importer.run(records, ACTOR);

        // A mộ counts as a death (§8.8 D2); read as one, the owner lost §3.6's privacy on a guess (§8.10 #1).
        ArgumentCaptor<GraveRequest> grave = ArgumentCaptor.forClass(GraveRequest.class);
        verify(graveService).save(eq(PERSON_ID), grave.capture(), eq(ACTOR));
        assertThat(grave.getValue().kind()).isEqualTo(GraveKind.LIVING_PLOT);
    }

    @Test
    @DisplayName("a grave extension is recorded with its kind, plot and coordinates, and its citations follow it")
    void readsTheGrave() {
        List<GedcomNode> records = GedcomNode.parse("""
                0 @S1@ SOUR
                1 TITL Bia mộ
                0 @I1@ INDI
                1 NAME Nguyễn /Văn An/
                1 _GRAVE khu B
                2 TYPE LIVING_PLOT
                2 MAP
                3 LATI S8.5
                3 LONG W100.25
                2 SOUR @S1@
                0 TRLR
                """);

        importer.run(records, ACTOR);

        ArgumentCaptor<GraveRequest> grave = ArgumentCaptor.forClass(GraveRequest.class);
        verify(graveService).save(eq(PERSON_ID), grave.capture(), eq(ACTOR));
        assertThat(grave.getValue().kind()).isEqualTo(GraveKind.LIVING_PLOT);
        assertThat(grave.getValue().plot()).isEqualTo("khu B");
        assertThat(grave.getValue().latitude()).isEqualByComparingTo("-8.5");
        assertThat(grave.getValue().longitude()).isEqualByComparingTo("-100.25");
        ArgumentCaptor<CitationRequest> citation = ArgumentCaptor.forClass(CitationRequest.class);
        verify(sourceService).addCitation(citation.capture(), eq(ACTOR));
        assertThat(citation.getValue().targetType()).isEqualTo(CitationTargetType.GRAVE);
        assertThat(citation.getValue().targetId()).isEqualTo(GRAVE_ID);
    }

    @Test
    @DisplayName("an over-long source title is cut to its column with a warning rather than failing the insert")
    void cutsAnOverlongTitle() {
        String title = "Gia phả ".repeat(60);
        List<GedcomNode> records = GedcomNode.parse("0 @S1@ SOUR\n1 TITL " + title + "\n1 TEXT Nội dung\n0 TRLR\n");

        GedcomImportResponse result = importer.run(records, ACTOR);

        ArgumentCaptor<SourceRequest> captor = ArgumentCaptor.forClass(SourceRequest.class);
        verify(sourceService).create(captor.capture(), eq(ACTOR));
        assertThat(captor.getValue().title()).hasSize(SourceRequest.MAX_TITLE);
        // TEXT is what the source says, so it lands in the notes, never in the date it dates from.
        assertThat(captor.getValue().dateText()).isNull();
        assertThat(captor.getValue().notes()).contains("Nội dung");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("Cắt bớt"));
    }

    @Test
    @DisplayName("a constraint failure on one person is reported and the import goes on to the next")
    void survivesAConstraintFailure() {
        // doThrow, not when(...): calling create inside when() would run the setUp answer with null arguments.
        doThrow(new DataIntegrityViolationException("value too long"))
                .doAnswer(call -> new PersonDetailResponse(
                        2L, "Người Hai", Gender.MALE, null, null, true, null, List.of(), Instant.now(), 0))
                .when(personService).create(any(), any());
        List<GedcomNode> records = GedcomNode.parse("""
                0 @I1@ INDI
                1 NAME Nguyễn /Văn An/
                0 @I2@ INDI
                1 NAME Nguyễn /Văn Hai/
                0 TRLR
                """);

        GedcomImportResponse result = importer.run(records, ACTOR);

        assertThat(result.personsCreated()).isEqualTo(1);
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("@I1@"));
    }

    @Test
    @DisplayName("export then import keeps a grave, its place levels, a cải táng and an event citation (§9)")
    void roundTripsWhatThisReviewAdded() {
        when(placeService.findOrCreatePath(any(), eq(ACTOR))).thenReturn(Optional.of(5L));
        EventResponse reburial = new EventResponse(
                7L, EventSubjectType.PERSON, 10L, EventType.REBURIAL, null, 5L, "Cải táng", 0);
        SourceResponse source = new SourceResponse(3L, "Bia mộ", SourceType.HEADSTONE, null, null, null, null, 2, 0);
        CitationResponse onEvent = new CitationResponse(
                1L, 3L, "Bia mộ", SourceType.HEADSTONE, CitationTargetType.EVENT, 7L, "mặt sau", null, 0);
        CitationResponse onGrave = new CitationResponse(
                2L, 3L, "Bia mộ", SourceType.HEADSTONE, CitationTargetType.GRAVE, 4L, "mặt trước", null, 0);
        GraveResponse grave = new GraveResponse(4L, 10L, GraveKind.GRAVE, 5L, "khu B",
                new BigDecimal("21.020000"), new BigDecimal("105.800000"), null, 0);
        String file = GedcomExporterTest.render(List.of(GedcomExporterTest.person(10L, null)), List.of(reburial),
                List.of(source), List.of(onEvent, onGrave), GedcomExporterTest.xaInTinh(), List.of(grave));

        GedcomImportResponse result = importer.run(GedcomNode.parse(file), ACTOR);

        assertThat(result.dropped()).isZero();
        ArgumentCaptor<EventRequest> event = ArgumentCaptor.forClass(EventRequest.class);
        verify(eventService).create(event.capture(), any());
        assertThat(event.getValue().type()).isEqualTo(EventType.REBURIAL);
        ArgumentCaptor<GraveRequest> saved = ArgumentCaptor.forClass(GraveRequest.class);
        verify(graveService).save(eq(PERSON_ID), saved.capture(), eq(ACTOR));
        assertThat(saved.getValue().plot()).isEqualTo("khu B");
        assertThat(saved.getValue().latitude()).isEqualByComparingTo("21.02");
        ArgumentCaptor<PlacePath> places = ArgumentCaptor.forClass(PlacePath.class);
        // Once for both: the event and the grave name the same PLAC line, and the importer caches each line.
        verify(placeService).findOrCreatePath(places.capture(), eq(ACTOR));
        assertThat(places.getValue().levels()).containsExactly(PlaceType.WARD, PlaceType.PROVINCE);
        ArgumentCaptor<CitationRequest> citations = ArgumentCaptor.forClass(CitationRequest.class);
        verify(sourceService, times(2)).addCitation(citations.capture(), eq(ACTOR));
        assertThat(citations.getAllValues()).extracting(CitationRequest::targetType)
                .containsExactlyInAnyOrder(CitationTargetType.EVENT, CitationTargetType.GRAVE);
    }
}
