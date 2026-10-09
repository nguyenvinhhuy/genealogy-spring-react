package com.genealogy.gedcom.controller;

import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.security.AuthPrincipal;
import com.genealogy.common.util.VietnamTime;
import com.genealogy.common.web.FileDownloads;
import com.genealogy.gedcom.dto.response.GedcomImportResponse;
import com.genealogy.gedcom.service.GedcomService;
import io.swagger.v3.oas.annotations.Operation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoints for moving the gia phả in and out as GEDCOM. */
@RestController
@RequestMapping("/api/v1/gedcom")
@RequiredArgsConstructor
public class GedcomController {


    private final GedcomService gedcomService;

    /**
     * Downloads the whole clan as a GEDCOM 7.0 file.
     *
     * @return the file as an attachment
     */
    @GetMapping("/export")
    @Operation(summary = "Export the whole gia phả as GEDCOM 7.0")
    public ResponseEntity<Resource> export() {
        byte[] body = gedcomService.export().getBytes(StandardCharsets.UTF_8);
        return FileDownloads.attachment(body, "gia-pha-" + VietnamTime.today() + ".ged", MediaType.TEXT_PLAIN);
    }

    /**
     * Reads an uploaded GEDCOM file into the clan; restricted to ADMIN in {@code SecurityConfig}.
     *
     * @param file the uploaded file, GEDCOM 5.5.1 or 7.0
     * @param principal the member running the import
     * @return what was created, skipped and guessed
     */
    @PostMapping("/import")
    @Operation(summary = "Import a GEDCOM file into the gia phả")
    public GedcomImportResponse importFile(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthPrincipal principal) {
        if (file.isEmpty()) {
            throw new BadRequestException("Tệp GEDCOM rỗng");
        }
        // No size check here: the multipart resolver rejects first, and GlobalExceptionHandler renders that.
        return gedcomService.importFile(read(file), principal.id());
    }

    /**
     * Reads an uploaded file as UTF-8 text.
     *
     * @param file the uploaded file
     * @return a reader over the file contents
     */
    private static BufferedReader read(MultipartFile file) {
        try {
            // GEDCOM 7.0 is UTF-8 by definition; a 1990s ANSEL file needs a converter, not a guess here.
            // Streamed, not read whole: a 20 MB upload held the bytes, a String and a line array all at once.
            return new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new BadRequestException("Không đọc được tệp GEDCOM: " + failure.getMessage());
        }
    }
}
