package com.genealogy.suggestion.dto.request;

import com.genealogy.family.dto.request.AddRelationRequest;
import jakarta.validation.constraints.NotNull;

/**
 * Whom a suggested new person belongs to.
 *
 * @param personId the person the new one is a child or spouse of
 * @param kind whether the new person is that person's child or spouse
 * @param familyId for a child, the union to link them into, or null for a new one-parent union
 */
public record SuggestedAnchor(@NotNull Long personId, @NotNull AddRelationRequest.Kind kind, Long familyId) {
}
