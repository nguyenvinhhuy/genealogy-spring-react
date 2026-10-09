package com.genealogy.common.model;

/** Whether a grave record is a grave or a plot built for someone still alive. */
// In common/model: `event` reads it for `living`, and `gedcom` writes it (§4).
public enum GraveKind {

    // Mộ: its owner has died and is buried there, so a recorded grave counts as a recorded death (§8.8 D2).
    GRAVE,

    // Sinh phần: built in advance for someone still alive, so it says nothing about whether they have died.
    LIVING_PLOT
}
