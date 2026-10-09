package com.genealogy.gedcom.domain;

import com.genealogy.common.model.PlaceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads and writes the parts of a GEDCOM place that are not its name: its levels and its coordinates. */
public final class GedcomPlaces {

    private static final String FORM_SEPARATOR = ", ";
    // NUMERIC(9, 6) in V3 and V4: a seventh decimal would fail the insert rather than be rounded by it.
    private static final int COORDINATE_SCALE = 6;
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    // A foreign file's FORM is free text; these are the jurisdiction titles common enough to read as a level.
    private static final Map<String, PlaceType> TITLES = Map.ofEntries(
            Map.entry("thôn", PlaceType.VILLAGE),
            Map.entry("làng", PlaceType.VILLAGE),
            Map.entry("village", PlaceType.VILLAGE),
            Map.entry("xã", PlaceType.WARD),
            Map.entry("phường", PlaceType.WARD),
            Map.entry("ward", PlaceType.WARD),
            Map.entry("commune", PlaceType.WARD),
            Map.entry("huyện", PlaceType.DISTRICT),
            Map.entry("quận", PlaceType.DISTRICT),
            Map.entry("district", PlaceType.DISTRICT),
            Map.entry("county", PlaceType.DISTRICT),
            Map.entry("tỉnh", PlaceType.PROVINCE),
            Map.entry("province", PlaceType.PROVINCE),
            Map.entry("state", PlaceType.PROVINCE),
            Map.entry("quốc gia", PlaceType.COUNTRY),
            Map.entry("nước", PlaceType.COUNTRY),
            Map.entry("country", PlaceType.COUNTRY));

    /** Not instantiable. */
    private GedcomPlaces() {
    }

    /**
     * Renders a place's levels as the FORM payload that goes beside its PLAC.
     *
     * @param levels each level of the path, most specific first
     * @return the FORM payload
     */
    public static String form(List<PlaceType> levels) {
        return levels.stream().map(PlaceType::name).collect(Collectors.joining(FORM_SEPARATOR));
    }

    /**
     * Reads a FORM payload into one level per component of the path it describes.
     *
     * @param form the FORM payload, or null
     * @param components how many comma-separated components the PLAC has
     * @return the levels, OTHER for a title not recognised, or empty when there is no FORM or it does not line up
     */
    public static List<PlaceType> levels(String form, int components) {
        if (form == null || form.isBlank()) {
            return List.of();
        }
        String[] titles = form.split(",", -1);
        // A FORM that does not line up with its PLAC says nothing reliable about any single component.
        if (titles.length != components) {
            return List.of();
        }
        List<PlaceType> levels = new ArrayList<>();
        for (String title : titles) {
            levels.add(levelOf(title));
        }
        return levels;
    }

    /**
     * Reads one jurisdiction title, whether our own export wrote it or a foreign one did.
     *
     * @param title the title
     * @return the level, OTHER when it names none this app models
     */
    private static PlaceType levelOf(String title) {
        String key = title.strip().toLowerCase(Locale.ROOT);
        try {
            return PlaceType.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notOurs) {
            return TITLES.getOrDefault(key, PlaceType.OTHER);
        }
    }

    /**
     * Renders a latitude the way GEDCOM's MAP.LATI spells it.
     *
     * @param latitude degrees, north positive
     * @return e.g. "N21.028511"
     */
    public static String latitude(BigDecimal latitude) {
        return (latitude.signum() < 0 ? "S" : "N") + latitude.abs().stripTrailingZeros().toPlainString();
    }

    /**
     * Renders a longitude the way GEDCOM's MAP.LONG spells it.
     *
     * @param longitude degrees, east positive
     * @return e.g. "E105.804817"
     */
    public static String longitude(BigDecimal longitude) {
        return (longitude.signum() < 0 ? "W" : "E") + longitude.abs().stripTrailingZeros().toPlainString();
    }

    /**
     * Reads a MAP.LATI payload.
     *
     * @param value the payload, or null
     * @return degrees north, or null when absent, malformed or out of range
     */
    public static BigDecimal parseLatitude(String value) {
        return parse(value, 'N', 'S', MAX_LATITUDE);
    }

    /**
     * Reads a MAP.LONG payload.
     *
     * @param value the payload, or null
     * @return degrees east, or null when absent, malformed or out of range
     */
    public static BigDecimal parseLongitude(String value) {
        return parse(value, 'E', 'W', MAX_LONGITUDE);
    }

    /**
     * Reads a signed or hemisphere-prefixed coordinate.
     *
     * @param value the payload, or null
     * @param positive the hemisphere letter that means positive
     * @param negative the hemisphere letter that means negative
     * @param limit the largest magnitude allowed
     * @return the degrees, or null when absent, malformed or out of range
     */
    private static BigDecimal parse(String value, char positive, char negative, BigDecimal limit) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip().toUpperCase(Locale.ROOT).replace(',', '.');
        boolean negated = false;
        if (text.charAt(0) == positive || text.charAt(0) == negative) {
            negated = text.charAt(0) == negative;
            text = text.substring(1).strip();
        }
        // Plain decimals only: BigDecimal reads "1E999999999", and setScale on that exhausts memory (§8.12 #9).
        if (!text.matches("[+-]?\\d{1,3}(\\.\\d{1,20})?")) {
            return null;
        }
        try {
            BigDecimal degrees = new BigDecimal(text).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
            degrees = negated ? degrees.negate() : degrees;
            return degrees.abs().compareTo(limit) > 0 ? null : degrees;
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
