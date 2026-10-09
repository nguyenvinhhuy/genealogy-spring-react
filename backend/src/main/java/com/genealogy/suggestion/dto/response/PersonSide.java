package com.genealogy.suggestion.dto.response;

import com.genealogy.common.model.Gender;
import java.util.List;

/**
 * One side of a suggestion's comparison: a person as they are, or as approval would leave them.
 *
 * @param names every name, primary first
 * @param gender the recorded sex, or null when none is given
 * @param birth the date of birth, rendered in Vietnamese, or null
 * @param death the date of death, rendered in Vietnamese, or null
 */
public record PersonSide(List<ProposedName> names, Gender gender, String birth, String death) {
}
