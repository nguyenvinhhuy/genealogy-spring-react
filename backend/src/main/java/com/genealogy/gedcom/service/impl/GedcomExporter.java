package com.genealogy.gedcom.service.impl;

import com.genealogy.branch.dto.response.BranchResponse;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.PlaceType;
import com.genealogy.common.model.RelationType;
import com.genealogy.common.util.VietnamTime;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.gedcom.domain.GedcomDates;
import com.genealogy.gedcom.domain.GedcomNames;
import com.genealogy.gedcom.domain.GedcomNode;
import com.genealogy.gedcom.domain.GedcomPlaces;
import com.genealogy.gedcom.domain.GedcomTags;
import com.genealogy.grave.dto.response.GraveResponse;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.dto.response.PersonNameResponse;
import com.genealogy.place.dto.response.PlaceResponse;
import com.genealogy.source.dto.response.CitationResponse;
import com.genealogy.source.dto.response.SourceResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders the whole clan as a GEDCOM 7.0 file. */
final class GedcomExporter {

    // Extension tag carrying this app's source kind, which GEDCOM 7 has no substructure for.
    static final String SOURCE_TYPE_TAG = "_STYPE";

    // Extension tag carrying a person's chi / phái / nhánh, which GEDCOM 7 has no concept of at all.
    static final String BRANCH_TAG = "_BRANCH";

    // Extension tag carrying a person's mộ phần: GEDCOM's BURI is when a burial happened, not where the grave is now.
    static final String GRAVE_TAG = "_GRAVE";

    private static final String SOURCE_TYPE_URI = "https://genealogy.local/gedcom/SourceType";
    private static final String BRANCH_URI = "https://genealogy.local/gedcom/Branch";
    private static final String GRAVE_URI = "https://genealogy.local/gedcom/Grave";

    /** Not instantiable. */
    private GedcomExporter() {
    }

    /**
     * Renders every record the clan holds as one GEDCOM 7.0 file.
     *
     * @param persons every person with their names
     * @param unions every union with its children
     * @param events every event
     * @param sources every source
     * @param citations every citation
     * @param places every place, for rendering each event's and grave's PLAC
     * @param branches every clan branch, for rendering each person's chi
     * @param graves every grave
     * @return the file contents
     */
    static String render(
            List<PersonDetailResponse> persons,
            List<UnionEdgeResponse> unions,
            List<EventResponse> events,
            List<SourceResponse> sources,
            List<CitationResponse> citations,
            List<PlaceResponse> places,
            List<BranchResponse> branches,
            List<GraveResponse> graves) {

        // Every index is built once up front: scanning the unions for each person was O(people × unions).
        Index index = new Index(
                placeLines(places),
                branchPaths(branches),
                eventsBySubject(events, EventSubjectType.PERSON),
                eventsBySubject(events, EventSubjectType.FAMILY),
                citationsByTarget(citations, CitationTargetType.PERSON),
                citationsByTarget(citations, CitationTargetType.FAMILY),
                citationsByTarget(citations, CitationTargetType.EVENT),
                citationsByTarget(citations, CitationTargetType.GRAVE),
                graves.stream().collect(Collectors.toMap(GraveResponse::personId, grave -> grave)),
                unionsAsPartner(unions),
                childEdges(unions),
                persons.stream().collect(Collectors.toMap(PersonDetailResponse::id, PersonDetailResponse::gender)));

        List<GedcomNode> records = new ArrayList<>();
        records.add(header());
        for (PersonDetailResponse person : persons) {
            records.add(individual(person, index));
        }
        for (UnionEdgeResponse union : unions) {
            records.add(family(union, index));
        }
        List<GedcomNode> repositories = new ArrayList<>();
        Map<String, String> repositoryXrefs = repositoryRecords(sources, repositories);
        records.addAll(repositories);
        for (SourceResponse source : sources) {
            records.add(source(source, repositoryXrefs));
        }
        records.add(GedcomNode.of("TRLR", null));

        StringBuilder out = new StringBuilder();
        records.forEach(record -> record.render(0, out));
        return out.toString();
    }

