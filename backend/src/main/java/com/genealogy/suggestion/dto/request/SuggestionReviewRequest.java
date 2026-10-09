package com.genealogy.suggestion.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for approving or rejecting a suggestion.
 *
 * @param approve true to accept and apply it, false to turn it down
 * @param reviewNote why — required when turning one down, so the suggester is not left guessing
 */
public record SuggestionReviewRequest(@NotNull Boolean approve, @Size(max = 2000) String reviewNote) {
}
