package com.genealogy.gedcom.service.impl;

import com.genealogy.branch.dto.request.BranchRequest;
import com.genealogy.branch.service.BranchService;
import com.genealogy.common.exception.ApiException;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.model.PersonNameType;
import com.genealogy.common.util.TextCut;
import com.genealogy.event.dto.request.EventRequest;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.request.FamilyChildRequest;
import com.genealogy.family.dto.request.FamilyRequest;
import com.genealogy.family.dto.response.FamilyResponse;
import com.genealogy.family.dto.response.ImportedUnion;
import com.genealogy.family.service.FamilyService;
import com.genealogy.gedcom.domain.GedcomDates;
import com.genealogy.gedcom.domain.GedcomNames;
import com.genealogy.gedcom.domain.GedcomNode;
import com.genealogy.gedcom.domain.GedcomPlaces;
import com.genealogy.gedcom.domain.GedcomTags;
import com.genealogy.gedcom.dto.response.GedcomImportResponse;
import com.genealogy.grave.dto.request.GraveRequest;
import com.genealogy.grave.dto.response.GraveSaved;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.dto.request.PlacePath;
import com.genealogy.place.service.PlaceService;
import com.genealogy.source.dto.request.CitationRequest;
import com.genealogy.source.dto.request.SourceRequest;
import com.genealogy.source.service.SourceService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;

/** Reads a GEDCOM file into the clan, one record at a time. */
@RequiredArgsConstructor
final class GedcomImporter {

    private static final String IMPORT_NOTE = "Nhập từ tệp GEDCOM";

    // Top-level tags this app has somewhere to put.
    private static final Set<String> MODELLED_TAGS =
            Set.of("INDI", "FAM", "HEAD", "TRLR", "NOTE", "SOUR", "REPO");

    // The versions this importer was written against.
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("5.5.1", "5.5", "7.0");

    private final PersonService personService;
    private final FamilyService familyService;
    private final EventService eventService;
    private final PlaceService placeService;
    private final SourceService sourceService;
    private final BranchService branchService;
    private final GraveService graveService;

    private final Map<String, Long> personIdByXref = new HashMap<>();
    private final Map<String, Long> sourceIdByXref = new HashMap<>();
    private final Map<String, String> repositoryByXref = new HashMap<>();
    private final Map<String, String> noteByXref = new HashMap<>();
    private final Map<String, GedcomTags.ChildRelations> pedigreeByChildAndFamily = new HashMap<>();
    private final Map<Long, Integer> unionsByPartner = new HashMap<>();
    private final Map<String, Optional<Long>> placeIdByLine = new HashMap<>();
    private final Map<String, Optional<Long>> branchIdByPath = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private int personsCreated;
    private int familiesCreated;
    private int eventsCreated;
    private int sourcesCreated;
    private int citationsCreated;
    private int skipped;
    private int dropped;
    // A field rather than a parameter on five methods; this class already carries per-run mutable state.
    private Long actorId;

    /**
     * Reads every record in a parsed GEDCOM file into the clan, record by record.
     *
     * @param records the file's top-level records
     * @param actorId id of the member running the import
     * @return what was created, skipped and guessed
     */
    GedcomImportResponse run(List<GedcomNode> records, Long actorId) {
        this.actorId = actorId;
        // Shared notes are pointed at from anywhere in the file, so they are collected before anything reads one.
        for (GedcomNode record : records) {
            if ("NOTE".equals(record.getTag()) && record.getXref() != null) {
                noteByXref.put(record.getXref(), record.getValue());
            }
            if ("REPO".equals(record.getTag()) && record.getXref() != null) {
                repositoryByXref.put(record.getXref(), record.childValue("NAME").orElse(record.getValue()));
            }
        }
        readHeader(records);
        try {
            readRecords(records);
        } finally {
            // In a finally: a failure midway has already committed people, and đời is only recomputed across the graph.
            familyService.recomputeGenerations();
        }
        return new GedcomImportResponse(
                personsCreated, familiesCreated, eventsCreated, sourcesCreated, citationsCreated,
                skipped, dropped, List.copyOf(warnings));
    }

