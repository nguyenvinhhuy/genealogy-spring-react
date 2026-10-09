import type { BaseSyntheticEvent } from 'react'

/**
 * Wraps a React Hook Form submit handler so an event prop can call it without leaving its promise floating.
 *
 * @param handler what `handleSubmit(...)` returned
 * @returns a handler that returns nothing, as `onSubmit` and `onClick` expect
 */
export function voidSubmit(handler: (event?: BaseSyntheticEvent) => Promise<void>) {
  // The promise never rejects for a validation failure, which RHF reports through formState; void says so.
  return (event?: BaseSyntheticEvent) => {
    void handler(event)
  }
}