    /**
     * Every lookup the records need, built once for the whole file.
     *
     * @param places each place's rendered PLAC line, by id
     * @param branches each branch's rendered root-first path, by id
     * @param personEvents every person event, grouped by person
     * @param familyEvents every family event, grouped by union
     * @param personCitations every person citation, grouped by person
     * @param familyCitations every family citation, grouped by union
     * @param eventCitations every event citation, grouped by event
     * @param graveCitations every grave citation, grouped by grave
     * @param graveByPerson each person's grave, by person id
     * @param unionsAsPartner the unions each person is a partner in, by person id
     * @param childEdges the union and child link each person appears as a child in, by person id
     * @param genderById each person's recorded sex, by id
     */
    private record Index(
            Map<Long, PlaceLine> places,
            Map<Long, String> branches,
            Map<Long, List<EventResponse>> personEvents,
            Map<Long, List<EventResponse>> familyEvents,
            Map<Long, List<CitationResponse>> personCitations,
            Map<Long, List<CitationResponse>> familyCitations,
            Map<Long, List<CitationResponse>> eventCitations,
            Map<Long, List<CitationResponse>> graveCitations,
            Map<Long, GraveResponse> graveByPerson,
            Map<Long, List<UnionEdgeResponse>> unionsAsPartner,
            Map<Long, ChildOf> childEdges,
            Map<Long, Gender> genderById) {
    }

    /**
     * One place as a PLAC line renders it.
     *
     * @param path the place and every place above it, most specific first
     * @param levels the level of each component of the path, index for index
     * @param latitude the place's own latitude, or null
     * @param longitude the place's own longitude, or null
     */
    private record PlaceLine(String path, List<PlaceType> levels, BigDecimal latitude, BigDecimal longitude) {
    }

    /**
     * Builds the file header, which names the version this file claims to follow.
     *
     * @return the HEAD record
     */
    private static GedcomNode header() {
        LocalDate today = VietnamTime.today();
        return GedcomNode.of("HEAD", null)
                .with(GedcomNode.of("GEDC", null).with("VERS", "7.0"))
                .with(GedcomNode.of("SOUR", "GENEALOGY").with("NAME", "Gia phả"))
                .with(GedcomNode.of("DATE", gedcomToday(today)))
                // Declared, or the extension tags are undocumented in a file whose header claims to follow 7.0.
                .with(GedcomNode.of("SCHMA", null)
                        .with("TAG", SOURCE_TYPE_TAG + " " + SOURCE_TYPE_URI)
                        .with("TAG", BRANCH_TAG + " " + BRANCH_URI)
                        .with("TAG", GRAVE_TAG + " " + GRAVE_URI))
                // Đời is derived on every write, so exporting it invites a reader to treat it as fact.
                .with("NOTE", "Đời (generation) is derived and therefore not exported.");
    }

    /**
     * Renders one person as an INDI record.
     *
     * @param person the person
     * @param index every lookup built for the file
     * @return the INDI record
     */
    private static GedcomNode individual(PersonDetailResponse person, Index index) {
        GedcomNode record = GedcomNode.record(individualXref(person.id()), "INDI");
        for (PersonNameResponse name : person.names()) {
            record.with(name(name));
        }
        record.with("SEX", GedcomTags.sexOf(person.gender()));
        for (EventResponse event : index.personEvents().getOrDefault(person.id(), List.of())) {
            record.with(event(event, index));
        }
        for (UnionEdgeResponse union : index.unionsAsPartner().getOrDefault(person.id(), List.of())) {
            record.with("FAMS", "@" + familyXref(union.familyId()) + "@");
        }
        ChildOf childOf = index.childEdges().get(person.id());
        if (childOf != null) {
            record.with(GedcomNode.of("FAMC", "@" + familyXref(childOf.familyId()) + "@")
                    .with(pedigree(childOf.child())));
        }
        record.with("NOTE", person.notes());
        // An extension, not PLAC or RESI: chi/phái is this clan's own structure, not a GEDCOM 7 concept.
        record.with(BRANCH_TAG, person.branchId() == null ? null : index.branches().get(person.branchId()));
        GraveResponse grave = index.graveByPerson().get(person.id());
        if (grave != null) {
            record.with(grave(grave, index));
        }
        for (CitationResponse citation : index.personCitations().getOrDefault(person.id(), List.of())) {
            record.with(citation(citation));
        }
        return record;
    }

