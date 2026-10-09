package com.genealogy.merge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for merging one person into another.
 *
 * @param targetId the person to keep — their id, primary name and đời survive
 * @param duplicateId the person to absorb and then delete
 * @param reason why these two are the same person; recorded in the audit trail as the basis (§3.8)
 */
public record MergeRequest(
        @NotNull Long targetId,
        @NotNull Long duplicateId,
        @NotBlank String reason) {
}
