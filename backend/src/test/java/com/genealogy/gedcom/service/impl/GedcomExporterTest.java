package com.genealogy.gedcom.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.branch.dto.response.BranchResponse;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.GraveKind;
import com.genealogy.common.model.PersonNameType;
import com.genealogy.common.model.PlaceType;
import com.genealogy.common.model.SourceType;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.grave.dto.response.GraveResponse;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.dto.response.PersonNameResponse;
import com.genealogy.place.dto.response.PlaceResponse;
import com.genealogy.source.dto.response.CitationResponse;
import com.genealogy.source.dto.response.SourceResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for rendering the clan as GEDCOM: the chi, place, grave, cải táng and event-citation lines (§9). */
class GedcomExporterTest {

    /**
     * Builds a minimal person with one primary name.
     *
     * @param id person id
     * @param branchId their recorded chi, or null
     * @return the person
     */
    static PersonDetailResponse person(Long id, Long branchId) {
        PersonNameResponse name =
                new PersonNameResponse(1L, PersonNameType.BIRTH, "Nguyễn", "Văn", "An", true, "Nguyễn Văn An");
        return new PersonDetailResponse(
                id, "Nguyễn Văn An", Gender.MALE, null, branchId, true, null, List.of(name), Instant.now(), 0);
    }

    /**
     * Builds a xã inside a tỉnh, the xã carrying coordinates.
     *
     * @return the two places, xã first
     */
    static List<PlaceResponse> xaInTinh() {
        return List.of(
                new PlaceResponse(5L, "Xã A", PlaceType.WARD, 6L,
                        new BigDecimal("21.020000"), new BigDecimal("105.800000"), "Xã A, Tỉnh C", 0),
                new PlaceResponse(6L, "Tỉnh C", PlaceType.PROVINCE, null, null, null, "Tỉnh C", 0));
    }

    /**
     * Renders a file holding only people, places, events, sources, citations and graves.
     *
     * @param persons the people
     * @param events their events
     * @param sources the sources
     * @param citations the citations
     * @param places the places
     * @param graves the graves
     * @return the file contents
     */
    static String render(
            List<PersonDetailResponse> persons,
            List<EventResponse> events,
            List<SourceResponse> sources,
            List<CitationResponse> citations,
            List<PlaceResponse> places,
            List<GraveResponse> graves) {
        return GedcomExporter.render(persons, List.of(), events, sources, citations, places, List.of(), graves);
    }

    @Test
    @DisplayName("HEAD declares the chi and grave extension tags, so a conforming reader does not reject them")
    void headDeclaresTheExtensionTags() {
        String output = render(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(output).contains("2 TAG " + GedcomExporter.BRANCH_TAG);
        assertThat(output).contains("2 TAG " + GedcomExporter.GRAVE_TAG);
    }

    @Test
    @DisplayName("a person's chi is written as its root-first path, not just the leaf name")
    void writesTheFullBranchPath() {
        BranchResponse root = new BranchResponse(1L, "Chi Hai", null, null, 0);
        BranchResponse leaf = new BranchResponse(2L, "Chi Hai - Phái Hai", 1L, null, 0);

        String output = GedcomExporter.render(
                List.of(person(10L, 2L)), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(root, leaf), List.of());

        assertThat(output).contains("1 " + GedcomExporter.BRANCH_TAG + " Chi Hai > Chi Hai - Phái Hai");
    }

    @Test
    @DisplayName("a person recorded in no chi gets no branch line at all, only the header's declaration")
    void writesNoBranchLineWhenUnrecorded() {
        String output = render(List.of(person(10L, null)), List.of(), List.of(), List.of(), List.of(), List.of());

        // "2 TAG _BRANCH ..." in HEAD.SCHMA is expected regardless; only the person-level "1 _BRANCH" must be absent.
        assertThat(output).doesNotContain("\n1 " + GedcomExporter.BRANCH_TAG);
    }

    @Test
    @DisplayName("an event's place carries each level in FORM and the place's own coordinates in MAP")
    void writesPlaceLevelsAndCoordinates() {
        EventResponse birth = new EventResponse(7L, EventSubjectType.PERSON, 10L, EventType.BIRTH, null, 5L, null, 0);

        String output = render(List.of(person(10L, null)), List.of(birth), List.of(), List.of(), xaInTinh(),
                List.of());

        assertThat(output).contains("2 PLAC Xã A, Tỉnh C\n3 FORM WARD, PROVINCE\n3 MAP\n4 LATI N21.02\n4 LONG E105.8");
    }

    @Test
    @DisplayName("a cải táng is an EVEN whose TYPE names it, since GEDCOM has no tag of its own for one")
    void writesReburialAsTypedEven() {
        EventResponse reburial =
                new EventResponse(7L, EventSubjectType.PERSON, 10L, EventType.REBURIAL, null, null, "Cải táng", 0);

        String output = render(List.of(person(10L, null)), List.of(reburial), List.of(), List.of(), List.of(),
                List.of());

        assertThat(output).contains("1 EVEN\n2 TYPE REBURIAL");
    }

    @Test
    @DisplayName("an event's own citations are written under the event, not dropped from the file")
    void writesEventCitations() {
        EventResponse death = new EventResponse(7L, EventSubjectType.PERSON, 10L, EventType.DEATH, null, null, null, 0);
        SourceResponse source =
                new SourceResponse(3L, "Gia phả cũ", SourceType.CLAN_BOOK, null, null, null, null, 1, 0);
        CitationResponse citation = new CitationResponse(
                1L, 3L, "Gia phả cũ", SourceType.CLAN_BOOK, CitationTargetType.EVENT, 7L, "trang 12", null, 0);

        String output = render(List.of(person(10L, null)), List.of(death), List.of(source), List.of(citation),
                List.of(), List.of());

        assertThat(output).contains("1 DEAT\n2 SOUR @S3@\n3 PAGE trang 12");
    }

    @Test
    @DisplayName("a grave is written with its kind, plot, place, coordinates and citations")
    void writesTheGrave() {
        GraveResponse grave = new GraveResponse(4L, 10L, GraveKind.LIVING_PLOT, 6L, "khu B",
                new BigDecimal("-8.500000"), new BigDecimal("-100.250000"), null, 0);
        CitationResponse bia = new CitationResponse(
                1L, 3L, "Bia mộ", SourceType.HEADSTONE, CitationTargetType.GRAVE, 4L, "mặt trước", null, 0);

        String output = render(List.of(person(10L, null)), List.of(), List.of(), List.of(bia), xaInTinh(),
                List.of(grave));

        assertThat(output).contains("1 _GRAVE khu B\n2 TYPE LIVING_PLOT\n2 PLAC Tỉnh C\n3 FORM PROVINCE\n"
                + "2 MAP\n3 LATI S8.5\n3 LONG W100.25\n2 SOUR @S3@\n3 PAGE mặt trước");
    }
}
