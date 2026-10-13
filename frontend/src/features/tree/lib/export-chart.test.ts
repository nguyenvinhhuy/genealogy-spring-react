import { describe, expect, it } from 'vitest'

import { chartFileName } from '@/features/tree/lib/export-chart'

describe('chartFileName', () => {
  it('turns a Vietnamese name into a plain file name, đ included', () => {
    expect(chartFileName('Nguyễn Văn Cả')).toBe('cay-pha-he-nguyen-van-ca')
    expect(chartFileName('Đỗ Thị Đào')).toBe('cay-pha-he-do-thi-dao')
  })

  it('falls back to a generic name when nothing usable is left', () => {
    expect(chartFileName('')).toBe('cay-pha-he')
    expect(chartFileName('  ??  ')).toBe('cay-pha-he')
  })
})
