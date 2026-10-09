import { describe, expect, it } from 'vitest'

import { editableText, parseFuzzyDate } from './parse-fuzzy-date'

describe('editableText', () => {
  it.each(['1890', 'khoảng 1890', 'trước 1900', 'sau 1900', '15/3/1890', '3/1890', '12/3', '1918-1922', '10/4/2020 nhuận'])(
    'writes "%s" so the parser reads it back to the same parts',
    (input) => {
      const parsed = parseFuzzyDate(input)
      // The input is what was stored; the text written from it must parse to the same date again (§8.12 #15).
      expect(parseFuzzyDate(editableText(parsed))).toMatchObject({
        modifier: parsed.modifier,
        year: parsed.year,
        month: parsed.month,
        day: parsed.day,
        year2: parsed.year2,
        leapMonth: parsed.leapMonth,
      })
    },
  )

  it('writes nothing for a date with no year and no month', () => {
    expect(editableText({ modifier: 'EXACT', year: null, month: null, day: null })).toBe('')
  })
})

describe('parseFuzzyDate', () => {
  it('reads a bare year as an exact year-only date', () => {
    expect(parseFuzzyDate('1890')).toMatchObject({ modifier: 'EXACT', year: 1890, month: null, day: null })
  })

  it('reads d/m/yyyy and m/yyyy', () => {
    expect(parseFuzzyDate('15/3/1890')).toMatchObject({ year: 1890, month: 3, day: 15 })
    expect(parseFuzzyDate('3/1890')).toMatchObject({ year: 1890, month: 3, day: null })
  })

  it.each([
    ['khoảng 1890', 'ABOUT'],
    ['khoang 1890', 'ABOUT'],
    ['khoảng chừng 1890', 'ABOUT'],
    ['about 1890', 'ABOUT'],
    ['abt. 1890', 'ABOUT'],
    ['trước 1900', 'BEFORE'],
    ['before 1900', 'BEFORE'],
    ['bef 1900', 'BEFORE'],
    ['sau 1900', 'AFTER'],
    ['after 1900', 'AFTER'],
    ['ước tính 1900', 'ESTIMATED'],
    ['estimated 1900', 'ESTIMATED'],
    ['tính ra 1900', 'CALCULATED'],
    ['calculated 1900', 'CALCULATED'],
  ])('reads the prefix in "%s" as %s and still finds the year', (input, modifier) => {
    // Until 2026-09-24 "bef" matched inside "before" and left "ore 1900", so the year was lost.
    const parsed = parseFuzzyDate(input)
    expect(parsed.modifier).toBe(modifier)
    expect(parsed.year).not.toBeNull()
  })

  it('reads a range of years as BETWEEN, even behind a prefix', () => {
    expect(parseFuzzyDate('1918-1922')).toMatchObject({ modifier: 'BETWEEN', year: 1918, year2: 1922 })
    expect(parseFuzzyDate('khoảng 1918 đến 1922')).toMatchObject({ modifier: 'BETWEEN', year: 1918, year2: 1922 })
  })

  it('keeps an inverted range as raw text rather than inventing an order', () => {
    expect(parseFuzzyDate('1922-1918')).toMatchObject({ year: null, raw: '1922-1918' })
  })

  it('keeps an out-of-range month or day as raw text, so the save is not refused and the words lost', () => {
    expect(parseFuzzyDate('3/15/1890')).toMatchObject({ year: null, raw: '3/15/1890' })
    expect(parseFuzzyDate('32/1/1890')).toMatchObject({ year: null, raw: '32/1/1890' })
  })

  it('keeps 31 February, which real records contain and the server clamps only for sorting', () => {
    expect(parseFuzzyDate('31/2/1890')).toMatchObject({ year: 1890, month: 2, day: 31 })
  })

  it('reads "nhuận" as the leap month wherever it is written', () => {
    expect(parseFuzzyDate('10/4 nhuận/2020')).toMatchObject({ year: 2020, month: 4, day: 10, leapMonth: true })
    expect(parseFuzzyDate('10/4/2020 nhuận')).toMatchObject({ year: 2020, month: 4, day: 10, leapMonth: true })
    expect(parseFuzzyDate('10/4/2020')).toMatchObject({ leapMonth: false })
    // A giỗ known only by day and month parses with no year; the input keeps it only for âm lịch.
    expect(parseFuzzyDate('12/3')).toMatchObject({ year: null, month: 3, day: 12 })
    expect(parseFuzzyDate('12/3 nhuận')).toMatchObject({ year: null, month: 3, day: 12, leapMonth: true })
  })

  it('does not mark a leap month on a year-only date, which names no month', () => {
    expect(parseFuzzyDate('2020 nhuận')).toMatchObject({ year: 2020, leapMonth: false })
  })

  it('keeps text it cannot read, with no year', () => {
    expect(parseFuzzyDate('đời Tự Đức')).toMatchObject({ year: null, raw: 'đời Tự Đức' })
  })

  it('treats blank input as no date at all', () => {
    expect(parseFuzzyDate('   ')).toMatchObject({ year: null, raw: null })
  })
})
