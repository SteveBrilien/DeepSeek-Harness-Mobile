export interface SavedConnection {
  endpoint: string
  token: string
}

interface NativeBridge {
  getEndpoint(): string
  getToken(): string
  saveConnection(endpoint: string, token: string): void
  clearConnection(): void
  getDeviceName(): string
  readCache(): string
  writeCache(payload: string): void
  clearCache(): void
  setSystemBars?(light: boolean): void
}

declare global {
  interface Window {
    DSHNative?: NativeBridge
    DSHMobileBack?: () => boolean
  }
}

const ENDPOINT_KEY = 'dsh-mobile.endpoint'
const TOKEN_KEY = 'dsh-mobile.dev-token'

export function loadConnection(): SavedConnection | null {
  const endpoint = window.DSHNative?.getEndpoint() || localStorage.getItem(ENDPOINT_KEY) || ''
  const token = window.DSHNative?.getToken() || localStorage.getItem(TOKEN_KEY) || ''
  return endpoint && token ? { endpoint, token } : null
}

export function saveConnection(connection: SavedConnection): void {
  if (window.DSHNative) {
    window.DSHNative.saveConnection(connection.endpoint, connection.token)
    return
  }
  localStorage.setItem(ENDPOINT_KEY, connection.endpoint)
  localStorage.setItem(TOKEN_KEY, connection.token)
}

export function clearConnection(): void {
  if (window.DSHNative) window.DSHNative.clearConnection()
  else {
    localStorage.removeItem(ENDPOINT_KEY)
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function deviceName(): string {
  return window.DSHNative?.getDeviceName() || `Web ${navigator.platform || 'Browser'}`
}

export function readNativeCache(): string | null {
  return window.DSHNative?.readCache() || localStorage.getItem('dsh-mobile.dev-cache')
}

export function writeNativeCache(payload: string): void {
  if (window.DSHNative) window.DSHNative.writeCache(payload)
  else localStorage.setItem('dsh-mobile.dev-cache', payload)
}

export function clearNativeCache(): void {
  if (window.DSHNative) window.DSHNative.clearCache()
  else localStorage.removeItem('dsh-mobile.dev-cache')
}
