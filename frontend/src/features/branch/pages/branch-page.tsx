import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { BranchDialog } from '@/features/branch/components/branch-dialog'
import { deleteBranch, fetchBranches, invalidateBranchQueries } from '@/features/branch/api'
import { ConfirmDeleteDialog } from '@/shared/components/confirm-delete-dialog'
import { PageContainer, PageHeader } from '@/shared/components/page-header'
import { TableCard, TableMessage } from '@/shared/components/table-card'
import { usePermissions } from '@/shared/hooks/use-permissions'
import { flattenHierarchy } from '@/shared/lib/hierarchy'
import { problemMessage } from '@/shared/lib/problem-detail'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

/**
 * Manages the clan's chi / phái / nhánh, shown as a tree indented by depth.
 *
 * @returns the page element
 */
export function BranchPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { mayEdit, mayDelete } = usePermissions()

  const { data: branches = [], isLoading, isError, error } = useQuery({
    queryKey: ['branches'],
    queryFn: fetchBranches,
  })
  const byId = new Map(branches.map((branch) => [branch.id, branch]))
  const rows = flattenHierarchy(branches)

  const removal = useMutation({
    mutationFn: ({ id, changeNote }: { id: number; changeNote: string }) => deleteBranch(id, changeNote),
    onSuccess: async () => {
      await invalidateBranchQueries(queryClient)
      toast.success(t('branch.deleted'))
    },
  })

  return (
    <PageContainer width="narrow">
      <PageHeader title={t('branch.title')} description={t('branch.subtitle')} actions={mayEdit && <BranchDialog />} />

      <TableCard>
        {isLoading && <TableMessage>{t('common.loading')}</TableMessage>}
        {/* A list that failed to load must not read as a clan with no chi. */}
        {isError && <TableMessage tone="error">{problemMessage(error, t('common.unexpectedError'))}</TableMessage>}
        {!isLoading && !isError && rows.length === 0 && <TableMessage>{t('branch.empty')}</TableMessage>}

        {rows.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('branch.name')}</TableHead>
                <TableHead>{t('branch.description')}</TableHead>
                {(mayEdit || mayDelete) && <TableHead className="w-0">{t('common.actions')}</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const branch = byId.get(row.id)
                if (!branch) {
                  return null
                }
                return (
                  <TableRow key={row.id}>
                    <TableCell className="font-medium" style={{ paddingLeft: `${row.depth * 20 + 16}px` }}>
                      {branch.name}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{branch.description || '—'}</TableCell>
                    {(mayEdit || mayDelete) && (
                      <TableCell>
                        <div className="flex items-center gap-1">
                          {mayEdit && <BranchDialog branch={branch} />}
                          {mayDelete && (
                            <ConfirmDeleteDialog
                              label={t('branch.delete')}
                              title={t('branch.deleteTitle', { name: branch.name })}
                              description={t('branch.deleteDescription')}
                              pending={removal.isPending}
                              onConfirm={(changeNote) => removal.mutateAsync({ id: branch.id, changeNote })}
                            />
                          )}
                        </div>
                      </TableCell>
                    )}
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </TableCard>
    </PageContainer>
  )
}
