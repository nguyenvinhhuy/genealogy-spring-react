package com.genealogy.media.domain;

/** What a stored file is, which decides where it is shown. */
public enum MediaKind {

    // Ảnh chân dung: the one photo per record that the tree and the book use.
    PORTRAIT,

    // Ảnh chụp/scan gia phả cũ: a page of the old clan book, usually A3.
    SCAN,

    // Ảnh thường: anything else the family photographed.
    PHOTO,

    // Giấy tờ: a birth certificate, land deed, household register.
    DOCUMENT
}
