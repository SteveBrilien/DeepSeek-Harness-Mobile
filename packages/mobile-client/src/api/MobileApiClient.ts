import { AbstractApiClient } from '@deepseek-ai/dsh-host-apiproxy/client'

const APP_PATH = '/dsh-mobile-v1'

export function normalizeEndpoint(value: string): string {
  const parsed = new URL(value.trim())
  if (parsed.protocol !== 'https:' && !isLoopback(parsed.hostname)) {
    throw new Error('远程服务必须使用 HTTPS')
  }
  parsed.hash = ''
  parsed.search = ''
  const pathname = parsed.pathname.replace(/\/+$/, '')
  parsed.pathname = pathname.endsWith(APP_PATH) ? pathname : `${pathname}${APP_PATH}`
  return parsed.toString().replace(/\/$/, '')
}

function isLoopback(hostname: string): boolean {
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]'
}

export function defaultEndpoint(): string {
  if (location.origin === 'https://appassets.androidplatform.net') return ''
  return normalizeEndpoint(location.origin + APP_PATH)
}

export class MobileApiClient extends AbstractApiClient {
  readonly endpoint: string
  readonly token: string

  constructor(endpoint: string, token: string) {
    super(30_000)
    this.endpoint = normalizeEndpoint(endpoint)
    this.token = token
  }

  protected override doFetch(input: URL, init: RequestInit = {}): Promise<Response> {
    const sourcePath = input.pathname
    let targetPath: string
    if (sourcePath === '/api/respond') targetPath = '/api/respond'
    else if (sourcePath === '/api/events.mux') targetPath = '/api/events/mux'
    else if (sourcePath === '/api/events.host') targetPath = '/api/events/host'
    else if (sourcePath.startsWith('/api/')) targetPath = '/api/rpc/' + sourcePath.slice('/api/'.length)
    else throw new Error(`不支持的 ApiProxy 路径：${sourcePath}`)

    const headers = new Headers(init.headers)
    headers.set('Authorization', `Bearer ${this.token}`)
    return fetch(this.endpoint + targetPath, { ...init, headers })
  }
}

interface PairResult {
  ok: boolean
  token?: string
  error?: string
}

export async function health(endpoint: string, signal?: AbortSignal): Promise<void> {
  const response = await fetch(normalizeEndpoint(endpoint) + '/api/health', { signal })
  if (!response.ok) throw new Error(`网关不可用（HTTP ${response.status}）`)
  const body = await response.json() as { ok?: boolean; contractVersion?: number }
  if (!body.ok || body.contractVersion !== 1) throw new Error('移动端网关协议版本不兼容')
}

export async function pair(endpoint: string, code: string, deviceName: string): Promise<string> {
  const target = normalizeEndpoint(endpoint)
  await health(target)
  const response = await fetch(target + '/api/pair', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ code, deviceName }),
  })
  const result = await response.json() as PairResult
  if (!response.ok || !result.ok || !result.token) {
    throw new Error(result.error === 'invalid-pairing-code' ? '配对码无效或已过期' : (result.error || '配对失败'))
  }
  return result.token
}
