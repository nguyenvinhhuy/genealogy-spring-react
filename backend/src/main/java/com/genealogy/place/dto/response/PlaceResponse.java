package com.genealogy.place.dto.response;

import com.genealogy.common.model.PlaceType;
import java.math.BigDecimal;

/**
 * A place as returned to clients.
 *
 * @param id place id
 * @param name place name
 * @param type administrative level
 * @param parentId enclosing place id, or null
 * @param latitude latitude in degrees, or null
 * @param longitude longitude in degrees, or null
 * @param path the place and every place above it, most specific first, e.g. "Xã A, Huyện B, Tỉnh C"
 * @param version the row's version, sent back with an edit
 */
public record PlaceResponse(
        Long id,
        String name,
        PlaceType type,
        Long parentId,
        BigDecimal latitude,
        BigDecimal longitude,
        String path,
        long version) {
}
