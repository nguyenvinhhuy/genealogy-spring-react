import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { createBranch, invalidateBranchQueries, updateBranch } from '@/features/branch/api'
import { BranchPicker, NO_BRANCH } from '@/features/branch/components/branch-picker'
import { buildBranchSchema, type BranchFormValues } from '@/features/branch/lib/schemas'
import type { Branch } from '@/features/branch/types'
import { useVersionAtOpen } from '@/shared/hooks/use-version-at-open'
import { voidSubmit } from '@/shared/lib/forms'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'

/** Props of {@link BranchDialog}. */
interface BranchDialogProps {
  // Omitted for "create a root branch"; passed for editing an existing one.
  branch?: Branch
}

/**
 * Creates a branch, or edits an existing one's name, parent and description.
 *
 * @param props the branch being edited, or nothing to create a new one
 * @returns the dialog with its trigger button
 */
export function BranchDialog({ branch }: BranchDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const version = useVersionAtOpen(branch?.version ?? null, open)
  const schema = useMemo(() => buildBranchSchema(t), [t])
  const defaults: BranchFormValues = {
    name: branch?.name ?? '',
    parentId: branch?.parentId != null ? String(branch.parentId) : NO_BRANCH,
    description: branch?.description ?? '',
    changeNote: '',
  }

  const { control, register, handleSubmit, reset, setValue, formState } = useForm<BranchFormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })
  // useWatch rather than watch(): the compiler cannot memoise a component that calls watch().
  const parentId = useWatch({ control, name: 'parentId' })

  const mutation = useMutation({
    mutationFn: (values: BranchFormValues) => {
      const payload = {
        name: values.name.trim(),
        parentId: values.parentId === NO_BRANCH ? null : Number(values.parentId),
        description: values.description.trim() || null,
        changeNote: values.changeNote.trim() || null,
        version,
      }
      return branch ? updateBranch(branch.id, payload) : createBranch(payload)
    },
    onSuccess: async () => {
      await invalidateBranchQueries(queryClient)
      toast.success(t(branch ? 'branch.updated' : 'branch.created'))
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
        {branch ? (
          <Button variant="ghost" size="sm">
            <Pencil aria-hidden />
            {t('branch.edit')}
          </Button>
        ) : (
          <Button size="sm">
            <Plus aria-hidden />
            {t('branch.create')}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(branch ? 'branch.edit' : 'branch.create')}</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor="branch-name">{t('branch.name')}</Label>
            <Input id="branch-name" {...register('name')} />
            {formState.errors.name && <p className="text-destructive text-xs">{formState.errors.name.message}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="branch-parent">{t('branch.parent')}</Label>
            <BranchPicker
              id="branch-parent"
              value={parentId}
              onValueChange={(next) => setValue('parentId', next)}
              excludeSubtreeOf={branch?.id}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="branch-description">{t('branch.description')}</Label>
            <Textarea id="branch-description" {...register('description')} />
            {formState.errors.description && (
              <p className="text-destructive text-xs">{formState.errors.description.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="branch-change-note">{t('common.changeNote')}</Label>
            <Textarea
              id="branch-change-note"
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
