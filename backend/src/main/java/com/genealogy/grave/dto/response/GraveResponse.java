package com.genealogy.grave.dto.response;

import com.genealogy.common.model.GraveKind;
import java.math.BigDecimal;

/**
 * A mộ phần as returned to clients.
 *
 * @param id grave id
 * @param personId whose grave it is
 * @param kind a grave (mộ) or a plot built for someone still alive (sinh phần)
 * @param placeId the enclosing place, or null
 * @param plot free-text plot reference
 * @param latitude latitude in degrees, or null
 * @param longitude longitude in degrees, or null
 * @param notes free-text notes
 * @param version the row's version, sent back with an edit
 */
public record GraveResponse(
        Long id,
        Long personId,
        GraveKind kind,
        Long placeId,
        String plot,
        BigDecimal latitude,
        BigDecimal longitude,
        String notes,
        long version) {
}
