import { apiClient } from '@/shared/lib/api-client'

/**
 * Fetches a file from the API and hands it to the browser to save.
 *
 * @param path the endpoint, relative to the API base
 * @param fallbackName what to call the file when the response names none
 * @param params query parameters, if any
 * @returns nothing; the browser is handed the file
 */
export async function downloadFile(
  path: string,
  fallbackName: string,
  params?: Record<string, unknown>,
): Promise<void> {
  const response = await withReadableBlobErrors(() => apiClient.get<Blob>(path, { params, responseType: 'blob' }))
  const filename = filenameFrom(response.headers['content-disposition'] as string | undefined, fallbackName)
  saveBlob(response.data, filename)
}

/**
 * Saves a blob the browser fetched as a file the user can keep.
 *
 * @param blob the file contents
 * @param filename what to call it on disk
 */
function saveBlob(blob: Blob, filename: string): void {
  // A plain link cannot carry the Authorization header the export endpoints need, so axios fetches it first.
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // Revoking immediately can cancel the download, so the object URL is released after the click starts.
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/**
 * Runs a download, replacing a blob error body with the ProblemDetail it actually holds.
 *
 * @param run the request to make
 * @returns what the request returned
 */
async function withReadableBlobErrors<T>(run: () => Promise<T>): Promise<T> {
  try {
    return await run()
  } catch (error) {
    const body: unknown = (error as { response?: { data?: unknown } }).response?.data
    if (body instanceof Blob) {
      const text = await body.text()
      try {
        ;(error as { response: { data: unknown } }).response.data = JSON.parse(text)
      } catch {
        // Not JSON — leave the original error alone rather than inventing a message.
      }
    }
    throw error
  }
}

/**
 * Reads the filename a response's Content-Disposition header suggests.
 *
 * @param header the raw header value, or undefined
 * @param fallback what to use when the header says nothing
 * @returns the filename to save as
 */
function filenameFrom(header: string | undefined, fallback: string): string {
  const match = header?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
  return match ? decodeURIComponent(match[1]) : fallback
}
