package com.genealogy.source.dto.response;

import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.SourceType;

/**
 * One citation, with enough of its source to render without a second call.
 *
 * @param id citation id
 * @param sourceId the source being cited
 * @param sourceTitle that source's title
 * @param sourceType what kind of evidence it is
 * @param targetType what kind of record it backs up
 * @param targetId the record id
 * @param locator where inside the source
 * @param quote what the source actually says
 * @param version the row version, sent back on the next edit
 */
public record CitationResponse(
        Long id,
        Long sourceId,
        String sourceTitle,
        SourceType sourceType,
        CitationTargetType targetType,
        Long targetId,
        String locator,
        String quote,
        long version) {
}
