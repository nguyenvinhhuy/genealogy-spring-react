package com.genealogy.member.dto.request;

/** The limits every password field shares. */
public final class Passwords {

    // The shortest password any form accepts.
    public static final int MIN_LENGTH = 8;

    // BCrypt reads 72 bytes and no more, so no password may be longer in characters either.
    public static final int MAX_BYTES = 72;

    /** Not instantiable. */
    private Passwords() {
    }
}
