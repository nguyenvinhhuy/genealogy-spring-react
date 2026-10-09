/** How seriously a data-quality finding should be taken. */
export type IssueSeverity = 'ERROR' | 'WARNING' | 'INFO'

/** Which consistency rule fired (CLAUDE.md §5.1). */
export type IssueCode =
  | 'ANCESTRY_CYCLE'
  | 'DEATH_BEFORE_BIRTH'
  | 'BURIAL_BEFORE_DEATH'
  | 'CONFLICTING_DATES'
  | 'BORN_BEFORE_PARENT'
  | 'BORN_AFTER_MOTHER_DEATH'
  | 'BORN_LONG_AFTER_FATHER_DEATH'
  | 'BORN_LONG_AFTER_PARENT_DEATH'
  | 'MOTHER_TOO_YOUNG'
  | 'MOTHER_TOO_OLD'
  | 'MARRIED_BEFORE_BIRTH'
  | 'MARRIED_TOO_YOUNG'
  | 'IMPLAUSIBLE_LIFESPAN'
  | 'CHILD_BORN_BEFORE_MARRIAGE'
  | 'POSSIBLE_DUPLICATE'

/**
 * One data-quality finding.
 */
// The API sends a code and its numbers rather than a sentence, so the message is built here and translated.
export interface QualityIssue {
  code: IssueCode
  severity: IssueSeverity
  personId: number
  personName: string
  relatedPersonId: number | null
  relatedPersonName: string | null
  familyId: number | null
  params: Record<string, string | number>
}
