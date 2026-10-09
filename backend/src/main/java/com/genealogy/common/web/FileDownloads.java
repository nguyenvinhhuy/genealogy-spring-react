package com.genealogy.common.web;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Builds the response that hands a generated file to a browser as a download. */
// One copy: the GEDCOM export and the book had the same six lines, and a third download would have made three.
public final class FileDownloads {

    /** Not instantiable. */
    private FileDownloads() {
    }

    /**
     * Wraps bytes as an attachment response under the given filename.
     *
     * @param body the file contents
     * @param filename what the browser should call the saved file
     * @param contentType the media type to declare
     * @return the response
     */
    public static ResponseEntity<Resource> attachment(byte[] body, String filename, MediaType contentType) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        // With a charset Spring adds filename*=UTF-8'', so "gia-phả-chi-hai.pdf" is not mangled.
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(contentType)
                .contentLength(body.length)
                .body(new ByteArrayResource(body));
    }
}
