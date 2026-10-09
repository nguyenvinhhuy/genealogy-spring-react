package com.genealogy.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the blank-text helpers every form field goes through. */
class BlankTest {

    @Test
    @DisplayName("blank text is stored as null and other text is trimmed")
    void toNullTrimsAndNullsBlank() {
        assertThat(Blank.toNull(null)).isNull();
        assertThat(Blank.toNull("   ")).isNull();
        assertThat(Blank.toNull("  Hà Nội ")).isEqualTo("Hà Nội");
    }

    @Test
    @DisplayName("two different texts are joined by a blank line")
    void joinKeepsBoth() {
        assertThat(Blank.join("Cụ làm nghề thầy thuốc", "Có dựng nhà thờ họ"))
                .isEqualTo("Cụ làm nghề thầy thuốc\n\nCó dựng nhà thờ họ");
    }

    @Test
    @DisplayName("the same text on both sides is kept once, so a doubly imported note is not doubled by a merge")
    void joinKeepsOneCopyOfTheSameText() {
        assertThat(Blank.join("Cụ làm nghề thầy thuốc", " Cụ làm nghề thầy thuốc "))
                .isEqualTo("Cụ làm nghề thầy thuốc");
    }

    @Test
    @DisplayName("a missing side leaves the other as it was")
    void joinSkipsMissingSides() {
        assertThat(Blank.join(null, "A")).isEqualTo("A");
        assertThat(Blank.join("A", " ")).isEqualTo("A");
        assertThat(Blank.join(null, null)).isNull();
    }
}
