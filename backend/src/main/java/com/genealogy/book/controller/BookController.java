package com.genealogy.book.controller;

import com.genealogy.book.service.BookService;
import com.genealogy.common.util.VietnamTime;
import com.genealogy.common.web.FileDownloads;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the printable gia phả book. */
@RestController
@RequestMapping("/api/v1/book")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    /**
     * Downloads the gia phả as a printable PDF.
     *
     * @param personId only this person and their descendants, or null for the whole clan
     * @return the PDF as an attachment
     */
    @GetMapping
    @Operation(summary = "Download the gia phả as a printable PDF")
    public ResponseEntity<Resource> download(@RequestParam(required = false) Long personId) {
        byte[] body = bookService.render(personId);
        return FileDownloads.attachment(
                body, "gia-pha-" + VietnamTime.today() + ".pdf", MediaType.APPLICATION_PDF);
    }
}
