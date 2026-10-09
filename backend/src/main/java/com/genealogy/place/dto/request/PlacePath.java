package com.genealogy.place.dto.request;

import com.genealogy.common.model.PlaceType;
import java.math.BigDecimal;
import java.util.List;

/**
 * A place named by its whole path, as a GEDCOM `PLAC` writes it, to be found or created level by level.
 *
 * @param names each level's name, most specific first
 * @param levels each level's type, index for index with `names`, or empty when the file does not say
 * @param latitude the most specific place's latitude, or null
 * @param longitude the most specific place's longitude, or null
 */
public record PlacePath(List<String> names, List<PlaceType> levels, BigDecimal latitude, BigDecimal longitude) {
}
