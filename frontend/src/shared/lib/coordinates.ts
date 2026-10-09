// The widest a latitude may be, in degrees either side of the equator.
export const MAX_LATITUDE = 90

// The widest a longitude may be, in degrees either side of Greenwich.
export const MAX_LONGITUDE = 180

// NUMERIC(9, 6) on the server: a seventh decimal would be refused, not rounded, by @Digits.
const DECIMALS = 6

/**
 * Reads one coordinate as typed, accepting a decimal comma as well as a point.
 *
 * @param text the text in the field
 * @returns the degrees, null for an empty field, or NaN when the text is not a number
 */
export function parseCoordinate(text: string): number | null {
  const trimmed = text.trim()
  if (trimmed === '') {
    return null
  }
  // "21,028511" is how a Vietnamese keyboard and Excel write it; Number() would read it as NaN.
  const normalised = trimmed.replace(',', '.')
  if (!/^[-+]?\d+(\.\d+)?$/.test(normalised)) {
    return Number.NaN
  }
  return Number(Number(normalised).toFixed(DECIMALS))
}

/**
 * Reads a latitude and longitude pasted together, as Google Maps copies them: "21.028511, 105.804817".
 *
 * @param text the text pasted into either field
 * @returns the pair, or null when the text is not exactly two numbers
 */
export function splitCoordinatePair(text: string): [number, number] | null {
  // Split on the comma-and-space or the space: a lone comma inside one number is a decimal comma, not a pair.
  const parts = text.trim().split(/\s*[,;]\s+|\s+|\s*;\s*/)
  if (parts.length !== 2) {
    return null
  }
  const latitude = parseCoordinate(parts[0])
  const longitude = parseCoordinate(parts[1])
  if (latitude == null || longitude == null || Number.isNaN(latitude) || Number.isNaN(longitude)) {
    return null
  }
  return [latitude, longitude]
}

/**
 * Reports whether a parsed coordinate is a number within the given bound.
 *
 * @param value the parsed coordinate, or null for none
 * @param limit the largest magnitude allowed
 * @returns true for null or an in-range number
 */
export function isCoordinateValid(value: number | null, limit: number): boolean {
  return value == null || (!Number.isNaN(value) && Math.abs(value) <= limit)
}

/**
 * Renders a stored coordinate back into a form field.
 *
 * @param value the stored degrees, or null
 * @returns the field text
 */
export function coordinateText(value: number | null): string {
  // `!= null`, not truthiness: 0 is the equator, and a falsy check read it as "no coordinate".
  return value != null ? String(value) : ''
}
