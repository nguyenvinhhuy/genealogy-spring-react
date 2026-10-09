import { Search } from 'lucide-react'
import type { ComponentProps } from 'react'

import { cn } from '@/shared/lib/utils'
import { Input } from '@/shared/ui/input'

/**
 * A text box with a magnifier, for the search above a list.
 *
 * @param props the input's own props; `aria-label` names it, as it has no visible label
 * @returns the search box
 */
export function SearchInput({ className, ...props }: ComponentProps<typeof Input>) {
  return (
    <div className={cn('relative w-full max-w-md', className)}>
      <Search className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" aria-hidden />
      <Input type="search" className="pl-8" {...props} />
    </div>
  )
}