    /**
     * Reads the sources, people and unions of a file, in the order each needs the one before.
     *
     * @param records the file's top-level records
     */
    private void readRecords(List<GedcomNode> records) {
        // Sources are read before anything cites one, for the same reason people are read before unions.
        for (GedcomNode record : records) {
            if ("SOUR".equals(record.getTag()) && record.getXref() != null) {
                readSource(record);
            }
        }
        // People must exist before any union can point at them, so INDI records are read in full first.
        for (GedcomNode record : records) {
            if ("INDI".equals(record.getTag())) {
                readIndividual(record);
            }
        }
        for (GedcomNode record : records) {
            if ("FAM".equals(record.getTag())) {
                readFamily(record);
            }
        }
        for (GedcomNode record : records) {
            if (!MODELLED_TAGS.contains(record.getTag())) {
                skipped++;
            }
        }
    }

    /**
     * Reads the version the file claims to follow, and says so when it is one this app does not know.
     *
     * @param records the file's top-level records
     */
    private void readHeader(List<GedcomNode> records) {
        String version = records.stream()
                .filter(record -> "HEAD".equals(record.getTag()))
                .findFirst()
                .flatMap(head -> head.child("GEDC"))
                .flatMap(gedc -> gedc.childValue("VERS"))
                .orElse(null);
        // Read at last: the endpoint claims 5.5.1 and 7.0, and until now answered a 4.0 file exactly the same.
        if (version == null) {
            warnings.add("Tệp không khai phiên bản GEDCOM; đọc theo 7.0");
        } else if (!SUPPORTED_VERSIONS.contains(version.strip())) {
            warnings.add("Phiên bản GEDCOM " + version.strip()
                    + " chưa được hỗ trợ; đọc như 7.0, có thể bỏ sót");
        }
    }

    /**
     * Creates one source from a SOUR record.
     *
     * @param record the SOUR record
     */
    private void readSource(GedcomNode record) {
        String title = record.childValue("TITL").orElse(null);
        if (title == null || title.isBlank()) {
            drop("Bỏ nguồn " + label(record) + ": không có tiêu đề");
            return;
        }
        // Every field is cut to its column first: a service call bypasses @Valid, and the insert would fail.
        SourceRequest request = new SourceRequest(
                cut(title.strip(), SourceRequest.MAX_TITLE, "tiêu đề nguồn", record),
                GedcomTags.sourceTypeOf(record.childValue(GedcomExporter.SOURCE_TYPE_TAG).orElse(null)),
                cut(record.childValue("AUTH").orElse(null), SourceRequest.MAX_AUTHOR, "tác giả", record),
                // PUBL only: TEXT is what the source says, so it goes with the notes, never into the date.
                cut(record.childValue("PUBL").orElse(null), SourceRequest.MAX_DATE_TEXT, "năm", record),
                cut(repositoryNameOf(record), SourceRequest.MAX_REPOSITORY, "nơi lưu", record),
                cut(sourceNotesOf(record), SourceRequest.MAX_NOTES, "ghi chú nguồn", record),
                IMPORT_NOTE,
                null);
        try {
            sourceIdByXref.put(record.getXref(), sourceService.create(request, actorId).id());
            sourcesCreated++;
        } catch (ApiException | DataAccessException failure) {
            drop("Không tạo được nguồn " + label(record) + ": " + reasonOf(failure));
        }
    }

    /**
     * Reads a source's notes, with the text it quotes appended when the file records one.
     *
     * @param record the SOUR record
     * @return the notes, or null when there are none
     */
    private String sourceNotesOf(GedcomNode record) {
        String note = noteOf(record);
        String text = record.childValue("TEXT").orElse(null);
        if (text == null || text.isBlank()) {
            return note;
        }
        String quoted = "Nguyên văn: " + text.strip();
        return note == null || note.isBlank() ? quoted : note + "\n\n" + quoted;
    }

