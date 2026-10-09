/** How precisely a genealogical date is known. */
export type DateModifier =
  | 'EXACT'
  | 'ABOUT'
  | 'BEFORE'
  | 'AFTER'
  | 'BETWEEN'
  | 'ESTIMATED'
  | 'CALCULATED'

/** Which calendar the date's parts are expressed in. */
export type CalendarType = 'SOLAR' | 'LUNAR'

/** What kind of record an event hangs off. */
export type EventSubjectType = 'PERSON' | 'FAMILY'

/** What happened; a giỗ is a DEATH on the LUNAR calendar, not a type of its own. */
export type EventType =
  | 'BIRTH'
  | 'DEATH'
  | 'BURIAL'
  // Cải táng: each move of a grave, with its own date and place; the grave itself says where it is now.
  | 'REBURIAL'
  | 'RESIDENCE'
  | 'OCCUPATION'
  | 'EDUCATION'
  | 'OTHER_PERSON'
  | 'MARRIAGE'
  | 'DIVORCE'
  | 'OTHER_FAMILY'

/** A genealogical date. */
// No `sortDate`: the backend derives one to order by, but rendering it overstates what the record knows.
export interface GenealogyDate {
  modifier: DateModifier
  calendar: CalendarType
  year: number | null
  month: number | null
  day: number | null
  year2: number | null
  month2: number | null
  day2: number | null
  // Verbatim user input, kept so nothing the family typed is lost.
  raw: string | null
  // The date written out in Vietnamese by the server, so the book and this page never disagree.
  display: string | null
  // True for a day in the tháng nhuận, the repeated lunar month; always false for a solar date.
  leapMonth: boolean
  // The same for a range's second endpoint, which can end in the leap month as easily as begin in one.
  leapMonth2: boolean
}

/** Something that happened to a person or a union. */
export interface GenealogyEvent {
  id: number
  subjectType: EventSubjectType
  subjectId: number
  type: EventType
  date: GenealogyDate | null
  placeId: number | null
  description: string | null
  // The row version an edit must be sent back with, so a stale form is refused rather than overwriting.
  version: number
}

/** One upcoming ngày giỗ. */
// Both calendars: the family knows the giỗ by its lunar date but needs the solar one for a phone calendar.
export interface Anniversary {
  // The death it comes from: one person can carry two recorded deaths after a merge, so this is the key.
  eventId: number
  personId: number
  personName: string
  lunarDay: number
  lunarMonth: number
  // The giỗ falls in a tháng nhuận, which most years do not have.
  leapMonth: boolean
  // The death day was recorded as approximate (khoảng, ước tính), so the giỗ is too.
  approximate: boolean
  nextOccurrence: string
  daysUntil: number
  // Năm thứ mấy, or null when the year of death is unknown.
  yearsSince: number | null
}

/** Payload for creating or updating an event. */
export interface EventPayload {
  subjectId: number
  type: EventType
  date?: Partial<GenealogyDate> | null
  placeId?: number | null
  description?: string | null
  // Why the edit was made, for the change history (§3.8).
  changeNote?: string | null
  // The version the edit was made from; omitted on create.
  version?: number
}
