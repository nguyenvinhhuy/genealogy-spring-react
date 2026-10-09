package com.genealogy.source.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for folding one source into another.
 *
 * @param duplicateId the source whose citations move and which is then deleted
 * @param targetId the source that is kept
 * @param reason why the two are the same source, recorded in the change history
 */
public record SourceMergeRequest(
        @NotNull Long duplicateId,
        @NotNull Long targetId,
        @NotBlank @Size(max = 2000) String reason) {
}
