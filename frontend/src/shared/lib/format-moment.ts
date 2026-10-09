import i18n from '@/shared/i18n'

// The one BCP 47 locale each app language renders dates and times in.
const LOCALE_BY_LANGUAGE: Record<string, string> = {
  vi: 'vi-VN',
  en: 'en-GB',
}

/**
 * Returns the BCP 47 locale for the current app language, read straight from i18next.
 *
 * @returns "vi-VN" or "en-GB"
 */
function currentLocale(): string {
  return LOCALE_BY_LANGUAGE[i18n.language] ?? LOCALE_BY_LANGUAGE.vi
}

/**
 * Renders an instant the way the current app language writes a date and time together.
 *
 * @param iso the timestamp as the API returns it
 * @returns the formatted moment, e.g. "25/09/2026, 14:30"
 */
export function formatMoment(iso: string): string {
  return new Date(iso).toLocaleString(currentLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * Renders a plain solar date (year-month-day, no time) the way the current app language writes one.
 *
 * @param iso the date as the API returns it, e.g. "2026-09-25"
 * @returns the formatted date, e.g. "25/09/2026"
 */
export function formatSolarDate(iso: string): string {
  // Parsed at local midnight so the date does not shift a day when the browser's zone is behind UTC.
  return new Date(`${iso}T00:00:00`).toLocaleDateString(currentLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

/**
 * Renders a count with the current app language's digit grouping.
 *
 * @param value the number
 * @returns the formatted number, e.g. "1.234" in Vietnamese and "1,234" in English
 */
export function formatCount(value: number): string {
  return value.toLocaleString(currentLocale())
}

/**
 * Renders the date part of a full instant, dropping the time.
 *
 * @param iso the timestamp as the API returns it, e.g. "2026-09-25T10:22:00Z"
 * @returns the formatted date, e.g. "25/09/2026"
 */
export function formatDateOnly(iso: string): string {
  return new Date(iso).toLocaleDateString(currentLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}
