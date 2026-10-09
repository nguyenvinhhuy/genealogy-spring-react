package com.genealogy.suggestion.dto.request;

import com.genealogy.suggestion.domain.SuggestionKind;
import com.genealogy.suggestion.domain.SuggestionTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for offering a suggestion.
 *
 * @param targetType what kind of record this is about
 * @param targetId the record being changed; null for a suggested new person
 * @param kind what is being asked for
 * @param person what is proposed, required for UPDATE and CREATE and rejected for NOTE
 * @param message why the member is asking for this, in their own words
 */
public record SuggestionRequest(
        @NotNull SuggestionTargetType targetType,
        Long targetId,
        @NotNull SuggestionKind kind,
        @Valid SuggestedPerson person,
        @NotBlank @Size(max = MAX_MESSAGE) String message) {

    // Long enough for a whole family story, short enough that one member cannot flood the queue (§8.9 #44).
    public static final int MAX_MESSAGE = 4000;
}
