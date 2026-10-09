import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { BRANCH_QUERY_KEY, fetchBranches } from '@/features/branch/api'
import { flattenHierarchy, subtreeOf } from '@/shared/lib/hierarchy'
import { NONE_VALUE } from '@/shared/lib/select-values'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

// Value the picker uses for "no chi selected": the one sentinel every select in the app shares.
export const NO_BRANCH = NONE_VALUE

/** Props of {@link BranchPicker}. */
interface BranchPickerProps {
  id?: string
  value: string
  onValueChange: (value: string) => void
  // A branch cannot become its own descendant's child, so its own subtree is left out when picking a parent.
  excludeSubtreeOf?: number
  // Shown as the first option; omit it where "no chi" is not a valid choice (e.g. a required parent picker).
  allowNone?: boolean
  // What the first option says: "no chi" on a form, but "every chi" on a filter, where it means no filter at all.
  noneLabel?: string
}

/**
 * A single-level select showing every chi indented under its ancestors.
 *
 * @param props the current value, the change handler, and what to exclude
 * @returns the select element
 */
export function BranchPicker({
  id,
  value,
  onValueChange,
  excludeSubtreeOf,
  allowNone = true,
  noneLabel,
}: BranchPickerProps) {
  const { t } = useTranslation()
  const { data: branches = [] } = useQuery({ queryKey: BRANCH_QUERY_KEY, queryFn: fetchBranches })

  const excluded = excludeSubtreeOf != null ? subtreeOf(branches, excludeSubtreeOf) : null
  const options = flattenHierarchy(branches).filter((option) => !excluded?.has(option.id))

  return (
    <Select value={value} onValueChange={onValueChange}>
      <SelectTrigger id={id} className="w-full">
        <SelectValue placeholder={t('branch.pick')} />
      </SelectTrigger>
      <SelectContent>
        {allowNone && <SelectItem value={NO_BRANCH}>{noneLabel ?? t('branch.none')}</SelectItem>}
        {options.map((option) => (
          // Indentation follows depth, so "Chi 1 › Phái 2" reads as nested rather than as one flat list.
          <SelectItem key={option.id} value={String(option.id)} style={{ paddingLeft: `${option.depth * 16 + 8}px` }}>
            {option.path}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
