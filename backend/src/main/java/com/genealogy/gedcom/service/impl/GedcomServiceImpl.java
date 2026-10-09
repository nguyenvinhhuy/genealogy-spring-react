package com.genealogy.gedcom.service.impl;

import com.genealogy.branch.service.BranchService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.event.service.EventService;
import com.genealogy.family.service.FamilyService;
import com.genealogy.gedcom.domain.GedcomNode;
import com.genealogy.gedcom.dto.response.GedcomImportResponse;
import com.genealogy.gedcom.service.GedcomService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import com.genealogy.source.service.SourceService;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link GedcomService}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GedcomServiceImpl implements GedcomService {

    private final PersonService personService;
    private final FamilyService familyService;
    private final EventService eventService;
    private final SourceService sourceService;
    private final PlaceService placeService;
    private final BranchService branchService;
    private final GraveService graveService;

    /**
     * Renders the whole clan as a GEDCOM 7.0 file.
     *
     * @return the file contents
     */
    @Override
    // One snapshot for all eight reads: under READ COMMITTED an edit between two of them exports a torn file.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String export() {
        return GedcomExporter.render(
                personService.findAllDetails(),
                familyService.findAllUnions(),
                eventService.findAll(),
                sourceService.findAllSources(),
                sourceService.findAllCitations(),
                placeService.findAll(),
                branchService.findAll(),
                graveService.findAll());
    }

    /**
     * Reads a GEDCOM file and adds everything in it to the clan, record by record.
     *
     * @param source the file contents, GEDCOM 5.5.1 or 7.0
     * @param actorId id of the member running the import
     * @return what was created, skipped and guessed
     */
    @Override
    // NOT_SUPPORTED, not a bare removal: the class-level readOnly transaction would otherwise refuse every insert.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GedcomImportResponse importFile(BufferedReader source, Long actorId) {
        List<GedcomNode> records;
        try (BufferedReader reader = source) {
            records = GedcomNode.parse(reader);
        } catch (IOException failure) {
            throw new BadRequestException("Không đọc được tệp GEDCOM: " + failure.getMessage());
        }
        if (records.isEmpty()) {
            throw new BadRequestException("Tệp không phải GEDCOM, hoặc không có bản ghi nào đọc được");
        }

        GedcomImportResponse result =
                new GedcomImporter(personService, familyService, eventService, placeService, sourceService,
                        branchService, graveService)
                        .run(records, actorId);
        log.info(
                "GEDCOM import by member {}: {} people, {} unions, {} events, {} skipped",
                actorId,
                result.personsCreated(),
                result.familiesCreated(),
                result.eventsCreated(),
                result.skipped());
        return result;
    }
}
