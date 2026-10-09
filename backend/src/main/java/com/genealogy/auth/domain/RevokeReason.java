package com.genealogy.auth.domain;

/** Why a refresh token stopped being usable. */
public enum RevokeReason {

    // Exchanged for a new pair; presenting it again means someone else holds a copy.
    ROTATED,

    // Its owner signed out of that device.
    LOGOUT,

    // Every session ended at once: a password, role or enabled state changed, or a stolen token was replayed.
    CREDENTIALS
}
