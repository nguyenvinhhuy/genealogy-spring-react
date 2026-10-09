package com.genealogy.gedcom.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for GEDCOM line parsing and rendering. */
class GedcomNodeTest {

    @Test
    @DisplayName("a record keeps its cross-reference, tag and nesting")
    void parsesNesting() {
        List<GedcomNode> records = GedcomNode.parse("""
                0 @I1@ INDI
                1 NAME Văn Ánh /Nguyễn/
                2 GIVN Văn Ánh
                1 SEX M
                0 TRLR
                """);

        assertThat(records).hasSize(2);
        GedcomNode individual = records.getFirst();
        assertThat(individual.getXref()).isEqualTo("I1");
        assertThat(individual.getTag()).isEqualTo("INDI");
        assertThat(individual.childValue("NAME")).contains("Văn Ánh /Nguyễn/");
        assertThat(individual.child("NAME").orElseThrow().childValue("GIVN")).contains("Văn Ánh");
        assertThat(individual.childValue("SEX")).contains("M");
        assertThat(records.getLast().getTag()).isEqualTo("TRLR");
    }

    @Test
    @DisplayName("CONT restores a line break and CONC joins with nothing")
    void foldsContinuations() {
        List<GedcomNode> records = GedcomNode.parse("""
                0 @S1@ SOUR
                1 NOTE dòng một
                2 CONT dòng hai
                2 CONC  nối liền
                """);

        assertThat(records.getFirst().childValue("NOTE")).contains("dòng một\ndòng hai nối liền");
    }

    @Test
    @DisplayName("a rendered multi-line value comes back identical after parsing")
    void roundTripsMultiLineValue() {
        GedcomNode written = GedcomNode.record("S1", "SOUR").with("NOTE", "dòng một\ndòng hai");
        StringBuilder out = new StringBuilder();
        written.render(0, out);

        assertThat(GedcomNode.parse(out.toString()).getFirst().childValue("NOTE"))
                .contains("dòng một\ndòng hai");
    }

    @Test
    @DisplayName("a level jump back to 0 closes every open record")
    void closesOpenRecords() {
        List<GedcomNode> records = GedcomNode.parse("""
                0 @I1@ INDI
                1 BIRT
                2 DATE 1890
                0 @I2@ INDI
                1 SEX F
                """);

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().child("BIRT").orElseThrow().childValue("DATE")).contains("1890");
        assertThat(records.getLast().childValue("SEX")).contains("F");
        assertThat(records.getLast().getChildren()).hasSize(1);
    }

    @Test
    @DisplayName("a line that is not GEDCOM at all is skipped rather than thrown on")
    void skipsJunkLines() {
        List<GedcomNode> records = GedcomNode.parse("""
                this is not gedcom
                0 @I1@ INDI

                1 SEX M
                """);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().childValue("SEX")).contains("M");
    }

    @Test
    @DisplayName("a value-less tag renders without a trailing space")
    void rendersValuelessTag() {
        StringBuilder out = new StringBuilder();
        GedcomNode.of("BIRT", null).with("DATE", "1890").render(1, out);

        assertThat(out.toString()).isEqualTo("1 BIRT\n2 DATE 1890\n");
    }
}
