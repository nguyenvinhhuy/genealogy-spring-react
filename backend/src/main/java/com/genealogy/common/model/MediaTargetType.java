package com.genealogy.common.model;

/** What kind of record a photo or scan belongs to. */
public enum MediaTargetType {

    // A photo of one person.
    PERSON,

    // A photo of a couple or their wedding.
    FAMILY,

    // A photo of a grave, which con cháu use to find it again.
    GRAVE,

    // A scan of a source, such as the pages of the old gia phả, which belong to no one person.
    SOURCE
}
