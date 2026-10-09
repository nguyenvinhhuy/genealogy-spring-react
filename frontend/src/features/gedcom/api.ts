import { apiClient } from '@/shared/lib/api-client'
import { downloadFile } from '@/shared/lib/download'

import type { GedcomImportResult } from './types'

/**
 * Downloads the whole gia phả as a GEDCOM 7.0 file.
 *
 * @returns nothing; the browser is handed the file
 */
export async function downloadGedcom(): Promise<void> {
  await downloadFile('/gedcom/export', 'gia-pha.ged')
}

/**
 * Uploads a GEDCOM file and reads it into the gia phả.
 *
 * @param file the file the user picked
 * @returns what was created, skipped and guessed
 */
export async function importGedcom(file: File): Promise<GedcomImportResult> {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<GedcomImportResult>('/gedcom/import', form)
  return response.data
}
