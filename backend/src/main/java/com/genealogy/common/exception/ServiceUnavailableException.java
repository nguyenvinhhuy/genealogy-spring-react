package com.genealogy.common.exception;

import org.springframework.http.HttpStatus;

/** Signals that a service the app depends on, such as object storage, did not answer. */
// A 503 with a fixed message: the provider's own text names internal hosts and buckets (§8.9 #10).
public class ServiceUnavailableException extends ApiException {

    /**
     * Creates the exception with the given explanation and cause.
     *
     * @param detail explanation of this specific occurrence, safe to show to any caller
     * @param cause what went wrong underneath, logged and never shown
     */
    public ServiceUnavailableException(String detail, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ProblemMessages.TITLE_UNAVAILABLE, detail, cause);
    }
}
