import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FuzzyDateInput } from '@/features/event/components/fuzzy-date-input'
import type { Family } from '@/features/family/types'
import type { Gender } from '@/features/person/types'
import { createSuggestion, SUGGESTION_QUERY_KEY } from '@/features/suggestion/api'
import {
  buildSuggestionSchema,
  FIRST_UNION,
  KEEP_GENDER,
  NEW_UNION,
  type SuggestionFormValues,
} from '@/features/suggestion/lib/schemas'
import type { SuggestionPayload } from '@/features/suggestion/types'
import { voidSubmit } from '@/shared/lib/forms'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link SuggestCard}. */
interface SuggestCardProps {
  personId: number
  // The person's unions, so a proposed child can be placed in the right one.
  families: Family[]
}

const DEFAULTS: SuggestionFormValues = {
  kind: 'NOTE',
  message: '',
  relation: 'CHILD',
  familyId: FIRST_UNION,
  gender: KEEP_GENDER,
  surname: '',
  middleName: '',
  givenName: '',
  birth: null,
  death: null,
}

/**
 * Lets any signed-in member propose a note, a correction, or a new relative, for a reviewer to apply.
 *
 * @param props which person the suggestion is about, and their unions
 * @returns the card element
 */
export function SuggestCard({ personId, families }: SuggestCardProps) {
  // A note stays the default: it asks the least, and a con cháu who has one fact should not face a form.
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const schema = useMemo(() => buildSuggestionSchema(t), [t])
  // Bumped after a send, so the date fields, which keep their own text, start empty again.
  const [generation, setGeneration] = useState(0)
  const { control, register, handleSubmit, reset, clearErrors, formState } = useForm<SuggestionFormValues>({
    resolver: zodResolver(schema),
    // Not resolved here: the unions load after this form, and a default frozen then made every child a new union.
    defaultValues: DEFAULTS,
  })
  const kind = useWatch({ control, name: 'kind' })
  const relation = useWatch({ control, name: 'relation' })

  const suggest = useMutation({
    mutationFn: (values: SuggestionFormValues) => createSuggestion(toPayload(personId, values, families)),
    onSuccess: () => {
      reset(DEFAULTS)
      setGeneration((current) => current + 1)
      // The member's own list and the reviewer's badge both count it.
      void queryClient.invalidateQueries({ queryKey: [SUGGESTION_QUERY_KEY] })
      toast.success(t('suggestion.sent'))
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('suggestion.cardTitle')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => suggest.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor={`suggest-kind-${personId}`}>{t('suggestion.what')}</Label>
            <Controller
              control={control}
              name="kind"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(next) => {
                    // An error from the other mode ("nothing proposed") must not follow the member into this one.
                    clearErrors()
                    field.onChange(next)
                  }}
                >
                  <SelectTrigger id={`suggest-kind-${personId}`} className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(['NOTE', 'UPDATE', 'CREATE'] as const).map((value) => (
                      <SelectItem key={value} value={value}>
                        {t(`suggestion.offer.${value}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          {kind === 'CREATE' && (
            <div className="grid gap-2 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor={`suggest-relation-${personId}`}>{t('family.relationKind')}</Label>
                <Controller
                  control={control}
                  name="relation"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id={`suggest-relation-${personId}`} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="CHILD">{t('family.child')}</SelectItem>
                        <SelectItem value="SPOUSE">{t('family.spouse')}</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
              {relation === 'CHILD' && (
                <div className="space-y-2">
                  <Label htmlFor={`suggest-family-${personId}`}>{t('family.withUnion')}</Label>
                  <Controller
                    control={control}
                    name="familyId"
                    render={({ field }) => (
                      <Select value={chosenUnion(field.value, families)} onValueChange={field.onChange}>
                        <SelectTrigger id={`suggest-family-${personId}`} className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {families.map((family) => (
                            <SelectItem key={family.id} value={String(family.id)}>
                              {t('family.unionN', { n: family.orderIndex + 1 })}
                            </SelectItem>
                          ))}
                          <SelectItem value={NEW_UNION}>{t('family.newSingleParentUnion')}</SelectItem>
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>
              )}
            </div>
          )}

          {kind !== 'NOTE' && (
            <div key={generation} className="space-y-4">
              <p className="text-muted-foreground text-xs">
                {t(kind === 'UPDATE' ? 'suggestion.updateHint' : 'suggestion.createHint')}
              </p>
              <div className="grid gap-2 sm:grid-cols-3">
                <div className="space-y-2">
                  <Label htmlFor={`suggest-surname-${personId}`}>{t('person.surname')}</Label>
                  <Input id={`suggest-surname-${personId}`} {...register('surname')} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor={`suggest-middle-${personId}`}>{t('person.middleName')}</Label>
                  <Input id={`suggest-middle-${personId}`} {...register('middleName')} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor={`suggest-given-${personId}`}>{t('person.givenName')}</Label>
                  <Input id={`suggest-given-${personId}`} {...register('givenName')} />
                </div>
              </div>
              {formState.errors.givenName && (
                <p className="text-destructive text-xs">{formState.errors.givenName.message}</p>
              )}
              <div className="space-y-2">
                <Label htmlFor={`suggest-gender-${personId}`}>{t('person.gender')}</Label>
                <Controller
                  control={control}
                  name="gender"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id={`suggest-gender-${personId}`} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={KEEP_GENDER}>
                          {t(kind === 'UPDATE' ? 'suggestion.keepGender' : 'person.genders.UNKNOWN')}
                        </SelectItem>
                        {(['MALE', 'FEMALE'] as const).map((gender) => (
                          <SelectItem key={gender} value={gender}>
                            {t(`person.genders.${gender}`)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <Controller
                  control={control}
                  name="birth"
                  render={({ field }) => (
                    <FuzzyDateInput
                      id={`suggest-birth-${personId}`}
                      label={t('suggestion.birth')}
                      value={field.value}
                      onChange={field.onChange}
                    />
                  )}
                />
                <Controller
                  control={control}
                  name="death"
                  render={({ field }) => (
                    <FuzzyDateInput
                      id={`suggest-death-${personId}`}
                      label={t('suggestion.death')}
                      value={field.value}
                      onChange={field.onChange}
                    />
                  )}
                />
              </div>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor={`suggest-message-${personId}`}>
              {t(kind === 'NOTE' ? 'suggestion.message' : 'suggestion.basis')}
            </Label>
            <Textarea
              id={`suggest-message-${personId}`}
              placeholder={t('suggestion.messagePlaceholder')}
              {...register('message')}
            />
            {formState.errors.message && (
              <p className="text-destructive text-xs">{formState.errors.message.message}</p>
            )}
          </div>
          <Button type="submit" size="sm" disabled={suggest.isPending}>
            {suggest.isPending ? t('common.saving') : t('suggestion.send')}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

/**
 * Resolves the union select's value, so "not chosen yet" reads as the person's first union.
 *
 * @param value what the form holds
 * @param families the person's unions
 * @returns the select value to show and send
 */
function chosenUnion(value: string, families: Family[]): string {
  if (value !== FIRST_UNION) {
    return value
  }
  return families[0] ? String(families[0].id) : NEW_UNION
}

/**
 * Turns the form into the request the server takes, sending only what the member actually filled in.
 *
 * @param personId the person the card is on
 * @param values the validated form
 * @param families the person's unions as they are now, which settle an unchosen union
 * @returns the payload
 */
function toPayload(personId: number, values: SuggestionFormValues, families: Family[]): SuggestionPayload {
  const message = values.message.trim()
  if (values.kind === 'NOTE') {
    return { targetType: 'PERSON', targetId: personId, kind: 'NOTE', message }
  }
  const given = values.givenName.trim()
  const names = given
    ? [
        {
          type: 'BIRTH' as const,
          surname: values.surname.trim() || null,
          middleName: values.middleName.trim() || null,
          givenName: given,
          primary: true,
        },
      ]
    : null
  const gender: Gender | null = values.gender === KEEP_GENDER ? null : values.gender
  const person = { names, gender, birth: values.birth, death: values.death }
  if (values.kind === 'UPDATE') {
    return { targetType: 'PERSON', targetId: personId, kind: 'UPDATE', person, message }
  }
  // A new person is anchored to this one, so approval places them in a union and gives them a đời (§8.9 #15).
  const union = chosenUnion(values.familyId, families)
  const familyId = values.relation === 'CHILD' && union !== NEW_UNION ? Number(union) : null
  return {
    targetType: 'PERSON',
    kind: 'CREATE',
    person: { ...person, anchor: { personId, kind: values.relation, familyId } },
    message,
  }
}
