import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo } from 'react'
import { Controller, useFieldArray, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { BranchPicker, NO_BRANCH } from '@/features/branch/components/branch-picker'
import {
  buildPersonSchema,
  GENDERS,
  NAME_TYPES,
  type PersonFormValues,
} from '@/features/person/lib/person-schema'
import type { PersonDetail, PersonPayload } from '@/features/person/types'
import { voidSubmit } from '@/shared/lib/forms'
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
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link PersonForm}. */
interface PersonFormProps {
  // The person being edited, or undefined to create a new one.
  person?: PersonDetail
  submitting: boolean
  onSubmit: (payload: PersonPayload) => void
  onCancel: () => void
}

/**
 * Builds the form's starting values from the person being edited, or an empty one-name form.
 *
 * @param person the person being edited, if any
 * @returns the default values
 */
function defaultsOf(person: PersonDetail | undefined): PersonFormValues {
  return {
    gender: person?.gender ?? 'UNKNOWN',
    branchId: person?.branchId != null ? String(person.branchId) : NO_BRANCH,
    notes: person?.notes ?? '',
    names: person?.names.length
      ? person.names.map((name) => ({
          type: name.type,
          surname: name.surname ?? '',
          middleName: name.middleName ?? '',
          givenName: name.givenName,
          primary: name.primary,
        }))
      : [{ type: 'BIRTH', surname: '', middleName: '', givenName: '', primary: true }],
    changeNote: '',
  }
}

/**
 * Creates or edits a person: names, sex, chi and notes.
 *
 * @param props the person being edited, submit state, and the submit/cancel handlers
 * @returns the form element
 */
export function PersonForm({ person, submitting, onSubmit, onCancel }: PersonFormProps) {
  const { t } = useTranslation()
  const schema = useMemo(() => buildPersonSchema(t), [t])

  const { control, register, handleSubmit, getValues, setValue, formState } = useForm<PersonFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaultsOf(person),
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'names' })

  const makePrimary = (index: number) =>
    getValues('names').forEach((_, at) => setValue(`names.${at}.primary`, at === index, { shouldDirty: true }))

  const removeName = (index: number) => {
    const wasPrimary = getValues(`names.${index}.primary`)
    remove(index)
    // Dropping the primary row would leave the person with none, so the first row takes over.
    if (wasPrimary) {
      setValue('names.0.primary', true, { shouldDirty: true })
    }
  }

  const submit = (values: PersonFormValues) =>
    onSubmit({
      gender: values.gender,
      branchId: values.branchId === NO_BRANCH ? null : Number(values.branchId),
      notes: values.notes.trim() || null,
      names: values.names
        .filter((name) => name.givenName.trim())
        .map((name) => ({
          type: name.type,
          surname: name.surname.trim() || null,
          middleName: name.middleName.trim() || null,
          givenName: name.givenName.trim(),
          primary: name.primary,
        })),
      changeNote: values.changeNote.trim() || null,
      version: person?.version,
    })

  return (
    <form className="space-y-6" onSubmit={voidSubmit(handleSubmit(submit))}>
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <Label>{t('person.names')}</Label>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => append({ type: 'HUY', surname: '', middleName: '', givenName: '', primary: false })}
          >
            {t('person.addName')}
          </Button>
        </div>

        {fields.map((field, index) => (
          <div key={field.id} className="grid grid-cols-[9rem_1fr_1fr_1fr] items-start gap-2 rounded-lg border p-3">
            <div className="space-y-1">
              <Label htmlFor={`name-type-${index}`}>{t('person.nameType')}</Label>
              <Controller
                control={control}
                name={`names.${index}.type`}
                render={({ field: type }) => (
                  <Select value={type.value} onValueChange={type.onChange}>
                    <SelectTrigger id={`name-type-${index}`} className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {NAME_TYPES.map((value) => (
                        <SelectItem key={value} value={value}>
                          {t(`person.nameTypes.${value}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor={`surname-${index}`}>{t('person.surname')}</Label>
              <Input id={`surname-${index}`} {...register(`names.${index}.surname`)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor={`middle-${index}`}>{t('person.middleName')}</Label>
              <Input id={`middle-${index}`} {...register(`names.${index}.middleName`)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor={`given-${index}`}>{t('person.givenName')}</Label>
              <Input id={`given-${index}`} {...register(`names.${index}.givenName`)} />
            </div>

            <div className="col-span-4 flex gap-2">
              <Controller
                control={control}
                name={`names.${index}.primary`}
                render={({ field: primary }) => (
                  <Button
                    type="button"
                    variant={primary.value ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => makePrimary(index)}
                  >
                    {primary.value ? t('person.primary') : t('person.makePrimary')}
                  </Button>
                )}
              />
              {fields.length > 1 && (
                <Button type="button" variant="ghost" size="sm" onClick={() => removeName(index)}>
                  {t('common.remove')}
                </Button>
              )}
            </div>
          </div>
        ))}
        {formState.errors.names && (
          <p className="text-destructive text-xs">
            {formState.errors.names.message ?? formState.errors.names.root?.message}
          </p>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="gender">{t('person.gender')}</Label>
        <Controller
          control={control}
          name="gender"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger id="gender" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {GENDERS.map((value) => (
                  <SelectItem key={value} value={value}>
                    {t(`person.genders.${value}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="branch">{t('person.branch')}</Label>
        <Controller
          control={control}
          name="branchId"
          render={({ field }) => <BranchPicker id="branch" value={field.value} onValueChange={field.onChange} />}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="notes">{t('person.notes')}</Label>
        <Textarea id="notes" {...register('notes')} />
      </div>

      {person && (
        <div className="space-y-2">
          <Label htmlFor="change-note">{t('common.changeNote')}</Label>
          <Textarea id="change-note" placeholder={t('common.changeNotePlaceholder')} {...register('changeNote')} />
        </div>
      )}

      <div className="flex gap-2">
        <Button type="submit" disabled={submitting}>
          {submitting ? t('common.saving') : t('common.save')}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
      </div>
    </form>
  )
}
