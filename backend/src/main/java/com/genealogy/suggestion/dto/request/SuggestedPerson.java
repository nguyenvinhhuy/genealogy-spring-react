package com.genealogy.suggestion.dto.request;

import com.genealogy.common.model.Gender;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.person.dto.request.PersonNameRequest;
import jakarta.validation.Valid;
import java.util.List;

/**
 * What a member proposes about a person: only the fields a con cháu can know and a reviewer can check.
 *
 * @param names the proposed names, or null to leave them as they are
 * @param gender the proposed sex, or null to leave it as it is
 * @param birth the proposed date of birth, or null to leave it as it is
 * @param death the proposed date of death, or null to leave it as it is
 * @param anchor for a new person, whom they are a child or spouse of; null for an edit
 */
// No chi and no notes: a MEMBER cannot read a living person's, and must not overwrite them blind (§8.9 D1).
public record SuggestedPerson(
        List<@Valid PersonNameRequest> names,
        Gender gender,
        @Valid GenealogyDateRequest birth,
        @Valid GenealogyDateRequest death,
        @Valid SuggestedAnchor anchor) {
}
