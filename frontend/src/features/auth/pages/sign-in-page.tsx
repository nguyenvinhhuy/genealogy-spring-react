import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'

import { login } from '@/features/auth/api'
import { LineageArt } from '@/features/auth/components/lineage-art'
import { buildSignInSchema, type SignInValues } from '@/features/auth/schema'
import { LanguageToggle } from '@/shared/components/language-toggle'
import { Logo } from '@/shared/components/logo'
import { ModeToggle } from '@/shared/components/mode-toggle'
import type { SignInState } from '@/shared/components/require-auth'
import { voidSubmit } from '@/shared/lib/forms'
import { problemMessage } from '@/shared/lib/problem-detail'
import { cn } from '@/shared/lib/utils'
import { useAuthStore } from '@/shared/store/auth-store'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

/**
 * Renders the sign-in page.
 *
 * @returns the page element
 */
export function SignInPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const setSession = useAuthStore((state) => state.setSession)
  const signedIn = useAuthStore((state) => state.accessToken !== null)
  // Only a path inside the app: a crafted "//elsewhere" in the state must not become an open redirect.
  const requested = (location.state as SignInState | null)?.from
  const destination = requested?.startsWith('/') && !requested.startsWith('//') ? requested : '/'

  // Schemas declared at module scope cannot call t(), so the factory runs here (CLAUDE.md 6).
  const schema = useMemo(() => buildSignInSchema(t), [t])

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SignInValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = async (values: SignInValues) => {
    try {
      const result = await login(values)
      setSession(result.accessToken, result.member)
      toast.success(t('auth.signedIn'))
      void navigate(destination, { replace: true })
    } catch (error) {
      toast.error(problemMessage(error, t('auth.signInFailed')))
    }
  }

  if (signedIn) {
    return <Navigate to={destination} replace />
  }

  return (
    <main className="bg-muted/60 relative flex min-h-svh flex-col items-center justify-center p-6 md:p-10">
      <div className="absolute top-4 right-4 flex items-center gap-1">
        <LanguageToggle />
        <ModeToggle />
      </div>

      {/* The split card of the template's third sign-in page: the form on one half, the picture on the other. */}
      <div className="w-full max-w-sm md:max-w-4xl">
        <Card className="overflow-hidden p-0 shadow-lg">
          <CardContent className="grid p-0 md:grid-cols-2">
            <form onSubmit={voidSubmit(handleSubmit(onSubmit))} className="flex flex-col gap-6 p-6 md:p-10">
              <div className="flex flex-col items-center gap-3 text-center">
                <Logo className="size-12" />
                <div className="space-y-1">
                  <h1 className="text-2xl font-semibold">{t('auth.welcome')}</h1>
                  <p className="text-muted-foreground text-sm text-balance">{t('auth.welcomeHint')}</p>
                </div>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="email">{t('auth.email')}</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="username"
                  placeholder={t('auth.emailPlaceholder')}
                  aria-invalid={errors.email !== undefined}
                  aria-describedby={errors.email ? 'email-error' : undefined}
                  {...register('email')}
                />
                {errors.email && (
                  <p id="email-error" className="text-destructive text-sm">
                    {errors.email.message}
                  </p>
                )}
              </div>

              <div className="grid gap-2">
                <Label htmlFor="password">{t('auth.password')}</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  aria-invalid={errors.password !== undefined}
                  aria-describedby={errors.password ? 'password-error' : undefined}
                  {...register('password')}
                />
                {errors.password && (
                  <p id="password-error" className="text-destructive text-sm">
                    {errors.password.message}
                  </p>
                )}
              </div>

              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? t('auth.submitting') : t('auth.signIn')}
              </Button>

              {/* There is no self-service reset: the trưởng tộc resets a forgotten password (§8.2). */}
              <p className="text-muted-foreground text-center text-xs">{t('auth.forgotHint')}</p>
            </form>

            <div
              className={cn(
                'bg-seal text-seal-foreground relative hidden p-10',
                'flex-col items-center justify-center gap-8 md:flex',
              )}
            >
              <LineageArt />
              <figure className="space-y-2 text-center">
                <blockquote className="font-heading text-2xl font-medium">{t('auth.proverb')}</blockquote>
                <figcaption className="text-seal-foreground/80 text-sm">{t('auth.proverbNote')}</figcaption>
              </figure>
            </div>
          </CardContent>
        </Card>
        <p className="text-muted-foreground mt-6 text-center text-xs">{t('auth.privateNote')}</p>
      </div>
    </main>
  )
}
