package com.genealogy.tree.service;

import com.genealogy.tree.dto.response.KinshipResponse;

/** Works out what one person is to another, in Vietnamese. */
public interface KinshipService {

    /**
     * Names the relationship from one person's point of view.
     *
     * @param fromId the speaker
     * @param toId the person being named
     * @return the relationship
     */
    KinshipResponse describe(Long fromId, Long toId);
}
