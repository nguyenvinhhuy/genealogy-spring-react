package com.genealogy.person.dto.response;

import com.genealogy.common.model.Gender;

/**
 * The minimum a person needs to be drawn as a node.
 *
 * @param id person id
 * @param displayName the primary name, rendered surname-first
 * @param gender recorded sex
 * @param generation đời, or null when the parentage graph has not placed them
 * @param living whether the person is treated as living
 */
public record PersonNodeResponse(
        Long id,
        String displayName,
        Gender gender,
        Integer generation,
        boolean living) {
}
