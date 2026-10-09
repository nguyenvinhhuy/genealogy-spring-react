import { downloadFile } from '@/shared/lib/download'

/**
 * Downloads the gia phả as a printable PDF, laid out đời by đời.
 *
 * @param personId only this person and their descendants, omit for the whole clan
 * @returns nothing; the browser is handed the file
 */
export async function downloadBook(personId?: number): Promise<void> {
  await downloadFile('/book', 'gia-pha.pdf', { personId })
}