    /**
     * Reads a source's repository, following the pointer GEDCOM 7 requires there.
     *
     * @param record the SOUR record
     * @return the repository's name, or null when the file names none
     */
    private String repositoryNameOf(GedcomNode record) {
        String repo = record.childValue("REPO").orElse(null);
        String xref = xrefOf(repo);
        return xref == null ? repo : repositoryByXref.get(xref);
    }

    /**
     * Records every citation hanging off one node, against the row it backs up.
     *
     * @param carrier the INDI, FAM, event or grave node the SOUR lines sit under
     * @param where how the citations' place in the file is named in a warning
     * @param targetType which kind of row it is
     * @param targetId the row the citations belong to
     */
    private void readCitations(GedcomNode carrier, String where, CitationTargetType targetType, Long targetId) {
        for (GedcomNode node : carrier.childrenNamed("SOUR")) {
            String xref = xrefOf(node.getValue());
            Long sourceId = xref == null ? null : sourceIdByXref.get(xref);
            if (sourceId == null) {
                drop("Bỏ trích dẫn " + node.getValue() + " ở " + where + ": không có nguồn tương ứng");
                continue;
            }
            String quote = node.child("DATA").flatMap(data -> data.childValue("TEXT")).orElse(null);
            try {
                sourceService.addCitation(new CitationRequest(
                        sourceId,
                        null,
                        targetType,
                        targetId,
                        cut(node.childValue("PAGE").orElse(null), CitationRequest.MAX_LOCATOR, "vị trí", where),
                        cut(quote, CitationRequest.MAX_QUOTE, "trích dẫn", where),
                        IMPORT_NOTE,
                        null), actorId);
                citationsCreated++;
            } catch (ApiException | DataAccessException failure) {
                drop("Không tạo được trích dẫn ở " + where + ": " + reasonOf(failure));
            }
        }
    }

    /**
     * Counts one thing this app models but could not keep, and says so.
     *
     * @param warning what was lost, in the caller's own words
     */
    private void drop(String warning) {
        // Counted apart from `skipped`, which the UI renders as "not in scope" — a discarded record is not that.
        warnings.add(warning);
        dropped++;
    }

    /**
     * Names a record the way the file does, for a warning a trưởng tộc has to act on.
     *
     * @param record the record being reported
     * @return its identifier in at-signs, or a phrase saying it carries none
     */
    private static String label(GedcomNode record) {
        // A record with no xref used to render as "@null@", a pointer syntax that exists in no file.
        return record.getXref() == null ? "(bản ghi không có mã)" : "@" + record.getXref() + "@";
    }

    /**
     * Says why a service refused a record, in words a trưởng tộc can act on.
     *
     * @param failure what the service threw
     * @return the reason
     */
    private static String reasonOf(RuntimeException failure) {
        // A constraint message is SQL, not a sentence, and names columns nobody reading the warning has seen.
        return failure instanceof ApiException ? failure.getMessage() : "cơ sở dữ liệu không nhận bản ghi này";
    }

    /**
     * Creates one person from an INDI record, together with their names, events and grave.
     *
     * @param record the INDI record
     */
    private void readIndividual(GedcomNode record) {
        List<PersonNameRequest> names = readNames(record);
        if (names.isEmpty()) {
            drop("Bỏ qua " + label(record) + ": không có tên nào đọc được");
            return;
        }

        PersonDetailResponse person;
        // Inside the try, the chi lookup included: one refused INDI must not end the import (§8.8 #4).
        try {
            PersonRequest request = new PersonRequest(
                    GedcomTags.genderOf(record.childValue("SEX").orElse(null)),
                    branchOf(record),
                    noteOf(record),
                    names,
                    IMPORT_NOTE,
                    null);
            person = personService.create(request, actorId);
        } catch (ApiException | DataAccessException failure) {
            drop("Không tạo được người " + label(record) + ": " + reasonOf(failure));
            return;
        }
        personsCreated++;
        if (record.getXref() != null) {
            personIdByXref.put(record.getXref(), person.id());
        }

        readPedigrees(record);
        readCitations(record, label(record), CitationTargetType.PERSON, person.id());
        for (GedcomNode child : record.getChildren()) {
            GedcomTags.personEventOf(child.getTag(), child.childValue("TYPE").orElse(null))
                    .ifPresent(type -> createEvent(type, person.id(), child, label(record)));
        }
        record.child(GedcomExporter.GRAVE_TAG).ifPresent(grave -> readGrave(grave, person.id(), label(record)));
    }

