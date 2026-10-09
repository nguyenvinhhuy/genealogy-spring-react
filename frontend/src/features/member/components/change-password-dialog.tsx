import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { changeOwnPassword } from '@/features/member/api'
import { buildChangePasswordSchema, type ChangePasswordValues } from '@/features/member/lib/schemas'
import { voidSubmit } from '@/shared/lib/forms'
import { endSession } from '@/shared/lib/session'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

const EMPTY: ChangePasswordValues = { currentPassword: '', newPassword: '', confirmPassword: '' }

/** Props of {@link ChangePasswordDialog}. */
interface ChangePasswordDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Lets the signed-in member change their own password, then sends them to sign in again.
 *
 * @param props whether the dialog is open, and how to close it
 * @returns the dialog
 */
export function ChangePasswordDialog({ open, onOpenChange }: ChangePasswordDialogProps) {
  const { t } = useTranslation()
  const schema = useMemo(() => buildChangePasswordSchema(t), [t])

  const { register, handleSubmit, reset, formState } = useForm<ChangePasswordValues>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY,
  })

  const mutation = useMutation({
    mutationFn: (values: ChangePasswordValues) =>
      changeOwnPassword({ currentPassword: values.currentPassword, newPassword: values.newPassword }),
    onSuccess: async () => {
      // The server has already ended every session; this only clears the now-dead cookie from the browser.
      await endSession()
      // No navigate: RequireAuth sends the signed-out page to sign-in with where it was, so signing in returns here.
      toast.success(t('member.passwordChanged'))
    },
  })

  /**
   * Opens or closes the dialog, clearing the typed passwords whenever it closes.
   *
   * @param next whether the dialog should be open
   */
  const setOpen = (next: boolean) => {
    // Every way out, Huỷ included: the Huỷ button used to leave three passwords in the form (§8.11 #15).
    if (!next) {
      reset(EMPTY)
    }
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('member.changePassword')}</DialogTitle>
          <DialogDescription>{t('member.changePasswordHint')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor="current-password">{t('member.currentPassword')}</Label>
            <Input
              id="current-password"
              type="password"
              autoComplete="current-password"
              {...register('currentPassword')}
            />
            {formState.errors.currentPassword && (
              <p className="text-destructive text-xs">{formState.errors.currentPassword.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="new-password">{t('member.newPassword')}</Label>
            <Input id="new-password" type="password" autoComplete="new-password" {...register('newPassword')} />
            {formState.errors.newPassword && (
              <p className="text-destructive text-xs">{formState.errors.newPassword.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="confirm-password">{t('member.confirmPassword')}</Label>
            <Input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              {...register('confirmPassword')}
            />
            {formState.errors.confirmPassword && (
              <p className="text-destructive text-xs">{formState.errors.confirmPassword.message}</p>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? t('common.saving') : t('member.changePassword')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
