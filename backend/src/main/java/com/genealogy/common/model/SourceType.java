package com.genealogy.common.model;

/** What kind of evidence a source is. */
// In common/model: `gedcom` names it too, and §4 forbids importing another feature's domain for it.
public enum SourceType {

    // Gia phả cũ: an earlier written genealogy, often in chữ Hán.
    CLAN_BOOK,

    // Lời kể: what a relative remembered and told.
    ORAL,

    // Giấy tờ: a birth certificate, land deed, household register.
    DOCUMENT,

    // Bia mộ: an inscription on a headstone.
    HEADSTONE,

    // Ảnh: a photograph, including a scan of a page.
    PHOTO,

    // A page on the internet.
    WEBSITE,

    // Anything else.
    OTHER
}