    /**
     * Renders a person's mộ phần as the grave extension, with its place, coordinates and citations.
     *
     * @param grave the grave
     * @param index every lookup built for the file
     * @return the extension node
     */
    private static GedcomNode grave(GraveResponse grave, Index index) {
        // The grave where it is now; each cải táng that moved it is its own EVEN TYPE REBURIAL (§8.8 D2).
        GedcomNode node = GedcomNode.of(GRAVE_TAG, grave.plot())
                .with("TYPE", grave.kind().name())
                .with(place(grave.placeId(), index))
                .with(map(grave.latitude(), grave.longitude()))
                .with("NOTE", grave.notes());
        for (CitationResponse citation : index.graveCitations().getOrDefault(grave.id(), List.of())) {
            node.with(citation(citation));
        }
        return node;
    }

    /**
     * Renders one name as a NAME line with its kind and parts.
     *
     * @param name the name
     * @return the NAME node
     */
    private static GedcomNode name(PersonNameResponse name) {
        // The Vietnamese kind rides in a PHRASE: g7's NAME.TYPE enumset has no tên húy, tự, hiệu or thụy.
        return GedcomNode.of("NAME", GedcomNames.format(name))
                .with(GedcomNode.of("TYPE", GedcomNames.gedcomTypeOf(name.type()))
                        .with("PHRASE", name.type().name()))
                .with("GIVN", name.givenName())
                .with("SURN", name.surname());
    }

    /**
     * Renders one event as the tag GEDCOM records it under, with its place and its own citations.
     *
     * @param event the event
     * @param index every lookup built for the file
     * @return the event node
     */
    private static GedcomNode event(EventResponse event, Index index) {
        GedcomNode node = GedcomNode.of(GedcomTags.tagOf(event.type()), null);
        String date = GedcomDates.format(event.date());
        String phrase = GedcomDates.phrase(event.date());
        // Written even with an empty DATE: "đời Tự Đức" has no year, and dropping it discards the only fact there.
        if (date != null || phrase != null) {
            node.with(GedcomNode.of("DATE", date).with("PHRASE", phrase));
        }
        if (GedcomTags.needsTypeLine(event.type())) {
            node.with("TYPE", event.type().name());
        }
        node.with(place(event.placeId(), index));
        node.with("NOTE", event.description());
        // Under the event itself: until 2026-09-28 an event's citations were in no line of the file (§8.8 #9).
        for (CitationResponse citation : index.eventCitations().getOrDefault(event.id(), List.of())) {
            node.with(citation(citation));
        }
        return node;
    }

    /**
     * Renders a place as a PLAC line with the level of each component and the place's own coordinates.
     *
     * @param placeId the place id, or null
     * @param index every lookup built for the file
     * @return the PLAC node, or null when there is no place
     */
    private static GedcomNode place(Long placeId, Index index) {
        PlaceLine line = placeId == null ? null : index.places().get(placeId);
        if (line == null) {
            return null;
        }
        // FORM, not an extension: it is GEDCOM's own way to say which component is the xã and which the tỉnh.
        return GedcomNode.of("PLAC", line.path())
                .with("FORM", GedcomPlaces.form(line.levels()))
                .with(map(line.latitude(), line.longitude()));
    }

    /**
     * Renders a pair of coordinates as a MAP structure.
     *
     * @param latitude degrees north, or null
     * @param longitude degrees east, or null
     * @return the MAP node, or null when the pair is incomplete
     */
    private static GedcomNode map(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return GedcomNode.of("MAP", null)
                .with("LATI", GedcomPlaces.latitude(latitude))
                .with("LONG", GedcomPlaces.longitude(longitude));
    }

    /**
     * Renders each place as the comma-separated path GEDCOM writes, most specific first, with its levels.
     *
     * @param places every place, each carrying its parent id
     * @return the PLAC line of each place, by id
     */
    private static Map<Long, PlaceLine> placeLines(List<PlaceResponse> places) {
        Map<Long, PlaceResponse> byId = places.stream()
                .collect(Collectors.toMap(PlaceResponse::id, place -> place));
        Map<Long, PlaceLine> lines = new HashMap<>();
        for (PlaceResponse place : places) {
            List<String> parts = new ArrayList<>();
            List<PlaceType> levels = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            PlaceResponse cursor = place;
            // Bounded by `seen`: a parent cycle is refused on write, and would otherwise spin here for ever.
            while (cursor != null && seen.add(cursor.id())) {
                parts.add(cursor.name());
                levels.add(cursor.type());
                cursor = cursor.parentId() == null ? null : byId.get(cursor.parentId());
            }
            lines.put(place.id(),
                    new PlaceLine(String.join(", ", parts), levels, place.latitude(), place.longitude()));
        }
        return lines;
    }