    /**
     * Records a person's mộ phần from the grave extension our own export writes.
     *
     * @param node the grave extension node
     * @param personId whose grave it is
     * @param where how the record is named in a warning
     */
    private void readGrave(GedcomNode node, Long personId, String where) {
        String place = "mộ phần của " + where;
        BigDecimal latitude = node.child("MAP").flatMap(map -> map.childValue("LATI"))
                .map(GedcomPlaces::parseLatitude).orElse(null);
        BigDecimal longitude = node.child("MAP").flatMap(map -> map.childValue("LONG"))
                .map(GedcomPlaces::parseLongitude).orElse(null);
        // A lone coordinate is refused by the service, and dropping both beats losing the whole grave.
        boolean located = latitude != null && longitude != null;
        GraveSaved saved;
        try {
            saved = graveService.save(personId, new GraveRequest(
                    GedcomTags.graveKindOf(node.childValue("TYPE").orElse(null)),
                    placeOf(node, place),
                    cut(node.getValue(), GraveRequest.MAX_PLOT, "vị trí mộ", where),
                    located ? latitude : null,
                    located ? longitude : null,
                    cut(noteOf(node), GraveRequest.MAX_NOTES, "ghi chú mộ", where),
                    IMPORT_NOTE,
                    null), actorId);
        } catch (ApiException | DataAccessException failure) {
            drop("Không ghi được " + place + ": " + reasonOf(failure));
            return;
        }
        readCitations(node, place, CitationTargetType.GRAVE, saved.grave().id());
    }

    /**
     * Remembers how this person joined each union, from the PEDI lines under their FAMC links.
     *
     * @param record the INDI record
     */
    private void readPedigrees(GedcomNode record) {
        // GEDCOM 7 puts PEDI under INDI.FAMC, which is where our own export writes it and where FAM cannot see it.
        for (GedcomNode famc : record.childrenNamed("FAMC")) {
            String familyXref = xrefOf(famc.getValue());
            if (record.getXref() == null || familyXref == null) {
                continue;
            }
            GedcomNode pedi = famc.child("PEDI").orElse(null);
            if (pedi == null) {
                continue;
            }
            pedigreeByChildAndFamily.put(
                    record.getXref() + "|" + familyXref,
                    GedcomTags.relationsOf(pedi.getValue(), pedi.childValue("PHRASE").orElse(null)));
        }
    }

    /**
     * Reads a record's NOTE, following a pointer to a shared note record when that is what it holds.
     *
     * @param record the record carrying the NOTE
     * @return the note text, or null when there is none
     */
    private String noteOf(GedcomNode record) {
        String note = record.childValue("NOTE").orElseGet(() -> record.childValue("SNOTE").orElse(null));
        String xref = xrefOf(note);
        // Stored verbatim, a pointer puts the string "@N1@" in the gia phả and prints it in the book.
        return xref == null ? note : noteByXref.get(xref);
    }

