package com.genealogy.suggestion.dto.response;

import com.genealogy.family.dto.request.AddRelationRequest;

/**
 * Whom a suggested new person would be added to, named for the screen.
 *
 * @param personId the person the new one is a child or spouse of
 * @param personName that person's display name, or null when they are gone
 * @param kind whether the new person is a child or a spouse
 * @param familyId for a child, the union they join, or null for a new one-parent union
 */
public record AnchorView(Long personId, String personName, AddRelationRequest.Kind kind, Long familyId) {
}