    /**
     * Renders each branch as its root-first path, e.g. "Chi Hai > Chi Hai - Phái Hai".
     *
     * @param branches every branch, each carrying its parent id
     * @return the path of each branch, by id
     */
    private static Map<Long, String> branchPaths(List<BranchResponse> branches) {
        Map<Long, BranchResponse> byId = branches.stream()
                .collect(Collectors.toMap(BranchResponse::id, branch -> branch));
        Map<Long, String> paths = new HashMap<>();
        for (BranchResponse branch : branches) {
            List<String> parts = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            BranchResponse cursor = branch;
            // Bounded by `seen`: a parent cycle is refused on write, and would otherwise spin here for ever.
            while (cursor != null && seen.add(cursor.id())) {
                parts.add(cursor.name());
                cursor = cursor.parentId() == null ? null : byId.get(cursor.parentId());
            }
            // Root first, unlike PLAC: a chi has no fixed levels, so only the parent order gives it meaning.
            Collections.reverse(parts);
            paths.put(branch.id(), String.join(" > ", parts));
        }
        return paths;
    }

    /**
     * Indexes every union by each of its partners.
     *
     * @param unions every union
     * @return the unions each person is a partner in, by person id
     */
    private static Map<Long, List<UnionEdgeResponse>> unionsAsPartner(List<UnionEdgeResponse> unions) {
        Map<Long, List<UnionEdgeResponse>> index = new HashMap<>();
        for (UnionEdgeResponse union : unions) {
            for (Long partner : Arrays.asList(union.partner1Id(), union.partner2Id())) {
                if (partner != null) {
                    index.computeIfAbsent(partner, key -> new ArrayList<>()).add(union);
                }
            }
        }
        return index;
    }

    /**
     * Indexes the union each person appears in as a child.
     *
     * @param unions every union
     * @return the union and child link for each child, by person id
     */
    private static Map<Long, ChildOf> childEdges(List<UnionEdgeResponse> unions) {
        Map<Long, ChildOf> index = new HashMap<>();
        for (UnionEdgeResponse union : unions) {
            for (ChildEdgeResponse child : union.children()) {
                // First wins, matching the old scan: GEDCOM FAMC is one link and a child has one set of parents here.
                index.putIfAbsent(child.childId(), new ChildOf(union.familyId(), child));
            }
        }
        return index;
    }

    /**
     * The union a person is a child in, with the link that says how they joined it.
     *
     * @param familyId the union
     * @param child the child link
     */
    private record ChildOf(Long familyId, ChildEdgeResponse child) {
    }

    /**
     * Renders how a child joined a union, as a PEDI line with a phrase for what PEDI cannot say.
     *
     * @param child the child link
     * @return the PEDI node, or null when both relations are plain birth
     */
    private static GedcomNode pedigree(ChildEdgeResponse child) {
        String value = GedcomTags.pedigreeOf(child.relationToP1());
        String phrase = GedcomTags.pedigreePhraseOf(child.relationToP1(), child.relationToP2());
        if (value == null && phrase == null) {
            return null;
        }
        // Written out as BIRTH when only the phrase needs saying: a PEDI line with no value is not legal GEDCOM.
        return GedcomNode.of("PEDI", value == null ? RelationType.BIRTH.name() : value).with("PHRASE", phrase);
    }

    /**
     * Renders one union as a FAM record.
     *
     * @param union the union
     * @param index every lookup built for the file
     * @return the FAM record
     */
    private static GedcomNode family(UnionEdgeResponse union, Index index) {
        GedcomNode record = GedcomNode.record(familyXref(union.familyId()), "FAM");
        // Filled by each partner's recorded sex: by position, a wife recorded first becomes the husband.
        Long husband = union.partner1Id();
        Long wife = union.partner2Id();
        if (index.genderById().get(husband) == Gender.FEMALE || index.genderById().get(wife) == Gender.MALE) {
            husband = union.partner2Id();
            wife = union.partner1Id();
        }
        record.with("HUSB", husband == null ? null : "@" + individualXref(husband) + "@");
        record.with("WIFE", wife == null ? null : "@" + individualXref(wife) + "@");
        // Children are written in birth order, which is the only place GEDCOM records con trưởng.
        for (ChildEdgeResponse child : union.children()) {
            record.with("CHIL", "@" + individualXref(child.childId()) + "@");
        }
        for (EventResponse event : index.familyEvents().getOrDefault(union.familyId(), List.of())) {
            record.with(event(event, index));
        }
        for (CitationResponse citation : index.familyCitations().getOrDefault(union.familyId(), List.of())) {
            record.with(citation(citation));
        }
        return record;
    }

