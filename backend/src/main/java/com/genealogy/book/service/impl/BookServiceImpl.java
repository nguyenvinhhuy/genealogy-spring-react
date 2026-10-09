package com.genealogy.book.service.impl;

import com.genealogy.book.service.BookService;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.GraveKind;
import com.genealogy.common.model.SourceType;
import com.genealogy.common.util.VietnamTime;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.dto.response.GraveResponse;
import com.genealogy.grave.service.GraveService;
import com.genealogy.media.service.ImageSize;
import com.genealogy.media.service.MediaService;
import com.genealogy.media.service.StorageService;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.dto.response.PersonNameResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.dto.response.PlaceResponse;
import com.genealogy.place.service.PlaceService;
import com.genealogy.source.dto.response.SourceResponse;
import com.genealogy.source.service.SourceService;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link BookService}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookServiceImpl implements BookService {

    // The life dates a person's entry prints, in the order the entry prints them.
    private static final List<Map.Entry<EventType, String>> DATE_LABELS = List.of(
            Map.entry(EventType.BIRTH, "Sinh"),
            Map.entry(EventType.DEATH, "Mất"),
            Map.entry(EventType.BURIAL, "An táng"),
            Map.entry(EventType.REBURIAL, "Cải táng"));

    private static final float TITLE_SIZE = 26f;
    private static final float SECTION_SIZE = 16f;
    private static final float NAME_SIZE = 12f;
    private static final float BODY_SIZE = 10.5f;

    // Tall enough to recognise a face, short enough that a page still holds several entries.
    private static final float PORTRAIT_HEIGHT = 96f;

    private final PersonService personService;
    private final FamilyService familyService;
    private final EventService eventService;
    private final MediaService mediaService;
    private final StorageService storageService;
    private final PlaceService placeService;
    private final GraveService graveService;
    private final SourceService sourceService;
    private final BookFonts fonts;

    /**
     * Renders the gia phả as a PDF, laid out đời by đời.
     *
     * @param rootPersonId only this person and their descendants, or null for the whole clan
     * @return the PDF bytes
     */
    @Override
    // Laying out the PDF takes seconds and needs no database; holding a pooled connection through it starves others.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public byte[] render(Long rootPersonId) {
        List<PersonDetailResponse> everyone = personService.findAllDetails();
        List<UnionEdgeResponse> unions = familyService.findAllUnions();
        List<EventResponse> events = eventService.findAll();
        // Where, not only when: until 2026-09-28 the book printed no place, no grave and no source (§8.8 #23).
        Map<Long, String> placePathById = placeService.findAll().stream()
                .collect(Collectors.toMap(PlaceResponse::id, PlaceResponse::path));
        Map<Long, GraveResponse> graveByPerson = graveService.findAll().stream()
                .collect(Collectors.toMap(GraveResponse::personId, grave -> grave));

        // Built once and handed down: a chi export used to index every union twice from the same input.
        Map<Long, List<UnionEdgeResponse>> unionsByPartner = unionsByPartner(unions);
        List<PersonDetailResponse> people = rootPersonId == null
                ? everyone
                : descendantsOf(rootPersonId, everyone, unionsByPartner);

        Map<Long, PersonDetailResponse> personById =
                everyone.stream().collect(Collectors.toMap(PersonDetailResponse::id, person -> person));
        Map<Long, List<EventResponse>> eventsByPerson = events.stream()
                .filter(event -> event.subjectType() == EventSubjectType.PERSON)
                .collect(Collectors.groupingBy(EventResponse::subjectId));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out));
                Document document = new Document(pdf, PageSize.A4)) {
            PdfFont regular = fonts.regular();
            PdfFont bold = fonts.bold();
            document.setFont(regular).setFontSize(BODY_SIZE).setMargins(56, 48, 56, 48);

            titlePage(document, bold, regular, rootPersonId == null ? null : personById.get(rootPersonId));
            Entries entries = new Entries(
                    unionsByPartner, personById, eventsByPerson, mediaService.findPortraitKeys(), placePathById,
                    graveByPerson);
            generations(document, bold, people, entries);
            sources(document, bold, sourceService.findAllSources());
        }
        return out.toByteArray();
    }

    /**
     * Every lookup a person's entry reads, built once for the whole book.
     *
     * @param unionsByPartner every union, grouped by each partner
     * @param personById every person by id, for naming a spouse or child
     * @param eventsByPerson every person event, grouped by person
     * @param portraitKeys the storage key of each person's portrait, by person id
     * @param placePathById each place's full path, most specific first, by id
     * @param graveByPerson each person's grave, by person id
     */
    private record Entries(
            Map<Long, List<UnionEdgeResponse>> unionsByPartner,
            Map<Long, PersonDetailResponse> personById,
            Map<Long, List<EventResponse>> eventsByPerson,
            Map<Long, String> portraitKeys,
            Map<Long, String> placePathById,
            Map<Long, GraveResponse> graveByPerson) {
    }

    /**
     * Writes the closing list of every source the gia phả rests on.
     *
     * @param document the document being built
     * @param bold the heading font
     * @param sources every source, in id order
     */
    private void sources(Document document, PdfFont bold, List<SourceResponse> sources) {
        if (sources.isEmpty()) {
            return;
        }
        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        document.add(new Paragraph("NGUỒN TƯ LIỆU").setFont(bold).setFontSize(SECTION_SIZE).setMarginBottom(8));
        for (SourceResponse source : sources) {
            List<String> parts = new ArrayList<>();
            parts.add(source.title());
            parts.add(sourceTypeLabel(source.type()));
            parts.add(source.author());
            parts.add(source.dateText());
            // "Lưu tại" is what lets a later reader go and look at the original for themselves.
            parts.add(source.repository() == null ? null : "lưu tại " + source.repository());
            document.add(line(parts.stream().filter(Objects::nonNull).collect(Collectors.joining(" — "))));
        }
    }

    /**
     * Names a kind of source in Vietnamese, for the printed list.
     *
     * @param type the kind of source
     * @return its name
     */
    private static String sourceTypeLabel(SourceType type) {
        return switch (type) {
            case CLAN_BOOK -> "gia phả cũ";
            case ORAL -> "lời kể";
            case DOCUMENT -> "giấy tờ";
            case HEADSTONE -> "bia mộ";
            case PHOTO -> "ảnh";
            case WEBSITE -> "trang web";
            case OTHER -> "khác";
        };
    }

    /**
     * Writes the title page.
     *
     * @param document the document being built
     * @param bold the heading font
     * @param regular the body font
     * @param root the person the book is rooted at, or null for the whole clan
     */
    private void titlePage(Document document, PdfFont bold, PdfFont regular, PersonDetailResponse root) {
        document.add(new Paragraph("GIA PHẢ")
                .setFont(bold)
                .setFontSize(TITLE_SIZE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(180));
        document.add(new Paragraph(root == null ? "Toàn dòng họ" : "Chi " + root.displayName())
                .setFont(regular)
                .setFontSize(SECTION_SIZE)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Bản in ngày " + VietnamTime.today())
                .setFont(regular)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(24));
        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
    }

    /**
     * Writes one section per đời, in order, each listing the people recorded at it.
     *
     * @param document the document being built
     * @param bold the heading font
     * @param people the people to include
     * @param entries every lookup a person's entry reads
     */
    private void generations(Document document, PdfFont bold, List<PersonDetailResponse> people, Entries entries) {

        // Null-last so people the parentage graph could not place get their own section, not no section.
        Map<Integer, List<PersonDetailResponse>> byGeneration =
                new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
        for (PersonDetailResponse person : people) {
            byGeneration.computeIfAbsent(person.generation(), key -> new ArrayList<>()).add(person);
        }

        for (Map.Entry<Integer, List<PersonDetailResponse>> entry : byGeneration.entrySet()) {
            document.add(new Paragraph(entry.getKey() == null ? "CHƯA RÕ ĐỜI" : "ĐỜI THỨ " + entry.getKey())
                    .setFont(bold)
                    .setFontSize(SECTION_SIZE)
                    .setMarginTop(18)
                    .setMarginBottom(8));
            for (PersonDetailResponse person : entry.getValue()) {
                person(document, bold, person, entries);
            }
        }
    }

    /**
     * Writes one person's entry: their names, dates and places, grave, spouses and children.
     *
     * @param document the document being built
     * @param bold the heading font
     * @param person the person to write
     * @param entries every lookup a person's entry reads
     */
    private void person(Document document, PdfFont bold, PersonDetailResponse person, Entries entries) {
        document.add(new Paragraph(person.displayName())
                .setFont(bold)
                .setFontSize(NAME_SIZE)
                .setMarginTop(10)
                .setMarginBottom(2));

        portrait(person.id(), entries.portraitKeys()).ifPresent(document::add);
        alternateNames(person).ifPresent(text -> document.add(line(text)));
        dates(person, entries.eventsByPerson(), entries.placePathById()).forEach(text -> document.add(line(text)));
        grave(entries.graveByPerson().get(person.id()), entries.placePathById())
                .ifPresent(text -> document.add(line(text)));

        for (UnionEdgeResponse union : entries.unionsByPartner().getOrDefault(person.id(), List.of())) {
            Long spouseId = person.id().equals(union.partner1Id()) ? union.partner2Id() : union.partner1Id();
            document.add(line("Vợ/chồng: " + nameOf(spouseId, entries.personById())));
            if (!union.children().isEmpty()) {
                document.add(line("Con: "
                        + union.children().stream()
                                .map(child -> nameOf(child.childId(), entries.personById()))
                                .collect(Collectors.joining(", "))));
            }
        }

        if (person.notes() != null && !person.notes().isBlank()) {
            document.add(line("Ghi chú: " + person.notes().strip()));
        }
    }

    /**
     * Builds the image block for a person's portrait, sized to sit beside their entry.
     *
     * @param personId the person
     * @param portraitKeys the storage key of each person's portrait, by person id
     * @return the image, or empty when they have no portrait or it cannot be read
     */
    private Optional<Image> portrait(Long personId, Map<Long, String> portraitKeys) {
        String key = portraitKeys.get(personId);
        if (key == null) {
            return Optional.empty();
        }
        // The small copy, as full photos bloat the PDF; one that will not load leaves the entry text-only.
        return storageService.download(key, ImageSize.THUMBNAIL).flatMap(bytes -> {
            try {
                return Optional.of(new Image(ImageDataFactory.create(bytes))
                        .setMaxHeight(PORTRAIT_HEIGHT)
                        .setMarginTop(2)
                        .setMarginBottom(4));
            } catch (Exception failure) {
                log.warn("Could not embed portrait {} in the book", key, failure);
                return Optional.empty();
            }
        });
    }

    /**
     * Renders the names a person was also known by.
     *
     * @param person the person
     * @return the line, or empty when they have only a primary name
     */
    private static Optional<String> alternateNames(PersonDetailResponse person) {
        String text = person.names().stream()
                .filter(name -> !name.primary())
                .map(name -> vietnameseNameType(name) + ": " + name.display())
                .collect(Collectors.joining("; "));
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    /**
     * Renders a person's birth, death, burial and cải táng lines, each with where it happened.
     *
     * @param person the person
     * @param eventsByPerson every person event, grouped by person
     * @param placePathById each place's full path, by id
     * @return the lines to print, in that order
     */
    private static List<String> dates(
            PersonDetailResponse person,
            Map<Long, List<EventResponse>> eventsByPerson,
            Map<Long, String> placePathById) {
        List<EventResponse> events = eventsByPerson.getOrDefault(person.id(), List.of());
        List<String> lines = new ArrayList<>();
        for (Map.Entry<EventType, String> label : DATE_LABELS) {
            events.stream()
                    .filter(event -> event.type() == label.getKey())
                    // The server renders the date once, so the printed page and the screen cannot disagree.
                    .map(event -> joinWhenAndWhere(
                            event.date() == null ? null : event.date().display(),
                            event.placeId() == null ? null : placePathById.get(event.placeId())))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(text -> lines.add(label.getValue() + ": " + text));
        }
        return lines;
    }

    /**
     * Joins a date and a place into one line, either of which may be missing.
     *
     * @param when the rendered date, or null
     * @param where the place's path, or null
     * @return "when, tại where", or whichever part there is, or null when there is neither
     */
    private static String joinWhenAndWhere(String when, String where) {
        if (where == null) {
            return when;
        }
        return when == null ? "tại " + where : when + ", tại " + where;
    }

    /**
     * Renders a person's grave as one line.
     *
     * @param grave the grave, or null when none is recorded
     * @param placePathById each place's full path, by id
     * @return the line, or empty when there is no grave or it records nothing to print
     */
    private static Optional<String> grave(GraveResponse grave, Map<Long, String> placePathById) {
        if (grave == null) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>();
        parts.add(grave.placeId() == null ? null : placePathById.get(grave.placeId()));
        parts.add(grave.plot());
        String where = parts.stream().filter(Objects::nonNull).collect(Collectors.joining("; "));
        String label = grave.kind() == GraveKind.LIVING_PLOT ? "Sinh phần" : "Mộ phần";
        return Optional.of(label + (where.isEmpty() ? " (chưa ghi vị trí)" : ": " + where));
    }

    /**
     * Walks down from one person to every descendant and the spouse each of them married in.
     *
     * @param rootPersonId the person to start from
     * @param everyone every person in the clan
     * @param unions every union
     * @param unionsByPartner every union, grouped by each partner
     * @return the people in that chi, in the order the whole-clan list had them
     */
    private static List<PersonDetailResponse> descendantsOf(
            Long rootPersonId,
            List<PersonDetailResponse> everyone,
            Map<Long, List<UnionEdgeResponse>> unionsByPartner) {

        if (everyone.stream().noneMatch(person -> person.id().equals(rootPersonId))) {
            throw new NotFoundException("Không có người với id " + rootPersonId);
        }
        Set<Long> included = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootPersonId);
        included.add(rootPersonId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            for (UnionEdgeResponse union : unionsByPartner.getOrDefault(current, List.of())) {
                // A gia phả that lists a man's children but not their mother is not a gia phả.
                Long spouseId = current.equals(union.partner1Id()) ? union.partner2Id() : union.partner1Id();
                if (spouseId != null) {
                    included.add(spouseId);
                }
                for (ChildEdgeResponse child : union.children()) {
                    if (included.add(child.childId())) {
                        queue.addLast(child.childId());
                    }
                }
            }
        }
        return everyone.stream().filter(person -> included.contains(person.id())).toList();
    }

    /**
     * Groups every union under each of its partners.
     *
     * @param unions every union
     * @return the unions each person is a partner in
     */
    private static Map<Long, List<UnionEdgeResponse>> unionsByPartner(List<UnionEdgeResponse> unions) {
        Map<Long, List<UnionEdgeResponse>> byPartner = new LinkedHashMap<>();
        for (UnionEdgeResponse union : unions) {
            if (union.partner1Id() != null) {
                byPartner.computeIfAbsent(union.partner1Id(), key -> new ArrayList<>()).add(union);
            }
            if (union.partner2Id() != null) {
                byPartner.computeIfAbsent(union.partner2Id(), key -> new ArrayList<>()).add(union);
            }
        }
        return byPartner;
    }

    /**
     * Names a person, saying so plainly when the record does not.
     *
     * @param id the person id, or null
     * @param personById every person by id
     * @return the display name, or "chưa rõ"
     */
    private static String nameOf(Long id, Map<Long, PersonDetailResponse> personById) {
        PersonDetailResponse person = id == null ? null : personById.get(id);
        return person == null ? "chưa rõ" : person.displayName();
    }

    /**
     * Names a kind of name in Vietnamese.
     *
     * @param name the name
     * @return the Vietnamese label
     */
    private static String vietnameseNameType(PersonNameResponse name) {
        return switch (name.type()) {
            case BIRTH -> "Tên khai sinh";
            case HUY -> "Tên húy";
            case TU -> "Tên tự";
            case HIEU -> "Tên hiệu";
            case THUY -> "Thụy hiệu";
            case SAINT -> "Tên thánh";
            case ALIAS -> "Biệt danh";
        };
    }

    /**
     * Builds one body line of a person's entry.
     *
     * @param text the text to print
     * @return the paragraph
     */
    private static Paragraph line(String text) {
        return new Paragraph(text).setMarginTop(0).setMarginBottom(1).setMultipliedLeading(1.15f);
    }
}
