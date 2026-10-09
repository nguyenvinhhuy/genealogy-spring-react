import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { UserPlus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { createMember, MEMBER_QUERY_KEY } from '@/features/member/api'
import { buildCreateMemberSchema, ROLES, type CreateMemberValues } from '@/features/member/lib/schemas'
import { voidSubmit } from '@/shared/lib/forms'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

const EMPTY: CreateMemberValues = {
  fullName: '',
  email: '',
  role: 'MEMBER',
  newPassword: '',
  confirmPassword: '',
}

/**
 * Lets the clan head open an account for someone, with a first password to hand over in person.
 *
 * @returns the dialog with its trigger button
 */
export function CreateMemberDialog() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const schema = useMemo(() => buildCreateMemberSchema(t), [t])

  const { control, register, handleSubmit, reset, setValue, formState } = useForm<CreateMemberValues>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY,
  })
  const role = useWatch({ control, name: 'role' })

  const mutation = useMutation({
    mutationFn: (values: CreateMemberValues) =>
      createMember({
        fullName: values.fullName,
        email: values.email,
        password: values.newPassword,
        role: values.role,
      }),
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: [MEMBER_QUERY_KEY] })
      toast.success(t('member.created', { name: created.fullName }))
      changeOpen(false)
    },
  })

  /**
   * Opens or closes the dialog, clearing the form either way so no password stays in memory.
   *
   * @param next whether the dialog should be open
   */
  function changeOpen(next: boolean) {
    setOpen(next)
    reset(EMPTY)
  }

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <UserPlus aria-hidden />
          {t('member.create')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('member.create')}</DialogTitle>
          <DialogDescription>{t('member.createHint')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor="create-member-name">{t('member.fullName')}</Label>
            <Input id="create-member-name" autoComplete="off" {...register('fullName')} />
            {formState.errors.fullName && (
              <p className="text-destructive text-xs">{formState.errors.fullName.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="create-member-email">{t('member.email')}</Label>
            <Input id="create-member-email" type="email" autoComplete="off" {...register('email')} />
            {formState.errors.email && <p className="text-destructive text-xs">{formState.errors.email.message}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="create-member-role">{t('member.role')}</Label>
            <Select value={role} onValueChange={(next) => setValue('role', next as CreateMemberValues['role'])}>
              <SelectTrigger id="create-member-role" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ROLES.map((option) => (
                  <SelectItem key={option} value={option}>
                    {t(`role.${option}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-muted-foreground text-xs">{t(`member.roleHint.${role}`)}</p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="create-member-password">{t('member.firstPassword')}</Label>
            <Input
              id="create-member-password"
              type="password"
              autoComplete="new-password"
              {...register('newPassword')}
            />
            {formState.errors.newPassword && (
              <p className="text-destructive text-xs">{formState.errors.newPassword.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="create-member-confirm">{t('member.confirmFirstPassword')}</Label>
            <Input
              id="create-member-confirm"
              type="password"
              autoComplete="new-password"
              {...register('confirmPassword')}
            />
            {formState.errors.confirmPassword && (
              <p className="text-destructive text-xs">{formState.errors.confirmPassword.message}</p>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => changeOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? t('common.saving') : t('member.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
