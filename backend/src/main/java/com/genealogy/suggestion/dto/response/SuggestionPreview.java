package com.genealogy.suggestion.dto.response;

/**
 * What approving a suggestion would change, field by field, built from the same merge approval applies.
 *
 * @param current the person as they are now, or null for a new person
 * @param proposed the person as approval would leave them
 * @param anchor for a new person, whom they would be added to; null for an edit
 */
public record SuggestionPreview(PersonSide current, PersonSide proposed, AnchorView anchor) {
}
