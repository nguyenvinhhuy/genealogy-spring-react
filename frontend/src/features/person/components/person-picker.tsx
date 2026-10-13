import { useQuery } from '@tanstack/react-query'
import { Check, ChevronsUpDown } from 'lucide-react'
import { type KeyboardEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { PersonNode } from '@/features/person/types'
import { searchPersons } from '@/features/search/api'
import { TYPING_DEBOUNCE_MS, useDebounced } from '@/shared/hooks/use-debounced'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/shared/ui/popover'

// Enough to find a name, few enough that the list stays something a person reads rather than scrolls.
const RESULT_LIMIT = 10

/** Props of {@link PersonPicker}. */
interface PersonPickerProps {
  id: string
  label: string
  value: PersonNode | null
  onChange: (person: PersonNode | null) => void
  // People who must not be offered, such as the person the link is being made from.
  excludeIds?: number[]
}

/**
 * Finds one person by name: a button that opens a search box with the matches listed right beneath it.
 *
 * @param props the field id and label, the chosen person, the change handler and who to leave out
 * @returns the field element
 */
export function PersonPicker({ id, label, value, onChange, excludeIds = [] }: PersonPickerProps) {
  // One control, not a search box beside an empty select that looked broken until something was typed (§6.1).
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const settled = useDebounced(query.trim(), TYPING_DEBOUNCE_MS)

  const { data, isError, isFetching } = useQuery({
    queryKey: ['persons', 'picker', settled],
    queryFn: () => searchPersons({ query: settled }, RESULT_LIMIT, 0),
    // An empty search would list the first ten people of the clan, which is no help in finding one.
    enabled: open && settled !== '',
  })

  const found = (data?.content ?? []).filter((person) => !excludeIds.includes(person.id))
  const more = (data?.page.totalElements ?? 0) > RESULT_LIMIT
  const listId = `${id}-list`
  const optionId = (person: PersonNode) => `${id}-option-${person.id}`
  // The id and đời, because duplicates share a name exactly and a name alone tells them apart not at all.
  const describe = (person: PersonNode) =>
    [
      person.displayName,
      `#${person.id}`,
      person.generation != null ? t('person.generationN', { n: person.generation }) : null,
    ]
      .filter(Boolean)
      .join(' · ')

  const close = () => {
    setOpen(false)
    setQuery('')
    setActive(0)
  }

  const choose = (person: PersonNode) => {
    onChange(person)
    close()
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((current) => Math.min(current + 1, found.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((current) => Math.max(current - 1, 0))
    } else if (event.key === 'Enter' && found[active]) {
      event.preventDefault()
      choose(found[active])
    }
  }

  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      {/* Modal, so the search box can take focus inside a dialog that traps it. */}
      <Popover open={open} onOpenChange={(next) => (next ? setOpen(true) : close())} modal>
        <PopoverTrigger asChild>
          <Button
            id={id}
            type="button"
            variant="outline"
            role="combobox"
            aria-expanded={open}
            aria-controls={listId}
            className="w-full justify-between font-normal"
          >
            <span className={cn('truncate', value == null && 'text-muted-foreground')}>
              {value ? describe(value) : t('person.picker.placeholder')}
            </span>
            <ChevronsUpDown aria-hidden className="opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent align="start" className="w-(--radix-popover-trigger-width) gap-1 p-2">
          <Input
            autoFocus
            value={query}
            aria-label={label}
            aria-controls={listId}
            aria-activedescendant={found[active] ? optionId(found[active]) : undefined}
            placeholder={t('person.searchPlaceholder')}
            onChange={(event) => {
              setQuery(event.target.value)
              setActive(0)
            }}
            onKeyDown={onKeyDown}
          />
          <div id={listId} role="listbox" aria-label={label} className="max-h-64 overflow-y-auto">
            {settled === '' && <PickerMessage text={t('person.picker.typeToSearch')} />}
            {settled !== '' && isFetching && found.length === 0 && (
              <PickerMessage text={t('person.picker.searching')} />
            )}
            {settled !== '' && !isFetching && !isError && found.length === 0 && (
              <PickerMessage text={t('person.picker.noMatch')} />
            )}
            {isError && <PickerMessage text={t('common.unexpectedError')} destructive />}
            {found.map((person, index) => (
              <div
                key={person.id}
                id={optionId(person)}
                role="option"
                tabIndex={-1}
                aria-selected={value?.id === person.id}
                className={cn(
                  'flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm',
                  index === active && 'bg-accent text-accent-foreground',
                )}
                onMouseEnter={() => setActive(index)}
                onClick={() => choose(person)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    choose(person)
                  }
                }}
              >
                <Check
                  aria-hidden
                  className={cn('size-4 shrink-0', value?.id === person.id ? 'opacity-100' : 'opacity-0')}
                />
                <span className="truncate">{describe(person)}</span>
              </div>
            ))}
            {more && <PickerMessage text={t('person.picker.more')} />}
          </div>
        </PopoverContent>
      </Popover>
    </div>
  )
}

/** Props of {@link PickerMessage}. */
interface PickerMessageProps {
  text: string
  destructive?: boolean
}

/**
 * Shows one line of guidance inside the picker's list: what to type, searching, nothing found.
 *
 * @param props the text and whether it reports a failure
 * @returns the line element
 */
function PickerMessage({ text, destructive = false }: PickerMessageProps) {
  return (
    <p className={cn('px-2 py-1.5 text-sm', destructive ? 'text-destructive' : 'text-muted-foreground')}>
      {text}
    </p>
  )
}
