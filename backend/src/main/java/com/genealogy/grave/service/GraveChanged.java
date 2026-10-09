package com.genealogy.grave.service;

/**
 * Published inside the transaction whenever a person's grave is recorded, changed, moved or removed.
 *
 * @param personId the person whose grave changed
 */
// An event, not a call: `event` reads graves for `living`, so a grave write calling `event` back is a bean cycle.
public record GraveChanged(Long personId) {
}
