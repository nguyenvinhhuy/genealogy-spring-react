/** What a GEDCOM import did. */
// `dropped` is separate from `skipped`: only the latter means "the app has nowhere to put this kind of record".
export interface GedcomImportResult {
  personsCreated: number
  familiesCreated: number
  eventsCreated: number
  sourcesCreated: number
  citationsCreated: number
  // How many records the file carried that this app does not model.
  skipped: number
  // How many records and sub-records this app models but could not keep.
  dropped: number
  // What was lost or guessed, one line each.
  warnings: string[]
}
