/**
 * Picks the letter shown in place of a portrait, from a Vietnamese full name.
 *
 * @param fullName the name, surname first
 * @returns one capital letter, or "?" for an empty name
 */
export function initialOf(fullName: string): string {
  // Surname first, so the given name is the last word, and it is the name people are called by.
  const words = fullName.trim().split(/\s+/).filter(Boolean)
  return words.length === 0 ? '?' : words[words.length - 1].charAt(0).toLocaleUpperCase('vi')
}
