package com.genealogy.common.model;

/** What kind of record an event hangs off. */
public enum EventSubjectType {

    // The event belongs to one person (birth, death, burial, ...).
    PERSON,

    // The event belongs to a union (marriage, divorce).
    FAMILY
}
