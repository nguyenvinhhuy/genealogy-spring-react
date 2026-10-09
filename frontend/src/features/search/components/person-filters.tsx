import { useTranslation } from 'react-i18next'

import { BranchPicker, NO_BRANCH } from '@/features/branch/components/branch-picker'
import type { PersonSearchFilters } from '@/features/search/types'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'

/** Props of {@link PersonFilters}. */
interface PersonFiltersProps {
  filters: PersonSearchFilters
  onChange: (filters: PersonSearchFilters) => void
}

// What the living-status select offers; "all" is a UI value, not something the API is sent.
const LIVING_OPTIONS = ['ALL', 'LIVING', 'DECEASED'] as const

/**
 * The filter row above the person list.
 *
 * @param props the current filters and a callback for changing them
 * @returns the filter element
 */
export function PersonFilters({ filters, onChange }: PersonFiltersProps) {
  // Every field is optional and they narrow together, so adding a đời keeps what was already typed.
  const { t } = useTranslation()

  const set = (patch: Partial<PersonSearchFilters>) => onChange({ ...filters, ...patch })
  // Non-digits are dropped, not parsed: NaN survives `?? ''`, renders as "NaN" and 400s the request.
  const number = (value: string) => {
    const digits = value.replace(/\D/g, '')
    return digits === '' ? undefined : Number(digits)
  }
  const living = filters.living === undefined ? 'ALL' : filters.living ? 'LIVING' : 'DECEASED'
  const active = Object.values(filters).some((value) => value !== undefined && value !== '')

  return (
    <div className="space-y-3">
      <Input
        value={filters.query ?? ''}
        placeholder={t('person.searchPlaceholder')}
        onChange={(event) => set({ query: event.target.value || undefined })}
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="space-y-2">
          <Label htmlFor="filter-branch">{t('person.branch')}</Label>
          <BranchPicker
            id="filter-branch"
            value={filters.branchId != null ? String(filters.branchId) : NO_BRANCH}
            onValueChange={(next) => set({ branchId: next === NO_BRANCH ? undefined : Number(next) })}
            noneLabel={t('branch.any')}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="filter-generation">{t('person.generation')}</Label>
          <Input
            id="filter-generation"
            inputMode="numeric"
            value={filters.generation ?? ''}
            placeholder={t('search.anyGeneration')}
            onChange={(event) => set({ generation: number(event.target.value) })}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="filter-living">{t('person.status')}</Label>
          <Select
            value={living}
            onValueChange={(next) =>
              set({ living: next === 'ALL' ? undefined : next === 'LIVING' })
            }
          >
            <SelectTrigger id="filter-living" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {LIVING_OPTIONS.map((option) => (
                <SelectItem key={option} value={option}>
                  {t(`search.living.${option}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="filter-birth-from">{t('search.birthYear')}</Label>
          <div className="flex items-center gap-2">
            <Input
              id="filter-birth-from"
              inputMode="numeric"
              value={filters.birthYearFrom ?? ''}
              placeholder={t('search.from')}
              onChange={(event) => set({ birthYearFrom: number(event.target.value) })}
            />
            <span className="text-muted-foreground">–</span>
            <Input
              inputMode="numeric"
              aria-label={t('search.birthYearTo')}
              value={filters.birthYearTo ?? ''}
              placeholder={t('search.to')}
              onChange={(event) => set({ birthYearTo: number(event.target.value) })}
            />
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="filter-death-from">{t('search.deathYear')}</Label>
          <div className="flex items-center gap-2">
            <Input
              id="filter-death-from"
              inputMode="numeric"
              value={filters.deathYearFrom ?? ''}
              placeholder={t('search.from')}
              onChange={(event) => set({ deathYearFrom: number(event.target.value) })}
            />
            <span className="text-muted-foreground">–</span>
            <Input
              inputMode="numeric"
              aria-label={t('search.deathYearTo')}
              value={filters.deathYearTo ?? ''}
              placeholder={t('search.to')}
              onChange={(event) => set({ deathYearTo: number(event.target.value) })}
            />
          </div>
        </div>
      </div>

      {active && (
        <Button variant="ghost" size="sm" onClick={() => onChange({})}>
          {t('search.clear')}
        </Button>
      )}
    </div>
  )
}
