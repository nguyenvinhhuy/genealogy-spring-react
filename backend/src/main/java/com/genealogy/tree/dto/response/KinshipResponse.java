package com.genealogy.tree.dto.response;

import com.genealogy.tree.domain.KinshipSide;

/**
 * What one person is to another, in Vietnamese: {@code term} is the word {@code fromId} calls {@code toId}.
 *
 * @param fromId the speaker
 * @param toId the person being named
 * @param term the Vietnamese kinship term, or null when no blood path was found
 * @param stepsUp generations from the speaker up to the common ancestor
 * @param stepsDown generations from the common ancestor down to the target
 * @param commonAncestorId the lowest common ancestor, or null for a spouse or no relation
 * @param side the parent the path runs through, or null when it does not apply or cannot be told
 */
public record KinshipResponse(
        Long fromId,
        Long toId,
        String term,
        int stepsUp,
        int stepsDown,
        Long commonAncestorId,
        KinshipSide side) {
}
