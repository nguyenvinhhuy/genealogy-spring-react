import { apiClient } from '@/shared/lib/api-client'

import type { QualityIssue } from './types'

/**
 * Runs every consistency rule over the whole clan.
 *
 * @returns the findings, most serious first
 */
export async function fetchQualityIssues(): Promise<QualityIssue[]> {
  const response = await apiClient.get<QualityIssue[]>('/quality/issues')
  return response.data
}
