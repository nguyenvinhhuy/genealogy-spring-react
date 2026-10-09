import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { editableText, parseFuzzyDate } from '@/features/event/lib/parse-fuzzy-date'
import type { CalendarType, GenealogyDate } from '@/features/event/types'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'

/** Props of {@link FuzzyDateInput}. */
interface FuzzyDateInputProps {
  id: string
  label: string
  value: Partial<GenealogyDate> | null
  onChange: (value: Partial<GenealogyDate> | null) => void
}

/**
 * A date field that takes what the family knows — "1890", "khoảng 1890", "trước 1900", "1918-1922" (CLAUDE.md §6.1).
 *
 * @param props the field id, label, current value and change handler
 * @returns the field element
 */
export function FuzzyDateInput({ id, label, value, onChange }: FuzzyDateInputProps) {
  const { t } = useTranslation()
  // An imported date may carry no raw text; its parts, written the way the parser reads them, stand in for it.
  const [text, setText] = useState(value ? (value.raw ?? (editableText(value) || (value.display ?? ''))) : '')
  // Kept here, not read back from `value`: an empty field emits null, which used to snap Âm lịch back to Dương lịch.
  const [calendar, setCalendar] = useState<CalendarType>(value?.calendar ?? 'SOLAR')

  const parsed = useMemo(() => parseFuzzyDate(text), [text])

  const emit = (nextText: string, nextCalendar: CalendarType) => {
    const parsedNext = parseFuzzyDate(nextText)
    const lunar = nextCalendar === 'LUNAR'
    // Only a lunar giỗ may lack its year (§8.10 D1); a solar "12/3" is kept as the family's words instead.
    const next = !lunar && parsedNext.year == null ? { ...parsedNext, month: null, day: null } : parsedNext
    // A tháng nhuận exists only in the lunar calendar; the server refuses the flag on a solar date.
    const leapMonth = lunar && next.leapMonth
    onChange(nextText.trim() ? { ...next, leapMonth, leapMonth2: false, calendar: nextCalendar } : null)
  }
  const yearlessGio = calendar === 'LUNAR' && parsed.year == null && parsed.month != null

  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex gap-2">
        <Input
          id={id}
          value={text}
          placeholder={t('date.placeholder')}
          onChange={(event) => {
            setText(event.target.value)
            emit(event.target.value, calendar)
          }}
        />
        <Select
          value={calendar}
          onValueChange={(next) => {
            setCalendar(next as CalendarType)
            emit(text, next as CalendarType)
          }}
        >
          <SelectTrigger className="w-32" aria-label={t('date.calendar')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="SOLAR">{t('date.solar')}</SelectItem>
            <SelectItem value="LUNAR">{t('date.lunar')}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <p className="text-muted-foreground text-xs">
        {yearlessGio
          ? t('date.yearless')
          : text.trim() && !parsed.year
            ? t('date.unparsed')
            : calendar === 'LUNAR'
              ? t('date.hintLunar')
              : t('date.hint')}
      </p>
    </div>
  )
}
