package com.genealogy.tree.dto.response;

/**
 * Where the clan's tree starts when nobody has been picked to centre on.
 *
 * @param personId the thuỷ tổ, or null when no union is recorded yet
 */
public record FounderResponse(Long personId) {
}
