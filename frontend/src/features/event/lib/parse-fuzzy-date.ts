import type { DateModifier, GenealogyDate } from '@/features/event/types'

/** The parts a parsed date carries, before the caller attaches a calendar. */
/** A date as read from what was typed, before the calendar is chosen. */
// `display` is omitted too: only the server renders one, and it does so from the stored date, not from input.
export type ParsedDate = Omit<GenealogyDate, 'calendar' | 'display'>

const YEAR = String.raw`(\d{3,4})`
const SEPARATOR = String.raw`[/\-.\s]`
const MONTHS_IN_YEAR = 12
const MAX_DAY = 31

// Full words before abbreviations, and never followed by a letter: "bef" used to swallow "before" and leave "ore".
const PREFIXES: ReadonlyArray<readonly [RegExp, DateModifier]> = [
  [/^(khoảng chừng|khoang chung|khoảng|khoang|chừng|chung|about|abt\.?|ca\.?|~)(?![a-zà-ỹ])\s*/i, 'ABOUT'],
  [/^(trước|truoc|before|bef\.?|<)(?![a-zà-ỹ])\s*/i, 'BEFORE'],
  [/^(sau|after|aft\.?|>)(?![a-zà-ỹ])\s*/i, 'AFTER'],
  [/^(ước tính|uoc tinh|estimated|est\.?)(?![a-zà-ỹ])\s*/i, 'ESTIMATED'],
  [/^(tính ra|tinh ra|calculated|cal\.?)(?![a-zà-ỹ])\s*/i, 'CALCULATED'],
]

// "nhuận" anywhere marks the tháng nhuận, the repeated lunar month: "10/4 nhuận/2020" or "10/4/2020 nhuận".
const LEAP = /\s*(nhuận|nhuan|leap)(?![a-zà-ỹ])\s*/i

/**
 * Builds an empty parsed date.
 *
 * @param raw the text the user typed
 * @returns a date carrying nothing but the raw text
 */
function empty(raw: string): ParsedDate {
  return {
    modifier: 'EXACT',
    year: null,
    month: null,
    day: null,
    year2: null,
    month2: null,
    day2: null,
    raw: raw || null,
    leapMonth: false,
    leapMonth2: false,
  }
}

/**
 * Reads a `d/m/y`, `m/y`, `y` or `d/m` fragment.
 *
 * @param text the fragment, already trimmed
 * @returns the parts it names, or null when it is not a date at all
 */
function parseParts(text: string): { year: number | null; month: number | null; day: number | null } | null {
  const full = new RegExp(`^(\\d{1,2})${SEPARATOR}(\\d{1,2})${SEPARATOR}${YEAR}$`).exec(text)
  if (full) {
    return { day: Number(full[1]), month: Number(full[2]), year: Number(full[3]) }
  }

  // A giỗ known only by day and month, "12/3", is the commonest record of a thuỷ tổ (§8.10 D1).
  const dayAndMonth = new RegExp(`^(\\d{1,2})${SEPARATOR}(\\d{1,2})$`).exec(text)
  if (dayAndMonth) {
    return { day: Number(dayAndMonth[1]), month: Number(dayAndMonth[2]), year: null }
  }

  const monthAndYear = new RegExp(`^(\\d{1,2})${SEPARATOR}${YEAR}$`).exec(text)
  if (monthAndYear) {
    return { day: null, month: Number(monthAndYear[1]), year: Number(monthAndYear[2]) }
  }

  const yearOnly = new RegExp(`^${YEAR}$`).exec(text)
  if (yearOnly) {
    return { day: null, month: null, year: Number(yearOnly[1]) }
  }
  return null
}

/**
 * Reports whether a month and day could name a real calendar day.
 *
 * @param month the month, or null
 * @param day the day, or null
 * @returns true when both are in range or absent
 */
function inRange(month: number | null, day: number | null): boolean {
  // Out of range, the server would refuse the save and the family's words would be lost; kept as raw text instead.
  const monthOk = month == null || (month >= 1 && month <= MONTHS_IN_YEAR)
  const dayOk = day == null || (day >= 1 && day <= MAX_DAY)
  return monthOk && dayOk
}

// The words parseFuzzyDate reads back for each modifier; an exact date has none.
const MODIFIER_WORDS: Partial<Record<DateModifier, string>> = {
  ABOUT: 'khoảng ',
  BEFORE: 'trước ',
  AFTER: 'sau ',
  ESTIMATED: 'ước tính ',
  CALCULATED: 'tính ra ',
}

/**
 * Writes a stored date as text that parseFuzzyDate reads back to the same date.
 *
 * @param date a stored or parsed date
 * @returns the text, or empty when the date names no year and no month
 */
export function editableText(date: Partial<GenealogyDate>): string {
  // The server's rendering ("từ 1918 đến 1922", "…, năm Canh Dần") is for reading, and the parser cannot read it.
  if (date.modifier === 'BETWEEN' && date.year != null && date.year2 != null) {
    return `${date.year}-${date.year2}`
  }
  const parts: string[] = []
  if (date.day != null && date.month != null) {
    parts.push(String(date.day))
  }
  if (date.month != null) {
    parts.push(String(date.month))
  }
  if (date.year != null) {
    parts.push(String(date.year))
  }
  if (parts.length === 0) {
    return ''
  }
  const words = date.modifier ? (MODIFIER_WORDS[date.modifier] ?? '') : ''
  return `${words}${parts.join('/')}${date.leapMonth ? ' nhuận' : ''}`
}

/**
 * Turns what the user typed into a genealogical date: a year, `d/m/yyyy`, a range, or "khoảng"/"trước"/"sau".
 *
 * @param input the text the user typed
 * @returns the parsed parts, with `year` left null when nothing could be read
 */
export function parseFuzzyDate(input: string): ParsedDate {
  const raw = input.trim()
  if (!raw) {
    return empty(raw)
  }

  let rest = raw
  let modifier: DateModifier = 'EXACT'
  for (const [pattern, value] of PREFIXES) {
    if (pattern.test(rest)) {
      modifier = value
      rest = rest.replace(pattern, '').trim()
      break
    }
  }

  const leap = LEAP.test(rest)
  rest = rest.replace(LEAP, leap ? '/' : '').replace(/\/+/g, '/').replace(/^\/|\/$/g, '').trim()

  // A range wins over the prefix: "1918-1922" is BETWEEN even if someone also wrote "khoảng".
  const range = new RegExp(`^${YEAR}\\s*(?:-|–|to|đến|den)\\s*${YEAR}$`, 'i').exec(rest)
  if (range) {
    const [from, to] = [Number(range[1]), Number(range[2])]
    return from <= to ? { ...empty(raw), modifier: 'BETWEEN', year: from, year2: to } : empty(raw)
  }

  const parts = parseParts(rest)
  if (!parts || !inRange(parts.month, parts.day)) {
    return empty(raw)
  }
  // A leap month needs a month to be the leap one; "1950 nhuận" names none.
  const leapMonth = leap && parts.month != null
  return { ...empty(raw), modifier, year: parts.year, month: parts.month, day: parts.day, leapMonth }
}