    /**
     * Renders one source as a SOUR record.
     *
     * @param source the source
     * @param repositoryXrefs the identifier of each repository record, by repository name
     * @return the SOUR record
     */
    private static GedcomNode source(SourceResponse source, Map<String, String> repositoryXrefs) {
        // PUBL, not TEXT: in both 5.5.1 and 7.0 a source's TEXT is what it says, not when it dates from.
        GedcomNode record = GedcomNode.record(sourceXref(source.id()), "SOUR")
                .with("TITL", source.title())
                .with("AUTH", source.author())
                .with("PUBL", source.dateText())
                .with(SOURCE_TYPE_TAG, source.type() == null ? null : source.type().name())
                .with("NOTE", source.notes());

        String xref = source.repository() == null ? null : repositoryXrefs.get(source.repository().strip());
        // GEDCOM 7 defines SOUR.REPO as a pointer, so the name lives in its own REPO record.
        return record.with("REPO", xref == null ? null : "@" + xref + "@");
    }

    /**
     * Renders each distinct repository name as its own REPO record, which is what a SOUR may point at.
     *
     * @param sources every source
     * @param out collects the REPO records
     * @return the identifier of each REPO record, by repository name
     */
    private static Map<String, String> repositoryRecords(List<SourceResponse> sources, List<GedcomNode> out) {
        Map<String, String> xrefByName = new LinkedHashMap<>();
        for (SourceResponse source : sources) {
            String name = source.repository() == null ? null : source.repository().strip();
            if (name == null || name.isBlank() || xrefByName.containsKey(name)) {
                continue;
            }
            String xref = "R" + (xrefByName.size() + 1);
            xrefByName.put(name, xref);
            out.add(GedcomNode.record(xref, "REPO").with("NAME", name));
        }
        return xrefByName;
    }

    /**
     * Renders the pointer that attaches a citation to the record it backs up.
     *
     * @param citation the citation
     * @return the SOUR pointer node
     */
    private static GedcomNode citation(CitationResponse citation) {
        return GedcomNode.of("SOUR", "@" + sourceXref(citation.sourceId()) + "@")
                .with("PAGE", citation.locator())
                .with(citation.quote() == null || citation.quote().isBlank()
                        ? null
                        : GedcomNode.of("DATA", null).with("TEXT", citation.quote()));
    }

    /**
     * Groups events by the record they hang off.
     *
     * @param events every event
     * @param subjectType which kind of subject to keep
     * @return the events of that kind, grouped by subject id
     */
    private static Map<Long, List<EventResponse>> eventsBySubject(
            List<EventResponse> events, EventSubjectType subjectType) {
        return events.stream()
                .filter(event -> event.subjectType() == subjectType)
                .collect(Collectors.groupingBy(EventResponse::subjectId));
    }

    /**
     * Groups citations by the record they back up.
     *
     * @param citations every citation
     * @param targetType which kind of target to keep
     * @return the citations of that kind, grouped by target id
     */
    private static Map<Long, List<CitationResponse>> citationsByTarget(
            List<CitationResponse> citations, CitationTargetType targetType) {
        return citations.stream()
                .filter(citation -> citation.targetType() == targetType)
                .collect(Collectors.groupingBy(CitationResponse::targetId));
    }

    /**
     * Renders today's date the way a GEDCOM header writes one.
     *
     * @param today the day to render
     * @return the DATE payload
     */
    private static String gedcomToday(LocalDate today) {
        // Through GedcomDates, which owns how a GEDCOM date is spelled and is the class that has the tests.
        return GedcomDates.part(today.getYear(), today.getMonthValue(), today.getDayOfMonth());
    }

    /**
     * Builds the cross-reference identifier of a person.
     *
     * @param id person id
     * @return the identifier without at-signs
     */
    private static String individualXref(Long id) {
        return "I" + id;
    }

    /**
     * Builds the cross-reference identifier of a union.
     *
     * @param id family id
     * @return the identifier without at-signs
     */
    private static String familyXref(Long id) {
        return "F" + id;
    }

    /**
     * Builds the cross-reference identifier of a source.
     *
     * @param id source id
     * @return the identifier without at-signs
     */
    private static String sourceXref(Long id) {
        return "S" + id;
    }
}