    /**
     * Reads every NAME line on an INDI record.
     *
     * @param record the INDI record
     * @return the names, the first one flagged primary
     */
    private List<PersonNameRequest> readNames(GedcomNode record) {
        List<PersonNameRequest> names = new ArrayList<>();
        for (GedcomNode nameNode : record.childrenNamed("NAME")) {
            GedcomNode typeNode = nameNode.child("TYPE").orElse(null);
            PersonNameType type = typeNode == null
                    ? PersonNameType.BIRTH
                    : GedcomNames.typeOf(typeNode.getValue(), typeNode.childValue("PHRASE").orElse(null));
            // GIVN/SURN are read too: a structured writer puts the real parts there and leaves NAME as `//`.
            PersonNameRequest name = GedcomNames.parse(
                    nameNode.getValue(),
                    nameNode.childValue("GIVN").orElse(null),
                    nameNode.childValue("SURN").orElse(null),
                    type,
                    names.isEmpty());
            if (name != null) {
                names.add(clamp(name, label(record)));
            }
        }
        return names;
    }

    /**
     * Trims a name to the lengths the schema allows, warning when anything had to go.
     *
     * @param name the name as the file spelled it
     * @param label how the record is named in a warning
     * @return a name that will fit
     */
    private PersonNameRequest clamp(PersonNameRequest name, String label) {
        // The limits come from the request record, so they cannot drift from the column they protect.
        String surname = TextCut.toLength(name.surname(), PersonNameRequest.MAX_SURNAME);
        String middleName = TextCut.toLength(name.middleName(), PersonNameRequest.MAX_MIDDLE_NAME);
        String givenName = TextCut.toLength(name.givenName(), PersonNameRequest.MAX_GIVEN_NAME);
        // One over-long name in a foreign file must not roll back the whole import at commit time.
        if (!Objects.equals(surname, name.surname())
                || !Objects.equals(middleName, name.middleName())
                || !Objects.equals(givenName, name.givenName())) {
            warnings.add("Cắt bớt tên quá dài ở " + label + ": " + name.givenName());
        }
        return new PersonNameRequest(name.type(), surname, middleName, givenName, name.primary());
    }

    /**
     * Cuts a field to the length its request allows, warning when anything had to go.
     *
     * @param text the text as the file spelled it, or null
     * @param max the longest the request allows
     * @param field what the field is, for the warning
     * @param record the record it came from
     * @return the text, shortened when it was too long
     */
    private String cut(String text, int max, String field, GedcomNode record) {
        return cut(text, max, field, label(record));
    }

    /**
     * Cuts a field to the length its request allows, warning when anything had to go.
     *
     * @param text the text as the file spelled it, or null
     * @param max the longest the request allows
     * @param field what the field is, for the warning
     * @param where how its place in the file is named in the warning
     * @return the text, shortened when it was too long
     */
    private String cut(String text, int max, String field, String where) {
        String kept = TextCut.toLength(text, max);
        if (!Objects.equals(kept, text)) {
            warnings.add("Cắt bớt " + field + " quá dài ở " + where);
        }
        return kept;
    }

