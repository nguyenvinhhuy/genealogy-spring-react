import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { UserPlus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { createEvent } from '@/features/event/api'
import { FuzzyDateInput } from '@/features/event/components/fuzzy-date-input'
import { addRelation, invalidateFamilyQueries } from '@/features/family/api'
import type { Family } from '@/features/family/types'
import { buildRelationSchema, type RelationFormValues } from '@/features/person/lib/relation-schema'
import { voidSubmit } from '@/shared/lib/forms'
import { Button } from '@/shared/ui/button'
import { Checkbox } from '@/shared/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'

// Radix Select cannot hold an empty-string value, so "start a new union" needs a sentinel of its own.
const NEW_UNION = 'new'

/**
 * Writes the birth and death a new person was entered with, as the events the rest of the app reads.
 *
 * @param personId the person just added
 * @param values what the form held
 * @returns false when an event could not be saved, so the caller can say so
 */
async function recordLife(personId: number, values: RelationFormValues): Promise<boolean> {
  try {
    if (values.birth) {
      await createEvent({ subjectId: personId, type: 'BIRTH', date: values.birth })
    }
    // "Đã mất" with no date is still a DEATH: living is derived from events, and nothing else says they died.
    if (values.death || values.deceased) {
      await createEvent({ subjectId: personId, type: 'DEATH', date: values.death })
    }
    return true
  } catch {
    return false
  }
}

/** Props of {@link AddRelationDialog}. */
interface AddRelationDialogProps {
  personId: number
  families: Family[]
}

/**
 * Adds a spouse or a child to one person, creating the new person in the same step.
 *
 * @param props the person being added to and their existing unions
 * @returns the dialog with its trigger button
 */
export function AddRelationDialog({ personId, families }: AddRelationDialogProps) {
  // Asks for a name and little else: entering people has to stay cheap or the tree dies at 30 (§6.1).
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const schema = useMemo(() => buildRelationSchema(t), [t])

  const defaults: RelationFormValues = {
    kind: 'SPOUSE',
    familyId: families[0] ? String(families[0].id) : NEW_UNION,
    gender: 'UNKNOWN',
    surname: '',
    middleName: '',
    givenName: '',
    birth: null,
    death: null,
    deceased: false,
  }
  const { control, register, handleSubmit, reset, formState } = useForm<RelationFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })
  // useWatch rather than watch(): the compiler cannot memoise a component that calls watch().
  const kind = useWatch({ control, name: 'kind' })

  const mutation = useMutation({
    mutationFn: async (values: RelationFormValues) => {
      const added = await addRelation(personId, {
        kind: values.kind,
        gender: values.gender,
        names: [
          {
            type: 'BIRTH',
            surname: values.surname.trim() || null,
            middleName: values.middleName.trim() || null,
            givenName: values.givenName.trim(),
            primary: true,
          },
        ],
        // A union that is not this person's is refused by the server, so a stale choice cannot misfile a child.
        familyId: values.kind === 'CHILD' && values.familyId !== NEW_UNION ? Number(values.familyId) : null,
      })
      // Two further requests: the family feature cannot write events, so the dates follow the new person.
      const stored = await recordLife(added.personId, values)
      return { added, stored }
    },
    onSuccess: async ({ stored }) => {
      await invalidateFamilyQueries(queryClient)
      toast.success(t('family.added'))
      if (!stored) {
        // The person exists and is findable: say what is missing instead of failing a save that mostly worked.
        toast.warning(t('family.datesNotSaved'))
      }
      setOpen(false)
    },
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        // Reset on open, not on close: the union list may have loaded or changed since the last time.
        if (next) {
          reset(defaults)
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <UserPlus aria-hidden />
          {t('family.addRelation')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('family.addRelation')}</DialogTitle>
        </DialogHeader>

        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor="relation-kind">{t('family.relationKind')}</Label>
            <Controller
              control={control}
              name="kind"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="relation-kind" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="SPOUSE">{t('family.spouse')}</SelectItem>
                    <SelectItem value="CHILD">{t('family.child')}</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          {kind === 'CHILD' && (
            <div className="space-y-2">
              <Label htmlFor="relation-family">{t('family.withUnion')}</Label>
              <Controller
                control={control}
                name="familyId"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="relation-family" className="w-full">
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

          <div className="space-y-2">
            <Label htmlFor="relation-gender">{t('person.gender')}</Label>
            <Controller
              control={control}
              name="gender"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="relation-gender" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(['MALE', 'FEMALE', 'UNKNOWN'] as const).map((gender) => (
                      <SelectItem key={gender} value={gender}>
                        {t(`person.genders.${gender}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div className="space-y-2">
              <Label htmlFor="relation-surname">{t('person.surname')}</Label>
              <Input id="relation-surname" {...register('surname')} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="relation-middle">{t('person.middleName')}</Label>
              <Input id="relation-middle" {...register('middleName')} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="relation-given">{t('person.givenName')}</Label>
              <Input id="relation-given" {...register('givenName')} />
            </div>
          </div>
          {formState.errors.givenName && (
            <p className="text-destructive text-xs">{formState.errors.givenName.message}</p>
          )}

          <div className="grid gap-4">
            <Controller
              control={control}
              name="birth"
              render={({ field }) => (
                <FuzzyDateInput
                  id="relation-birth"
                  label={t('family.birth')}
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
                  id="relation-death"
                  label={t('family.death')}
                  value={field.value}
                  onChange={field.onChange}
                />
              )}
            />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Controller
                control={control}
                name="deceased"
                render={({ field }) => (
                  <Checkbox
                    id="relation-deceased"
                    checked={field.value}
                    onCheckedChange={(checked) => field.onChange(checked === true)}
                  />
                )}
              />
              <Label htmlFor="relation-deceased">{t('family.deceased')}</Label>
            </div>
            <p className="text-muted-foreground text-xs">{t('family.deceasedHint')}</p>
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? t('common.saving') : t('common.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
