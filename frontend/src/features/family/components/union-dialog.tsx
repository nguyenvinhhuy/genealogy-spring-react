import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { invalidateFamilyQueries, updateFamily } from '@/features/family/api'
import { buildUnionSchema, FAMILY_STATUSES, type UnionFormValues } from '@/features/family/lib/schemas'
import type { Family } from '@/features/family/types'
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

/** Props of {@link UnionDialog}. */
interface UnionDialogProps {
  family: Family
}

/**
 * Edits a union's status and its position among a person's unions.
 *
 * @param props the union being edited
 * @returns the dialog with its trigger button
 */
export function UnionDialog({ family }: UnionDialogProps) {
  // Partners are not edited here: re-pointing a union at someone else is a merge or a new union, not a correction.
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const version = useVersionAtOpen(family.version, open)
  const schema = useMemo(() => buildUnionSchema(t), [t])
  const defaults: UnionFormValues = { status: family.status, orderIndex: family.orderIndex, changeNote: '' }

  const { control, register, handleSubmit, reset, formState } = useForm<UnionFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })

  const mutation = useMutation({
    mutationFn: (values: UnionFormValues) =>
      updateFamily(family.id, {
        partner1Id: family.partner1Id,
        partner2Id: family.partner2Id,
        status: values.status,
        orderIndex: values.orderIndex,
        changeNote: values.changeNote.trim() || null,
        version,
      }),
    onSuccess: async () => {
      await invalidateFamilyQueries(queryClient)
      toast.success(t('family.updated'))
      setOpen(false)
    },
  })

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
          {t('family.editUnion')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('family.editUnion')}</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor="union-status">{t('family.status')}</Label>
            <Controller
              control={control}
              name="status"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="union-status" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {FAMILY_STATUSES.map((status) => (
                      <SelectItem key={status} value={status}>
                        {t(`family.statuses.${status}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="union-order">{t('family.orderIndex')}</Label>
            <Input
              id="union-order"
              type="number"
              min={0}
              {...register('orderIndex', { valueAsNumber: true })}
            />
            <p className="text-muted-foreground text-xs">{t('family.orderIndexHint')}</p>
            {formState.errors.orderIndex && (
              <p className="text-destructive text-xs">{formState.errors.orderIndex.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="union-change-note">{t('common.changeNote')}</Label>
            <Textarea
              id="union-change-note"
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
