package com.genealogy.media.service;

/** Which copy of a stored image a caller wants, with no provider's transform syntax in sight (§3.9). */
public enum ImageSize {

    // A copy at most 600 pixels on its long edge: enough for a gallery tile and a printed portrait.
    THUMBNAIL,

    // The file exactly as it was uploaded.
    ORIGINAL
}
