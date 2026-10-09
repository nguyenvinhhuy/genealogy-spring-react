import { apiClient } from '@/shared/lib/api-client'
import type { Page } from '@/shared/types/page'

import type { AuditAction, AuditEntityType, Revision } from './types'

const PAGE_SIZE = 20

/**
 * Lists the recorded changes to one record, newest first.
 *
 * @param entityType which kind of record
 * @param entityId the record id
 * @param page which page, zero-based
 * @returns one page of revisions
 */
export async function fetchRevisions(
  entityType: AuditEntityType,
  entityId: number,
  page = 0,
): Promise<Page<Revision>> {
  const response = await apiClient.get<Page<Revision>>('/revisions', {
    params: { entityType, entityId, page, size: PAGE_SIZE },
  })
  return response.data
}

/**
 * Lists recorded changes across the whole gia phả, newest first, filtered by kind and action.
 *
 * @param entityType which kind of record, or undefined for every kind
 * @param action what was done, or undefined for every action
 * @param page which page, zero-based
 * @returns one page of revisions
 */
export async function searchRevisions(
  entityType: AuditEntityType | undefined,
  action: AuditAction | undefined,
  page = 0,
): Promise<Page<Revision>> {
  const response = await apiClient.get<Page<Revision>>('/revisions', {
    params: { entityType, action, page, size: PAGE_SIZE },
  })
  return response.data
}
