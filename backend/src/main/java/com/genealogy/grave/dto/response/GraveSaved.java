package com.genealogy.grave.dto.response;

/**
 * The grave a save produced, and whether it was recorded for the first time.
 *
 * @param grave the saved grave
 * @param created true when no grave was recorded for the person before this save
 */
public record GraveSaved(GraveResponse grave, boolean created) {
}
