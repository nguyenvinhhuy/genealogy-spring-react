import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { invalidateFamilyQueries, updateChildLink } from '@/features/family/api'
import { buildChildLinkSchema, type ChildLinkFormValues, RELATION_TYPES } from '@/features/family/lib/schemas'
import type { Family, FamilyChild } from '@/features/family/types'
import { PersonName } from '@/features/person/components/person-name'
import { useVersionAtOpen } from '@/shared/hooks/use-version-at-open'
import { voidSubmit } from '@/shared/lib/forms'
import { Button } from '@/shared/ui/button'
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
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link ChildLinkDialog}. */
interface ChildLinkDialogProps {
  family: Family
  child: FamilyChild
}

/**
 * Corrects how one child relates to each partner of a union — con đẻ, con nuôi, con riêng — and their birth order.
 *
 * @param props the union and the child link being corrected
 * @returns the dialog with its trigger button
 */
export function ChildLinkDialog({ family, child }: ChildLinkDialogProps) {
  // The only way to undo the BIRTH default when a stepparent is recorded after the children (§8.6).
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const version = useVersionAtOpen(family.version, open)
  const schema = useMemo(() => buildChildLinkSchema(t), [t])
  const defaults: ChildLinkFormValues = {
    relationToP1: child.relationToP1,
    relationToP2: child.relationToP2,
    birthOrder: child.birthOrder,
    changeNote: '',
  }

  const { control, register, handleSubmit, reset, formState } = useForm<ChildLinkFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })

  const mutation = useMutation({
    mutationFn: (values: ChildLinkFormValues) =>
      updateChildLink(family.id, child.childId, {
        relationToP1: values.relationToP1,
        relationToP2: values.relationToP2,
        birthOrder: values.birthOrder,
        changeNote: values.changeNote.trim() || null,
        version,
      }),
    onSuccess: async () => {
      await invalidateFamilyQueries(queryClient)
      toast.success(t('family.childUpdated'))
      setOpen(false)
    },
  })

  // One row per recorded partner; a missing partner has no relation worth asking about.
  const slots = [
    { name: 'relationToP1' as const, partnerId: family.partner1Id },
    { name: 'relationToP2' as const, partnerId: family.partner2Id },
  ].filter((slot) => slot.partnerId != null)

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) {
          reset(defaults)
        }
      }}
    >
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <Pencil aria-hidden />
          {t('common.edit')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {t('family.editChild')}: <PersonName id={child.childId} />
          </DialogTitle>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          {slots.map((slot) => (
            <div key={slot.name} className="space-y-2">
              <Label htmlFor={`child-${slot.name}`}>
                {t('family.relationTo')} <PersonName id={slot.partnerId as number} />
              </Label>
              <Controller
                control={control}
                name={slot.name}
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id={`child-${slot.name}`} className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {RELATION_TYPES.map((relation) => (
                        <SelectItem key={relation} value={relation}>
                          {t(`family.relations.${relation}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          ))}

          <div className="space-y-2">
            <Label htmlFor="child-birth-order">{t('family.birthOrder')}</Label>
            <Input
              id="child-birth-order"
              type="number"
              min={1}
              {...register('birthOrder', { setValueAs: (raw: string) => (raw === '' ? null : Number(raw)) })}
            />
            <p className="text-muted-foreground text-xs">{t('family.birthOrderHint')}</p>
            {formState.errors.birthOrder && (
              <p className="text-destructive text-xs">{formState.errors.birthOrder.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="child-change-note">{t('common.changeNote')}</Label>
            <Textarea
              id="child-change-note"
              placeholder={t('common.changeNotePlaceholder')}
              {...register('changeNote')}
            />
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
