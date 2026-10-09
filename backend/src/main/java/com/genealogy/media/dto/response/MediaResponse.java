package com.genealogy.media.dto.response;

import com.genealogy.common.model.MediaTargetType;
import com.genealogy.media.domain.MediaKind;
import java.time.Instant;

/**
 * One photo or scan as returned to clients.
 *
 * @param id media id
 * @param targetType what kind of record it belongs to
 * @param targetId the record id
 * @param kind what the file is
 * @param url where a browser can fetch the original; short-lived when the provider signs its URLs
 * @param thumbnailUrl where a browser can fetch a small copy for a gallery, or the original when there is none
 * @param contentType the stored media type, read from the file's bytes
 * @param sizeBytes how big the file is
 * @param filename what it was called when it was uploaded
 * @param caption what the family wrote about it
 * @param sortOrder its position in the record's gallery
 * @param uploadedBy id of the member who uploaded it
 * @param uploadedByName that member's name, or null when they are gone
 * @param createdAt when it was uploaded
 * @param version the row version an edit must be sent back with
 */
public record MediaResponse(
        Long id,
        MediaTargetType targetType,
        Long targetId,
        MediaKind kind,
        String url,
        String thumbnailUrl,
        String contentType,
        long sizeBytes,
        String filename,
        String caption,
        int sortOrder,
        Long uploadedBy,
        String uploadedByName,
        Instant createdAt,
        long version) {
}
