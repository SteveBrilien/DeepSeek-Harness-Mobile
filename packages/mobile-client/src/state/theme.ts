export type AppTheme = 'light' | 'tokyo-night' | 'dark'

const STORAGE_KEY = 'dsh-mobile-theme-v1'

export function loadTheme(): AppTheme {
  const value = localStorage.getItem(STORAGE_KEY)
  return value === 'light' || value === 'tokyo-night' || value === 'dark' ? value : 'dark'
}

export function applyTheme(theme: AppTheme): void {
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme === 'light' ? 'light' : 'dark'
  localStorage.setItem(STORAGE_KEY, theme)
  window.DSHNative?.setSystemBars?.(theme === 'light')
}
