import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { fetchMembers, MEMBER_QUERY_KEY } from '@/features/member/api'
import { CreateMemberDialog } from '@/features/member/components/create-member-dialog'
import { EditMemberDialog } from '@/features/member/components/edit-member-dialog'
import { ResetPasswordDialog } from '@/features/member/components/reset-password-dialog'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { Pager, TableCard, TableMessage } from '@/shared/components/table-card'
import { problemMessage } from '@/shared/lib/problem-detail'
import { useAuthStore } from '@/shared/store/auth-store'
import { Badge } from '@/shared/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

/**
 * Lists every account for the clan head, who opens, edits, disables and resets them here.
 *
 * @returns the page element
 */
export function MemberPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const ownId = useAuthStore((state) => state.member?.id)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [MEMBER_QUERY_KEY, page],
    queryFn: () => fetchMembers(page),
    placeholderData: keepPreviousData,
  })
  const members = data?.content ?? []
  const totalPages = data?.page.totalPages ?? 0

  return (
    <PageContainer>
      <PageHeader title={t('member.pageTitle')} description={t('member.subtitle')} actions={<CreateMemberDialog />} />

      <TableCard
        footer={
          totalPages > 1 && (
            <Pager page={page} totalPages={totalPages} onChange={setPage} label={t('member.pageTitle')} />
          )
        }
      >
        {isLoading && <TableMessage>{t('common.loading')}</TableMessage>}
        {/* A failed read must not look like a clan with no accounts. */}
        {isError && <TableMessage tone="error">{problemMessage(error, t('common.unexpectedError'))}</TableMessage>}
        {!isLoading && !isError && members.length === 0 && <TableMessage>{t('member.empty')}</TableMessage>}

        {members.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('member.fullName')}</TableHead>
                <TableHead>{t('member.email')}</TableHead>
                <TableHead>{t('member.role')}</TableHead>
                <TableHead>{t('member.status')}</TableHead>
                <TableHead className="w-0">{t('common.actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {members.map((member) => (
                <TableRow key={member.id}>
                  <TableCell className="font-medium">{member.fullName}</TableCell>
                  <TableCell className="text-muted-foreground">{member.email}</TableCell>
                  <TableCell>{t(`role.${member.role}`)}</TableCell>
                  <TableCell>
                    <Badge variant={member.active ? 'outline' : 'destructive'}>
                      {t(member.active ? 'member.active' : 'member.inactive')}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <EditMemberDialog member={member} isSelf={member.id === ownId} />
                      {/* One's own is "Đổi mật khẩu", which asks for the current one; the server refuses this. */}
                      {member.id !== ownId && <ResetPasswordDialog member={member} />}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TableCard>
    </PageContainer>
  )
}
