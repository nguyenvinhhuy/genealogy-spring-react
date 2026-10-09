package com.genealogy.suggestion.domain;

/** Where a suggestion is in the review queue. */
public enum SuggestionStatus {

    // Chờ duyệt: nobody has looked at it yet.
    PENDING,

    // Đã duyệt: accepted, and any payload it carried has been applied.
    APPROVED,

    // Đã từ chối: turned down, with the reason recorded.
    REJECTED
}
