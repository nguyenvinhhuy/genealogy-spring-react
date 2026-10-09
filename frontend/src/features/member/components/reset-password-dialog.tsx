import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { KeyRound } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { resetPassword } from '@/features/member/api'
import { buildResetPasswordSchema, type ResetPasswordValues } from '@/features/member/lib/schemas'
import type { Member } from '@/features/member/types'
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

const EMPTY: ResetPasswordValues = { newPassword: '', confirmPassword: '' }

/** Props of {@link ResetPasswordDialog}. */
interface ResetPasswordDialogProps {
  member: Member
}

/**
 * Lets the clan head set a new password for a member who cannot sign in.
 *
 * @param props the account whose password is reset
 * @returns the dialog with its trigger button
 */
export function ResetPasswordDialog({ member }: ResetPasswordDialogProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const schema = useMemo(() => buildResetPasswordSchema(t), [t])

  const { register, handleSubmit, reset, formState } = useForm<ResetPasswordValues>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY,
  })

  const mutation = useMutation({
    mutationFn: (values: ResetPasswordValues) => resetPassword(member.id, { newPassword: values.newPassword }),
    onSuccess: () => {
      toast.success(t('member.passwordReset', { name: member.fullName }))
      setOpen(false)
      reset(EMPTY)
    },
  })

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        // Cleared both ways, so a typed password neither lingers in memory nor reappears for the next member.
        reset(EMPTY)
      }}
    >
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <KeyRound aria-hidden />
          {t('member.resetPassword')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('member.resetPasswordFor', { name: member.fullName })}</DialogTitle>
          <DialogDescription>{t('member.resetPasswordHint')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor="reset-new-password">{t('member.newPassword')}</Label>
            <Input
              id="reset-new-password"
              type="password"
              autoComplete="new-password"
              {...register('newPassword')}
            />
            {formState.errors.newPassword && (
              <p className="text-destructive text-xs">{formState.errors.newPassword.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="reset-confirm-password">{t('member.confirmPassword')}</Label>
            <Input
              id="reset-confirm-password"
              type="password"
              autoComplete="new-password"
              {...register('confirmPassword')}
            />
            {formState.errors.confirmPassword && (
              <p className="text-destructive text-xs">{formState.errors.confirmPassword.message}</p>
            )}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setOpen(false)
                reset(EMPTY)
              }}
            >
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? t('common.saving') : t('member.resetPassword')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
