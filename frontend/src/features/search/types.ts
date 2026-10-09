/** Every filter the advanced person search accepts, all optional and all combined with AND (F20). */
export interface PersonSearchFilters {
  // Accent-insensitive search across all of a person's names.
  query?: string
  // Chi / phái filter.
  branchId?: number
  // Đời filter.
  generation?: number
  // True for the living, false for the deceased, undefined for both.
  living?: boolean
  birthYearFrom?: number
  birthYearTo?: number
  deathYearFrom?: number
  deathYearTo?: number
  // A place, matching that place and everything beneath it in the hierarchy.
  placeId?: number
}
