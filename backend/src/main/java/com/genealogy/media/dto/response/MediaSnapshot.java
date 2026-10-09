package com.genealogy.media.dto.response;

import com.genealogy.common.model.MediaTargetType;
import com.genealogy.media.domain.MediaKind;

/**
 * What the audit trail keeps of one file.
 *
 * @param id media id
 * @param targetType what kind of record the file is attached to
 * @param targetId the record id
 * @param kind what the file is
 * @param filename what the file was called
 * @param contentType its media type, read from its bytes
 * @param sizeBytes how many bytes it has
 * @param caption what is written under it
 * @param sortOrder its place among the record's files
 */
// Never its URL: a signed URL is a bearer link (§3.9), and a revision is read for years.
public record MediaSnapshot(
        Long id,
        MediaTargetType targetType,
        Long targetId,
        MediaKind kind,
        String filename,
        String contentType,
        long sizeBytes,
        String caption,
        int sortOrder) {
}
