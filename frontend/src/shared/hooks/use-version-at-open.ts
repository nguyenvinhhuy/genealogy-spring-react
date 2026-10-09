import { useState } from 'react'

/**
 * Returns the version a record had when a dialog opened, and the live one while it is shut.
 *
 * @param current the record's version as the query has it now
 * @param open whether the dialog is open
 * @returns the version to send with the form
 */
export function useVersionAtOpen<T>(current: T, open: boolean): T {
  // A refetch while the dialog is open must not upgrade the version the form sends: its fields still hold the old
  // values, so the server would accept a stale edit and overwrite the other editor's change (§8.12 #1).
  const [state, setState] = useState({ open, version: current })
  if (open !== state.open) {
    setState({ open, version: open ? current : state.version })
  }
  return open ? state.version : current
}
