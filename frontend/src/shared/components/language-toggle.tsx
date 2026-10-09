import { Languages } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { IconTooltip } from '@/shared/components/icon-tooltip'
import { LANGUAGES, rememberLanguage, type Language } from '@/shared/i18n'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'

/**
 * Lets the viewer pick the interface language, remembered on this browser.
 *
 * @returns the language menu
 */
export function LanguageToggle() {
  const { t, i18n } = useTranslation()
  const label = t('language.label')

  return (
    <DropdownMenu>
      <IconTooltip label={label}>
        {/* A span between them: the tooltip's own data-state overwrote the menu trigger's open state (§6.3). */}
        <span className="inline-flex">
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label={label}>
              <Languages aria-hidden />
            </Button>
          </DropdownMenuTrigger>
        </span>
      </IconTooltip>
      <DropdownMenuContent align="end" className="w-40">
        <DropdownMenuRadioGroup
          value={i18n.resolvedLanguage}
          onValueChange={(next) => {
            const language = next as Language
            rememberLanguage(language)
            void i18n.changeLanguage(language)
          }}
        >
          {LANGUAGES.map((language) => (
            <DropdownMenuRadioItem key={language} value={language}>
              {t(`language.${language}`)}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
