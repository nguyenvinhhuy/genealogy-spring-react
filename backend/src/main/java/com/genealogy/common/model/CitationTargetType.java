package com.genealogy.common.model;

/** What kind of record a citation backs up. */
// In common/model: `merge`, `purge` and `gedcom` all name it, and §4 puts those in the shared kernel.
public enum CitationTargetType {

    // A claim about one person.
    PERSON,

    // A claim about a union.
    FAMILY,

    // A claim about a dated event.
    EVENT,

    // A claim about a grave: a bia mộ is the primary evidence for its owner's dates and resting place.
    GRAVE
}
