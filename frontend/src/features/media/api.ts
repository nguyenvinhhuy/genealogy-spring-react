import { apiClient } from '@/shared/lib/api-client'

import type { Media, MediaKind, MediaTargetType, MediaUpdatePayload } from './types'

// The first element of every media query key, so one invalidation reaches every gallery on screen.
export const MEDIA_QUERY_KEY = 'media'

// A signed URL lives an hour (app.storage.url-ttl); refetching well inside that keeps every link openable.
export const MEDIA_REFRESH_MS = 20 * 60 * 1000

// What the server accepts, read from the bytes; `.heic` is listed because Windows gives it no media type.
export const MEDIA_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp,image/heic,.heic,application/pdf'

// The formats a browser and the printed book can both draw, so only these may become a portrait.
export const PORTRAIT_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif'])

/**
 * Builds the query key of one record's gallery.
 *
 * @param targetType what kind of record
 * @param targetId the record id
 * @returns the key
 */
export function mediaQueryKey(targetType: MediaTargetType, targetId: number) {
  return [MEDIA_QUERY_KEY, targetType, targetId] as const
}

/**
 * Lists the photos and scans attached to one record.
 *
 * @param targetType what kind of record
 * @param targetId the record id
 * @returns the files, or nothing when the caller may not see this record (CLAUDE.md §3.6)
 */
export async function fetchMedia(targetType: MediaTargetType, targetId: number): Promise<Media[]> {
  const response = await apiClient.get<Media[]>('/media', { params: { targetType, targetId } })
  return response.data
}

/**
 * Uploads a photo or scan and attaches it to a record.
 *
 * @param targetType what kind of record
 * @param targetId the record id
 * @param file the file the user picked
 * @param kind what the file is
 * @param caption what the family wants written under it
 * @returns the stored file
 */
export async function uploadMedia(
  targetType: MediaTargetType,
  targetId: number,
  file: File,
  kind: MediaKind,
  caption: string,
): Promise<Media> {
  // Every field in the body: a caption in the query string lands in every proxy's access log (§8.9 #19).
  const form = new FormData()
  form.append('file', file)
  form.append('targetType', targetType)
  form.append('targetId', String(targetId))
  form.append('kind', kind)
  if (caption.trim()) {
    form.append('caption', caption.trim())
  }
  const response = await apiClient.post<Media>('/media', form)
  return response.data
}

/**
 * Edits an already-uploaded file's caption, kind or position.
 *
 * @param id media id
 * @param payload the new values
 * @returns the updated file
 */
export async function updateMedia(id: number, payload: MediaUpdatePayload): Promise<Media> {
  const response = await apiClient.put<Media>(`/media/${id}`, payload)
  return response.data
}

/**
 * Deletes a photo or scan, with the reason for the change history.
 *
 * @param id media id
 * @param changeNote why it is being deleted, or empty
 * @returns nothing
 */
export async function deleteMedia(id: number, changeNote: string): Promise<void> {
  await apiClient.delete(`/media/${id}`, { params: { changeNote: changeNote || undefined } })
}
