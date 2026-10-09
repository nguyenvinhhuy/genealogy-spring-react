package com.genealogy.suggestion.domain;

/** What a suggestion is asking for. */
public enum SuggestionKind {

    // Sửa một người đã có: the payload is the proposed replacement.
    UPDATE,

    // Thêm người mới: the payload is the person to create.
    CREATE,

    // Góp ý: something worth knowing that the suggester cannot express as an edit.
    NOTE
}