    /**
     * Creates one union from a FAM record, with its partners, children and events.
     *
     * @param record the FAM record
     */
    private void readFamily(GedcomNode record) {
        Long husband = pointer(record.childValue("HUSB").orElse(null));
        Long wife = pointer(record.childValue("WIFE").orElse(null));
        List<GedcomNode> childNodes = record.childrenNamed("CHIL");
        // Both of FamilyService's refusals are mirrored here, or the one it throws for aborts the whole file.
        if ((husband == null && wife == null) || Objects.equals(husband, wife)) {
            drop("Bỏ qua " + label(record) + ": không có cặp vợ chồng hợp lệ");
            return;
        }

        boolean hasMarriage = record.child("MARR").isPresent();
        boolean hasDivorce = record.child("DIV").isPresent();
        FamilyStatus status = GedcomTags.statusOf(hasDivorce, hasMarriage);

        // Collected then linked with the union in one call: each addChild reloads the whole graph and rewrites đời.
        List<FamilyChildRequest> children = new ArrayList<>();
        for (GedcomNode childNode : childNodes) {
            Long childId = pointer(childNode.getValue());
            if (childId == null) {
                drop("Bỏ qua con " + childNode.getValue() + " trong " + label(record)
                        + ": không có INDI tương ứng");
                continue;
            }
            GedcomTags.ChildRelations relations = relationsFor(childNode, record.getXref());
            // birth_order stays null: CHIL order is arrival order, and §5.3 answers "bác/chú/cô" without it.
            children.add(new FamilyChildRequest(
                    childId, relations.toPartner1(), relations.toPartner2(), null));
        }
        ImportedUnion imported;
        try {
            imported = familyService.importUnion(
                    new FamilyRequest(husband, wife, status, orderIndexFor(husband, wife), IMPORT_NOTE, null),
                    children, actorId);
        } catch (ApiException | DataAccessException failure) {
            drop("Không tạo được gia đình " + label(record) + ": " + reasonOf(failure));
            return;
        }
        FamilyResponse family = imported.family();
        familiesCreated++;
        for (String refusal : imported.refusals()) {
            drop("Không nối được con vào " + label(record) + ": " + refusal);
        }
        readCitations(record, label(record), CitationTargetType.FAMILY, family.id());

        for (GedcomNode child : record.getChildren()) {
            GedcomTags.familyEventOf(child.getTag())
                    .ifPresent(type -> createEvent(type, family.id(), child, label(record)));
        }
    }

    /**
     * Works out where this union sits among the ones already read for the same partners.
     *
     * @param husband the first partner, or null
     * @param wife the second partner, or null
     * @return the union's position, counting from zero
     */
    private int orderIndexFor(Long husband, Long wife) {
        // File order is the only sequence a GEDCOM carries, and without it vợ cả and vợ thứ both land at 0.
        int index = 0;
        // Arrays.asList, not List.of: a single-parent union has a null partner and List.of throws on one (§9).
        for (Long partner : Arrays.asList(husband, wife)) {
            if (partner != null) {
                index = Math.max(index, unionsByPartner.merge(partner, 1, Integer::sum) - 1);
            }
        }
        return index;
    }

    /**
     * Works out how a child relates to each partner, preferring the INDI record's own FAMC pedigree.
     *
     * @param childNode the CHIL line, which a foreign file may hang a pedigree off
     * @param familyXref the union's identifier in the file
     * @return the relation to each partner
     */
    private GedcomTags.ChildRelations relationsFor(GedcomNode childNode, String familyXref) {
        String childXref = xrefOf(childNode.getValue());
        GedcomTags.ChildRelations fromIndi = childXref == null || familyXref == null
                ? null
                : pedigreeByChildAndFamily.get(childXref + "|" + familyXref);
        if (fromIndi != null) {
            return fromIndi;
        }
        // Some foreign writers put PEDI under FAM.CHIL instead, so that spelling is read as a fallback.
        GedcomNode pedi = childNode.child("PEDI").orElse(null);
        return pedi == null
                ? GedcomTags.relationsOf(null, null)
                : GedcomTags.relationsOf(pedi.getValue(), pedi.childValue("PHRASE").orElse(null));
    }

    /**
     * Creates one event with its own citations, keeping the import alive when the event is rejected.
     *
     * @param type what happened
     * @param subjectId the person or union it belongs to
     * @param node the GEDCOM node carrying the date
     * @param where how the record is named in a warning
     */
    private void createEvent(EventType type, Long subjectId, GedcomNode node, String where) {
        Optional<GedcomNode> dateNode = node.child("DATE");
        GenealogyDateRequest date = dateNode
                .map(found -> GedcomDates.parse(found.getValue(), found.childValue("PHRASE").orElse(null)))
                .orElse(null);
        String description = noteOf(node);
        // `1 DEAT Y` and `1 DEAT` + `2 PLAC` assert the event with no date; dropped, `living` stays true for ever.
        boolean asserted = "Y".equalsIgnoreCase(node.getValue()) || !node.getChildren().isEmpty();
        if (date == null && description == null && !asserted) {
            return;
        }
        String event = "sự kiện " + type + " của " + where;
        EventResponse created;
        // The place is resolved inside the try: creating a level of it can be refused too (§8.8 #4).
        try {
            created = eventService.create(
                    new EventRequest(subjectId, type, date, placeOf(node, event), description, IMPORT_NOTE, null),
                    actorId);
            eventsCreated++;
        } catch (ApiException | DataAccessException failure) {
            drop("Không tạo được " + event + ": " + reasonOf(failure));
            return;
        }
        readCitations(node, event, CitationTargetType.EVENT, created.id());
    }

