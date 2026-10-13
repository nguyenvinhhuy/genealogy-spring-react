package com.genealogy.book.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.RelationType;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.media.service.MediaService;
import com.genealogy.media.service.StorageService;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import com.genealogy.source.service.SourceService;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the printed gia phả, read back as text from the PDF itself. */
class BookServiceImplTest {

    private final PersonService personService = mock(PersonService.class);
    private final FamilyService familyService = mock(FamilyService.class);
    private final EventService eventService = mock(EventService.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final StorageService storageService = mock(StorageService.class);
    private final PlaceService placeService = mock(PlaceService.class);
    private final GraveService graveService = mock(GraveService.class);
    private final SourceService sourceService = mock(SourceService.class);

    private final BookServiceImpl book = new BookServiceImpl(
            personService, familyService, eventService, mediaService, storageService, placeService, graveService,
            sourceService, new BookFonts());

    private final List<PersonDetailResponse> people = new ArrayList<>();
    private final List<UnionEdgeResponse> unions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(personService.findAllDetails()).thenReturn(people);
        lenient().when(familyService.findAllUnions()).thenReturn(unions);
        lenient().when(eventService.findAll()).thenReturn(List.of());
        lenient().when(placeService.findAll()).thenReturn(List.of());
        lenient().when(graveService.findAll()).thenReturn(List.of());
        lenient().when(mediaService.findPortraitKeys()).thenReturn(Map.of());
        lenient().when(sourceService.findAllSources()).thenReturn(List.of());
    }

    /**
     * Records one person in the clan.
     *
     * @param id the person id
     * @param name their display name
     * @param gender their recorded sex
     * @param generation their đời
     */
    private void person(long id, String name, Gender gender, Integer generation) {
        people.add(new PersonDetailResponse(
                id, name, gender, generation, null, false, null, List.of(), Instant.EPOCH, 0));
    }

    /**
     * Builds a birth child of both partners.
     *
     * @param childId the child
     * @return the child link
     */
    private static ChildEdgeResponse born(long childId) {
        return new ChildEdgeResponse(childId, RelationType.BIRTH, RelationType.BIRTH, null);
    }

    /**
     * Renders the whole-clan book and reads every page back as one string.
     *
     * @param clan the people of the blood line
     * @return the book's text
     * @throws IOException when the PDF cannot be read
     */
    private String renderClan(Long... clan) throws IOException {
        when(familyService.findClanMemberIds()).thenReturn(Set.of(clan));
        return text(book.render(null));
    }

