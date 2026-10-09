import { AxiosError } from 'axios'

/** An RFC 9457 error body as returned by the backend. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
}

// The status the backend answers when a record does not exist, or is hidden from the caller (§3.6).
export const HTTP_NOT_FOUND = 404

/**
 * Reads the HTTP status of a failed request.
 *
 * @param error the caught error
 * @returns the status, or undefined when the error never reached the server
 */
export function problemStatus(error: unknown): number | undefined {
  return error instanceof AxiosError ? error.response?.status : undefined
}

/**
 * Extracts a human-readable message from an unknown error, preferring ProblemDetail.detail.
 *
 * @param error the caught error
 * @param fallback message to use when the error carries none
 * @returns the message to show the user
 */
export function problemMessage(error: unknown, fallback: string): string {
  // Only the server's own words, which are Vietnamese; axios's "Network Error" is English and says nothing (#29).
  if (error instanceof AxiosError) {
    const problem = error.response?.data as ProblemDetail | undefined
    return problem?.detail ?? problem?.title ?? fallback
  }
  return fallback
}
