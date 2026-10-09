package com.genealogy.suggestion.dto.response;

import com.genealogy.common.model.PersonNameType;

/**
 * One name as a suggestion shows it, rendered by the server so no screen writes a second name renderer.
 *
 * @param type which kind of name
 * @param display the name, surname first
 * @param primary whether it is the person's main name
 */
public record ProposedName(PersonNameType type, String display, boolean primary) {
}
