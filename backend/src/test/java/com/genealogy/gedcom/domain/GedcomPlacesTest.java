package com.genealogy.gedcom.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for reading a GEDCOM map coordinate (docs/analysis.md F13). */
class GedcomPlacesTest {

    @Test
    @DisplayName("a hemisphere letter and a plain decimal are read as signed degrees")
    void readsHemispheres() {
        assertThat(GedcomPlaces.parseLatitude("N21.0275")).isEqualByComparingTo(new BigDecimal("21.0275"));
        assertThat(GedcomPlaces.parseLatitude("S21.0275")).isEqualByComparingTo(new BigDecimal("-21.0275"));
        assertThat(GedcomPlaces.parseLongitude("E105.8")).isEqualByComparingTo(new BigDecimal("105.8"));
    }

    @Test
    @DisplayName("an exponent is refused instead of exhausting memory in setScale")
    void refusesAnExponent() {
        assertThat(GedcomPlaces.parseLatitude("N1E999999999")).isNull();
        assertThat(GedcomPlaces.parseLongitude("1e-999999999")).isNull();
    }

    @Test
    @DisplayName("a value out of range or not a number reads as no coordinate")
    void refusesOutOfRange() {
        assertThat(GedcomPlaces.parseLatitude("N91")).isNull();
        assertThat(GedcomPlaces.parseLatitude("abc")).isNull();
        assertThat(GedcomPlaces.parseLatitude(null)).isNull();
    }
}