    /**
     * Extracts the text of every page of a PDF.
     *
     * @param bytes the PDF
     * @return the text, page after page
     * @throws IOException when the PDF cannot be read
     */
    private static String text(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        try (PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))) {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                text.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page))).append('\n');
            }
        }
        return text.toString();
    }

    /**
     * Counts how often a phrase appears in a text.
     *
     * @param text the text
     * @param phrase the phrase
     * @return the number of occurrences
     */
    private static int count(String text, String phrase) {
        int found = 0;
        for (int at = text.indexOf(phrase); at >= 0; at = text.indexOf(phrase, at + phrase.length())) {
            found++;
        }
        return found;
    }

    @Test
    @DisplayName("a married couple prints once, husband first, with their children listed once beneath them")
    void coupleIsOneEntryWithItsChildrenOnce() throws IOException {
        person(1, "Nguyễn Lê", Gender.MALE, 1);
        person(2, "Nguyễn Thị Mỹ", Gender.FEMALE, 1);
        person(3, "Nguyễn Vinh Chương", Gender.MALE, 2);
        person(4, "Nguyễn Thị Tâm", Gender.FEMALE, 2);
        unions.add(new UnionEdgeResponse(10L, 2L, 1L, FamilyStatus.MARRIED, 0, List.of(born(3), born(4))));

        String text = renderClan(1L, 3L, 4L);

        assertThat(text).contains("Nguyễn Lê – Nguyễn Thị Mỹ");
        assertThat(count(text, "Con: ")).isEqualTo(1);
        assertThat(text).contains("Con: Nguyễn Vinh Chương, Nguyễn Thị Tâm");
        // The wife who married in has no heading of her own: her name appears only in the couple line.
        assertThat(count(text, "Nguyễn Thị Mỹ")).isEqualTo(1);
    }

    @Test
    @DisplayName("each marriage of a man married twice is its own numbered couple with its own children")
    void secondMarriageIsItsOwnCouple() throws IOException {
        person(1, "Nguyễn Lê", Gender.MALE, 1);
        person(2, "Phạm Thị A", Gender.FEMALE, 1);
        person(3, "Trần Thị B", Gender.FEMALE, 1);
        person(4, "Nguyễn Văn Con Cả", Gender.MALE, 2);
        person(5, "Nguyễn Văn Con Thứ", Gender.MALE, 2);
        unions.add(new UnionEdgeResponse(10L, 1L, 2L, FamilyStatus.MARRIED, 0, List.of(born(4))));
        unions.add(new UnionEdgeResponse(11L, 1L, 3L, FamilyStatus.MARRIED, 1, List.of(born(5))));

        String text = renderClan(1L, 4L, 5L);

        assertThat(text).contains("1. Nguyễn Lê – Phạm Thị A").contains("2. Nguyễn Lê – Trần Thị B");
        assertThat(text.indexOf("Con: Nguyễn Văn Con Cả")).isLessThan(text.indexOf("2. Nguyễn Lê – Trần Thị B"));
        assertThat(text.indexOf("Con: Nguyễn Văn Con Thứ")).isGreaterThan(text.indexOf("2. Nguyễn Lê – Trần Thị B"));
    }

    @Test
    @DisplayName("siblings print in birth order within their đời, not in the order they were typed in")
    void siblingsPrintInTheOrderOfTheUnion() throws IOException {
        person(1, "Nguyễn Lê", Gender.MALE, 1);
        // Entered youngest first: ids say nothing about who is the eldest.
        person(2, "Nguyễn Út", Gender.MALE, 2);
        person(3, "Nguyễn Giữa", Gender.MALE, 2);
        person(4, "Nguyễn Cả", Gender.MALE, 2);
        unions.add(new UnionEdgeResponse(10L, 1L, null, FamilyStatus.UNKNOWN, 0, List.of(
                new ChildEdgeResponse(4L, RelationType.BIRTH, RelationType.BIRTH, 1),
                new ChildEdgeResponse(3L, RelationType.BIRTH, RelationType.BIRTH, 2),
                new ChildEdgeResponse(2L, RelationType.BIRTH, RelationType.BIRTH, 3))));

        String text = renderClan(1L, 2L, 3L, 4L);
        String secondGeneration = text.substring(text.indexOf("ĐỜI THỨ 2"));

        assertThat(secondGeneration.indexOf("Nguyễn Cả")).isLessThan(secondGeneration.indexOf("Nguyễn Giữa"));
        assertThat(secondGeneration.indexOf("Nguyễn Giữa")).isLessThan(secondGeneration.indexOf("Nguyễn Út"));
    }

    @Test
    @DisplayName("a con nuôi is printed as a child with the link named, and an unrecorded sex is not read as male")
    void adoptedChildAndUnknownSex() throws IOException {
        person(1, "Nguyễn Lê", Gender.MALE, 1);
        person(2, "Lê Chưa Rõ", Gender.UNKNOWN, 1);
        person(3, "Nguyễn Nuôi", Gender.MALE, 2);
        unions.add(new UnionEdgeResponse(10L, 1L, 2L, FamilyStatus.MARRIED, 0, List.of(
                new ChildEdgeResponse(3L, RelationType.ADOPTED, RelationType.ADOPTED, null))));

        String text = renderClan(1L);

        assertThat(text).contains("Nguyễn Nuôi (con nuôi)");
        // The adopted child still has an entry of their own in đời 2.
        assertThat(text.substring(text.indexOf("ĐỜI THỨ 2"))).contains("Nguyễn Nuôi");
        assertThat(text).contains("Nguyễn Lê – Lê Chưa Rõ");
    }

    @Test
    @DisplayName("a woman of the clan heads her own marriage, her husband named first in the couple line")
    void daughterOfTheClanHeadsHerMarriage() throws IOException {
        person(1, "Nguyễn Lê", Gender.MALE, 1);
        person(2, "Nguyễn Thị Tâm", Gender.FEMALE, 2);
        person(3, "Phạm Tài", Gender.MALE, 2);
        person(4, "Phạm Công Vũ", Gender.MALE, 3);
        unions.add(new UnionEdgeResponse(10L, 1L, null, FamilyStatus.UNKNOWN, 0, List.of(born(2))));
        unions.add(new UnionEdgeResponse(11L, 2L, 3L, FamilyStatus.MARRIED, 0, List.of(born(4))));

        String text = renderClan(1L, 2L, 4L);

        assertThat(text).contains("Phạm Tài – Nguyễn Thị Tâm").contains("Con: Phạm Công Vũ");
        // The rể prints only inside her entry, never under a heading of his own.
        assertThat(count(text, "Phạm Tài")).isEqualTo(1);
    }
}
