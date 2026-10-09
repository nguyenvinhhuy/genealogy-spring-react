import { ChevronsUpDown, KeyRound, LogOut } from 'lucide-react'
import { lazy, Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { initialOf } from '@/shared/lib/initial'
import { endSession } from '@/shared/lib/session'
import { useAuthStore } from '@/shared/store/auth-store'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem, useSidebar } from '@/shared/ui/sidebar'

const ChangePasswordDialog = lazy(async () => ({
  default: (await import('@/features/member/components/change-password-dialog')).ChangePasswordDialog,
}))

/**
 * Draws the signed-in member at the foot of the sidebar, with their password change and sign-out.
 *
 * @returns the account menu
 */
export function NavUser() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { isMobile } = useSidebar()
  const member = useAuthStore((state) => state.member)
  const [passwordOpen, setPasswordOpen] = useState(false)

  if (!member) {
    return null
  }

  const onSignOut = async () => {
    await endSession()
    void navigate('/sign-in', { replace: true })
  }

  const identity = (
    <>
      <Avatar className="rounded-lg">
        <AvatarFallback className="rounded-lg bg-seal font-heading text-sm text-seal-foreground">
          {initialOf(member.fullName)}
        </AvatarFallback>
      </Avatar>
      <div className="grid flex-1 text-left text-sm leading-tight">
        <span className="truncate font-medium">{member.fullName}</span>
        <span className="text-muted-foreground truncate text-xs">{t(`role.${member.role}`)}</span>
      </div>
    </>
  )

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              tooltip={t('nav.accountMenu')}
              aria-label={t('nav.accountMenu')}
              className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              {identity}
              <ChevronsUpDown className="ml-auto size-4" aria-hidden />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg"
            side={isMobile ? 'bottom' : 'right'}
            align="end"
            sideOffset={4}
          >
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm text-foreground">
                {identity}
              </div>
              <p className="text-muted-foreground truncate px-1 pb-1 text-xs">{member.email}</p>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => setPasswordOpen(true)}>
              <KeyRound aria-hidden />
              {t('member.changePassword')}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => void onSignOut()}>
              <LogOut aria-hidden />
              {t('auth.signOut')}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        {/* Fetched on first use: the dialog brings zod, which every signed-in page load paid for otherwise (#37). */}
        {passwordOpen && (
          <Suspense fallback={null}>
            <ChangePasswordDialog open={passwordOpen} onOpenChange={setPasswordOpen} />
          </Suspense>
        )}
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
