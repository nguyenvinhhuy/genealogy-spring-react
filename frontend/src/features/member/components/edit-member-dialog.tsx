import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { MEMBER_QUERY_KEY, updateMember } from '@/features/member/api'
import { buildUpdateMemberSchema, ROLES, type UpdateMemberValues } from '@/features/member/lib/schemas'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'

// The two values of the status picker; a Select carries strings, the request a boolean.
const ACTIVE = 'active'
const INACTIVE = 'inactive'

/** Props of {@link EditMemberDialog}. */
interface EditMemberDialogProps {
  member: Member
  // The clan head's own row: role and status stay put, or they could lock themselves out.
  isSelf: boolean
}

/**
 * Lets the clan head rename an account, change its role, or disable or enable it.
 *
 * @param props the account to edit, and whether it is the caller's own
 * @returns the dialog with its trigger button
 */
export function EditMemberDialog({ member, isSelf }: EditMemberDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const schema = useMemo(() => buildUpdateMemberSchema(t), [t])
  const defaults: UpdateMemberValues = {
    fullName: member.fullName,
    role: member.role,
    active: member.active,
    changeNote: '',
  }

  const { control, register, handleSubmit, reset, setValue, formState } = useForm<UpdateMemberValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  })
  const role = useWatch({ control, name: 'role' })
  const active = useWatch({ control, name: 'active' })

  const mutation = useMutation({
    mutationFn: (values: UpdateMemberValues) =>
      updateMember(member.id, {
        fullName: values.fullName,
        role: values.role,
        active: values.active,
        changeNote: values.changeNote.trim() || null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: [MEMBER_QUERY_KEY] })
      toast.success(t('member.updated', { name: member.fullName }))
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
          {t('member.edit')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('member.editTitle', { name: member.fullName })}</DialogTitle>
          <DialogDescription>{t(isSelf ? 'member.editSelfHint' : 'member.editHint')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={voidSubmit(handleSubmit((values) => mutation.mutate(values)))}>
          <div className="space-y-2">
            <Label htmlFor={`edit-member-name-${member.id}`}>{t('member.fullName')}</Label>
            <Input id={`edit-member-name-${member.id}`} {...register('fullName')} />
            {formState.errors.fullName && (
              <p className="text-destructive text-xs">{formState.errors.fullName.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor={`edit-member-role-${member.id}`}>{t('member.role')}</Label>
            <Select
              value={role}
              disabled={isSelf}
              onValueChange={(next) => setValue('role', next as UpdateMemberValues['role'])}
            >
              <SelectTrigger id={`edit-member-role-${member.id}`} className="w-full">
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
            <Label htmlFor={`edit-member-status-${member.id}`}>{t('member.status')}</Label>
            <Select
              value={active ? ACTIVE : INACTIVE}
              disabled={isSelf}
              onValueChange={(next) => setValue('active', next === ACTIVE)}
            >
              <SelectTrigger id={`edit-member-status-${member.id}`} className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ACTIVE}>{t('member.active')}</SelectItem>
                <SelectItem value={INACTIVE}>{t('member.inactive')}</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor={`edit-member-note-${member.id}`}>{t('common.changeNote')}</Label>
            <Textarea
              id={`edit-member-note-${member.id}`}
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
