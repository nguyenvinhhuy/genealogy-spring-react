package com.genealogy.quality.domain;

/** The data-quality rules from CLAUDE.md §5.1; each constant carries its own severity. */
public enum IssueCode {

    // A parent chain leads back to the same person; the only rule that is an error, not a warning.
    ANCESTRY_CYCLE(IssueSeverity.ERROR),

    // Died before being born.
    DEATH_BEFORE_BIRTH(IssueSeverity.WARNING),

    // Buried before dying.
    BURIAL_BEFORE_DEATH(IssueSeverity.WARNING),

    // Two recorded births, or two recorded deaths, that cannot both be true.
    CONFLICTING_DATES(IssueSeverity.WARNING),

    // Born before a birth parent was born.
    BORN_BEFORE_PARENT(IssueSeverity.WARNING),

    // Born after the mother had already died.
    BORN_AFTER_MOTHER_DEATH(IssueSeverity.WARNING),

    // Born more than ten months after the father died.
    BORN_LONG_AFTER_FATHER_DEATH(IssueSeverity.WARNING),

    // Born more than ten months after the death of a parent whose sex is unrecorded.
    BORN_LONG_AFTER_PARENT_DEATH(IssueSeverity.WARNING),

    // The mother was under twelve at the birth.
    MOTHER_TOO_YOUNG(IssueSeverity.WARNING),

    // The mother was over fifty-five at the birth.
    MOTHER_TOO_OLD(IssueSeverity.WARNING),

    // Married before being born.
    MARRIED_BEFORE_BIRTH(IssueSeverity.WARNING),

    // Married before the age of thirteen.
    MARRIED_TOO_YOUNG(IssueSeverity.WARNING),

    // Lived longer than a hundred and ten years.
    IMPLAUSIBLE_LIFESPAN(IssueSeverity.WARNING),

    // Born well before the parents' recorded marriage, which is common and not a mistake.
    CHILD_BORN_BEFORE_MARRIAGE(IssueSeverity.INFO),

    // The same person recorded twice.
    POSSIBLE_DUPLICATE(IssueSeverity.INFO);

    private final IssueSeverity severity;

    /**
     * Binds a rule to how seriously it should be taken.
     *
     * @param severity the rule's severity
     */
    IssueCode(IssueSeverity severity) {
        this.severity = severity;
    }

    /**
     * Returns how seriously this rule should be taken.
     *
     * @return the severity
     */
    public IssueSeverity severity() {
        return severity;
    }
}
