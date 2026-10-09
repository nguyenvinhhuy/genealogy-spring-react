import { describe, expect, it } from 'vitest'

import { initialOf } from './initial'

describe('initialOf', () => {
  it('takes the given name, which comes last', () => {
    expect(initialOf('Nguyễn Văn Đức')).toBe('Đ')
  })

  it('ignores extra spaces and keeps the diacritic', () => {
    expect(initialOf('  Lê   thị  ánh ')).toBe('Á')
  })

  it('answers "?" for an empty name', () => {
    expect(initialOf('   ')).toBe('?')
  })
})
