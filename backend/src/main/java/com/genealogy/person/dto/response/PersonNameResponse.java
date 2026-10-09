package com.genealogy.person.dto.response;

import com.genealogy.common.model.PersonNameType;

/**
 * One of a person's names as returned to clients.
 *
 * @param id name id
 * @param type which kind of name this is
 * @param surname ho, or null
 * @param middleName ten dem, or null
 * @param givenName ten
 * @param primary whether this is the person's main name
 * @param display the name rendered surname-first
 */
public record PersonNameResponse(
        Long id,
        PersonNameType type,
        String surname,
        String middleName,
        String givenName,
        boolean primary,
        String display) {
}
