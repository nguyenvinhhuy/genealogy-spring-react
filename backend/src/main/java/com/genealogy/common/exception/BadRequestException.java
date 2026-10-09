package com.genealogy.common.exception;

import org.springframework.http.HttpStatus;

/** Signals a invalid request condition. */
public class BadRequestException extends ApiException {

    /**
     * Creates the exception with the given explanation.
     *
     * @param detail explanation of this specific occurrence
     */
    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, ProblemMessages.TITLE_BAD_REQUEST, detail);
    }
}
