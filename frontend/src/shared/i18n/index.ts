import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import en from './locales/en.json'
import vi from './locales/vi.json'

// The languages the interface ships in; `vi` is the default and the fallback (CLAUDE.md §6).
export const LANGUAGES = ['vi', 'en'] as const

/** One language the interface ships in. */
export type Language = (typeof LANGUAGES)[number]

const STORAGE_KEY = 'genealogy.language'

/**
 * Reads the language this browser last chose, when there is one.
 *
 * @returns the remembered language, or `vi`
 */
function rememberedLanguage(): Language {
  // Storage can throw in a private window or with site data blocked; the default is always safe.
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return LANGUAGES.find((language) => language === stored) ?? 'vi'
  } catch {
    return 'vi'
  }
}

/**
 * Remembers the language chosen on this browser.
 *
 * @param language the chosen language
 */
export function rememberLanguage(language: Language): void {
  try {
    localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // A preference that cannot be stored is simply not remembered.
  }
}

void i18n.use(initReactI18next).init({
  resources: {
    vi: { translation: vi },
    en: { translation: en },
  },
  lng: rememberedLanguage(),
  fallbackLng: 'vi',
  interpolation: { escapeValue: false },
})

// Screen readers and hyphenation follow the page's language, so it moves with the interface.
document.documentElement.lang = i18n.language
i18n.on('languageChanged', (language) => {
  document.documentElement.lang = language
})

export default i18n
