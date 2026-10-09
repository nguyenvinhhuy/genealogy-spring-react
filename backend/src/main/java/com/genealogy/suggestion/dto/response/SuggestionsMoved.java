package com.genealogy.suggestion.dto.response;

/**
 * What moving a duplicate's suggestions onto the person kept did.
 *
 * @param moved how many suggestions now point at the person kept
 * @param turnedIntoNotes how many pending edits became notes, because they were proposed against the duplicate
 */
public record SuggestionsMoved(int moved, int turnedIntoNotes) {
}