    /**
     * Resolves an INDI's chi extension line to a branch of the clan, creating any level of it that is missing.
     *
     * @param record the INDI record
     * @return the branch id, or null when the file names none
     */
    private Long branchOf(GedcomNode record) {
        String path = record.childValue(GedcomExporter.BRANCH_TAG).orElse(null);
        if (path == null || path.isBlank()) {
            return null;
        }
        // Root first, unlike PLAC: a chi has no fixed levels, so the file's own order is the only structure there is.
        Optional<Long> found = branchIdByPath.computeIfAbsent(
                path.strip(),
                value -> branchService.findOrCreatePath(
                        Arrays.stream(value.split(">"))
                                .map(name -> TextCut.toLength(name.strip(), BranchRequest.MAX_NAME))
                                .toList(),
                        actorId));
        return found.orElse(null);
    }

    /**
     * Resolves a node's PLAC line to a place of the clan, creating any level of its path that is missing.
     *
     * @param node the GEDCOM node carrying the PLAC
     * @param what what the place belongs to, for the warning text
     * @return the place id, or null when the node names none
     */
    private Long placeOf(GedcomNode node, String what) {
        GedcomNode plac = node.child("PLAC").orElse(null);
        if (plac == null || plac.getValue() == null || plac.getValue().isBlank()) {
            return null;
        }
        List<String> names = Arrays.asList(plac.getValue().split(",", -1));
        String form = plac.childValue("FORM").orElse(null);
        String latitude = plac.child("MAP").flatMap(map -> map.childValue("LATI")).orElse(null);
        String longitude = plac.child("MAP").flatMap(map -> map.childValue("LONG")).orElse(null);
        // The whole line is the key: the same path with a different FORM or MAP is not the same answer.
        String key = String.join("|", plac.getValue().strip(), String.valueOf(form),
                String.valueOf(latitude), String.valueOf(longitude));
        Optional<Long> found = placeIdByLine.computeIfAbsent(key, ignored -> placeService.findOrCreatePath(
                new PlacePath(
                        names,
                        GedcomPlaces.levels(form, names.size()),
                        GedcomPlaces.parseLatitude(latitude),
                        GedcomPlaces.parseLongitude(longitude)),
                actorId));
        if (found.isEmpty()) {
            drop("Chưa gán được địa danh \"" + plac.getValue().strip() + "\" cho " + what);
        }
        return found.orElse(null);
    }

    /**
     * Resolves a GEDCOM pointer such as {@code @I3@} to the person it now refers to.
     *
     * @param pointer the pointer payload, or null
     * @return the person id, or null when the file never defined that record
     */
    private Long pointer(String pointer) {
        String xref = xrefOf(pointer);
        return xref == null ? null : personIdByXref.get(xref);
    }

    /**
     * Reads the identifier out of a GEDCOM pointer payload such as {@code @I3@}.
     *
     * @param pointer the payload, or null
     * @return the identifier without its at-signs, or null when the payload is not a pointer
     */
    private static String xrefOf(String pointer) {
        // Length checked as well as the ends: the one-character string "@" satisfies both startsWith and endsWith.
        if (pointer == null || pointer.length() < 3 || !pointer.startsWith("@") || !pointer.endsWith("@")) {
            return null;
        }
        return pointer.substring(1, pointer.length() - 1);
    }
}
