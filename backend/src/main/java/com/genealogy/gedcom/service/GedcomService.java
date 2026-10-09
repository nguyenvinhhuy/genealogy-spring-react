package com.genealogy.gedcom.service;

import com.genealogy.gedcom.dto.response.GedcomImportResponse;
import java.io.BufferedReader;

/** GEDCOM import and export (docs/analysis.md F13). */
public interface GedcomService {

    /**
     * Renders the whole clan as a GEDCOM 7.0 file.
     *
     * @return the file contents
     */
    String export();

    /**
     * Reads a GEDCOM file and adds everything in it to the clan.
     *
     * @param source the file contents, GEDCOM 5.5.1 or 7.0
     * @param actorId id of the member running the import
     * @return what was created, skipped and guessed
     */
    GedcomImportResponse importFile(BufferedReader source, Long actorId);
}
