import type { TFunction } from 'i18next'
import { describe, expect, it } from 'vitest'

import { buildPersonSchema, NO_GENDER } from './person-schema'
import { buildRelationSchema } from './relation-schema'

// Returns the key itself, so a test can assert which message a rule chose.
const t = ((key: string) => key) as unknown as TFunction

/**
 * Builds person form values with one primary name.
 *
 * @param gender the sex field's value
 * @param surname the primary name's họ
 * @returns the values
 */
function person(gender: string, surname: string) {
  return {
    gender,
    branchId: '',
    notes: '',
    names: [{ type: 'BIRTH', surname, middleName: '', givenName: 'An', primary: true }],
    changeNote: '',
  }
}

describe('buildPersonSchema', () => {
  it('refuses a new person with no họ and no chosen sex', () => {
    const result = buildPersonSchema(t, true).safeParse(person(NO_GENDER, ' '))
    expect(result.error?.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: ['gender'], message: 'person.validation.genderRequired' }),
        expect.objectContaining({ path: ['names', 0, 'surname'], message: 'person.validation.surnameRequired' }),
      ]),
    )
  })

  it('puts a missing tên under the primary row', () => {
    const values = person('MALE', 'Nguyễn')
    values.names[0].givenName = ''
    expect(buildPersonSchema(t, true).safeParse(values).error?.issues).toContainEqual(
      expect.objectContaining({ path: ['names', 0, 'givenName'], message: 'person.validation.givenNameRequired' }),
    )
  })

  it('accepts Chưa rõ as a chosen sex', () => {
    expect(buildPersonSchema(t, true).safeParse(person('UNKNOWN', 'Nguyễn')).success).toBe(true)
  })

  it('lets an edit keep a person recorded without a họ', () => {
    expect(buildPersonSchema(t, false).safeParse(person('UNKNOWN', '')).success).toBe(true)
  })
})

describe('buildRelationSchema', () => {
  const values = {
    kind: 'SPOUSE',
    familyId: '',
    middleName: '',
    givenName: 'Mỹ',
    birth: null,
    death: null,
    deceased: false,
  }

  it('refuses a spouse or child with no họ or no chosen sex', () => {
    const result = buildRelationSchema(t).safeParse({ ...values, gender: NO_GENDER, surname: '' })
    expect(result.error?.issues.map((issue) => issue.path[0])).toEqual(
      expect.arrayContaining(['gender', 'surname']),
    )
  })

  it('accepts a spouse with all three', () => {
    expect(buildRelationSchema(t).safeParse({ ...values, gender: 'FEMALE', surname: 'Lê' }).success).toBe(true)
  })
})
