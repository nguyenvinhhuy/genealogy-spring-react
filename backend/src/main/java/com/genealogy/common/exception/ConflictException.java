package com.genealogy.common.exception;

import org.springframework.http.HttpStatus;

/** Signals a conflict condition. */
public class ConflictException extends ApiException {

    /**
     * Creates the exception with the given explanation.
     *
     * @param detail explanation of this specific occurrence
     */
    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, ProblemMessages.TITLE_CONFLICT, detail);
    }
}
