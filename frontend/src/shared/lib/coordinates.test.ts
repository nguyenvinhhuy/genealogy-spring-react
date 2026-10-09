import { describe, expect, it } from 'vitest'

import {
  coordinateText,
  isCoordinateValid,
  MAX_LATITUDE,
  parseCoordinate,
  splitCoordinatePair,
} from './coordinates'

describe('parseCoordinate', () => {
  it('reads a point and a decimal comma alike', () => {
    expect(parseCoordinate('21.028511')).toBe(21.028511)
    expect(parseCoordinate('21,028511')).toBe(21.028511)
  })

  it('answers null for an empty field and NaN for text that is not a number', () => {
    expect(parseCoordinate('  ')).toBeNull()
    expect(parseCoordinate('Bắc 21')).toBeNaN()
  })

  it('rounds to the six decimals the server stores', () => {
    expect(parseCoordinate('21.0285119')).toBe(21.028512)
  })
})

describe('splitCoordinatePair', () => {
  it('splits the pair Google Maps copies', () => {
    expect(splitCoordinatePair('21.028511, 105.804817')).toEqual([21.028511, 105.804817])
  })

  it('splits a pair written with decimal commas and a semicolon', () => {
    expect(splitCoordinatePair('21,02; 105,8')).toEqual([21.02, 105.8])
  })

  it('does not read a single decimal-comma number as a pair', () => {
    expect(splitCoordinatePair('21,028511')).toBeNull()
  })
})

describe('isCoordinateValid', () => {
  it('accepts the equator, which a truthiness check once read as missing', () => {
    expect(isCoordinateValid(0, MAX_LATITUDE)).toBe(true)
    expect(coordinateText(0)).toBe('0')
  })

  it('refuses a latitude past the pole', () => {
    expect(isCoordinateValid(91, MAX_LATITUDE)).toBe(false)
  })
})
