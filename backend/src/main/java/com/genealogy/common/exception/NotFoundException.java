package com.genealogy.common.exception;

import org.springframework.http.HttpStatus;

/** Signals a resource not found condition. */
public class NotFoundException extends ApiException {

    /**
     * Creates the exception with the given explanation.
     *
     * @param detail explanation of this specific occurrence
     */
    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ProblemMessages.TITLE_NOT_FOUND, detail);
    }
}
