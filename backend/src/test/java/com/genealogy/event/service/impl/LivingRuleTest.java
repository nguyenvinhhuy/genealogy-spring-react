package com.genealogy.event.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.common.model.DateModifier;
import com.genealogy.common.model.GenealogyDate;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the living flag that living-person redaction keys on (CLAUDE.md §3.4, §3.6). */
class LivingRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    /**
     * Builds a year-only birth date.
     *
     * @param modifier the precision modifier
     * @param year the year recorded
     * @return the date
     */
    private static GenealogyDate born(DateModifier modifier, Integer year) {
        GenealogyDate date = new GenealogyDate();
        date.setModifier(modifier);
        date.setYear(year);
        return date;
    }

    @Test
    @DisplayName("a recorded death or burial means not living, whatever the birth says")
    void recordedDeathOrBurialIsNotLiving() {
        assertThat(LivingRule.isLiving(true, List.of(born(DateModifier.EXACT, 2000)), TODAY)).isFalse();
    }

    @Test
    @DisplayName("nothing recorded at all is treated as living: redacting is the safe mistake")
    void nothingRecordedIsLiving() {
        assertThat(LivingRule.isLiving(false, List.of(), TODAY)).isTrue();
    }

    @Test
    @DisplayName("a birth that can only fall over a century ago is not living")
    void bornOverACenturyAgo() {
        assertThat(LivingRule.isLiving(false, List.of(born(DateModifier.EXACT, 1900)), TODAY)).isFalse();
    }

    @Test
    @DisplayName("a year-only birth exactly a century back still counts as living until the whole year has passed")
    void yearOnlyBirthOnTheBoundary() {
        // "1926" may be 31 December 1926, which is under a hundred years before 24 September 2026.
        assertThat(LivingRule.isLiving(false, List.of(born(DateModifier.EXACT, 1926)), TODAY)).isTrue();
    }

    @Test
    @DisplayName("an open-ended AFTER birth keeps the person living, because it has no latest possible day")
    void afterWithNoUpperBoundIsLiving() {
        assertThat(LivingRule.isLiving(false, List.of(born(DateModifier.AFTER, 1850)), TODAY)).isTrue();
    }

    @Test
    @DisplayName("an ABOUT birth is widened, so khoảng 1924 is not yet presumed dead")
    void aboutIsWidened() {
        assertThat(LivingRule.isLiving(false, List.of(born(DateModifier.ABOUT, 1924)), TODAY)).isTrue();
    }

    @Test
    @DisplayName("of two recorded births, the later one decides: either may be the true one")
    void conflictingBirthsFailClosed() {
        List<GenealogyDate> births = List.of(born(DateModifier.EXACT, 1880), born(DateModifier.EXACT, 1990));

        assertThat(LivingRule.isLiving(false, births, TODAY)).isTrue();
    }

    @Test
    @DisplayName("a birth with no year at all keeps the person living")
    void birthWithNoYearIsLiving() {
        assertThat(LivingRule.isLiving(false, List.of(born(DateModifier.EXACT, null)), TODAY)).isTrue();
    }
}
