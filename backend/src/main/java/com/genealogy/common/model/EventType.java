package com.genealogy.common.model;

/** What happened; a giỗ is a DEATH on the LUNAR calendar, not a type of its own (§3.3). */
// In common/model: `book`, `quality`, `gedcom` and `search` all name it, and §4 puts those in the kernel.
public enum EventType {

    // Sinh.
    BIRTH(EventSubjectType.PERSON),

    // Mất.
    DEATH(EventSubjectType.PERSON),

    // An táng: when and where the first burial happened, never rewritten when the grave moves.
    BURIAL(EventSubjectType.PERSON),

    // Cải táng: one move of the remains, with its own date and destination; the grave row keeps only where it is now.
    REBURIAL(EventSubjectType.PERSON),

    // Nơi ở.
    RESIDENCE(EventSubjectType.PERSON),

    // Nghề nghiệp.
    OCCUPATION(EventSubjectType.PERSON),

    // Học vấn, khoa bảng.
    EDUCATION(EventSubjectType.PERSON),

    // Anything else worth recording about one person.
    OTHER_PERSON(EventSubjectType.PERSON),

    // Kết hôn.
    MARRIAGE(EventSubjectType.FAMILY),

    // Ly hôn.
    DIVORCE(EventSubjectType.FAMILY),

    // Anything else worth recording about a union.
    OTHER_FAMILY(EventSubjectType.FAMILY);

    private final EventSubjectType subjectType;

    /**
     * Binds an event type to the kind of record it can hang off.
     *
     * @param subjectType the only subject type this event is valid for
     */
    EventType(EventSubjectType subjectType) {
        this.subjectType = subjectType;
    }

    /**
     * Returns the kind of record this event type belongs to.
     *
     * @return the subject type
     */
    public EventSubjectType subjectType() {
        return subjectType;
    }
}
