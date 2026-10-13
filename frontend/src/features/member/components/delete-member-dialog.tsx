import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { deleteMember, MEMBER_QUERY_KEY } from '@/features/member/api'
import type { Member } from '@/features/member/types'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'

/** Props of {@link DeleteMemberDialog}. */
interface DeleteMemberDialogProps {
  member: Member
}

/**
 * Asks the clan head to confirm deleting an account, and why.
 *
 * @param props the account to delete
 * @returns the dialog with its trigger button
 */
export function DeleteMemberDialog({ member }: DeleteMemberDialogProps) {
  // Hidden, not removed, on the server (§3.8): the trail must still say who made each change.
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const removal = useMutation({
    mutationFn: (changeNote: string) => deleteMember(member.id, changeNote || null),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: [MEMBER_QUERY_KEY] })
      toast.success(t('member.deleted', { name: member.fullName }))
    },
  })

  return (
    <ConfirmDeleteDialog
      label={t('member.delete')}
      title={t('member.deleteTitle', { name: member.fullName })}
      description={t('member.deleteDescription')}
      pending={removal.isPending}
      onConfirm={(changeNote) => removal.mutateAsync(changeNote)}
    />
  )
}
