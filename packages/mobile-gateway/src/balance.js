'use strict'

// Server-only DeepSeek balance adapter. The credential value never enters an
// ApiProxy envelope, WebView response, log entry, error message, or cache file.

const { createHash } = require('node:crypto')

const BALANCE_URL = 'https://api.deepseek.com/user/balance'
const CACHE_TTL_MS = 30 * 1000
const TIMEOUT_MS = 10 * 1000

function credentialValue(credential) {
  if (typeof credential === 'string') return credential.trim() || undefined
  if (credential && typeof credential === 'object' && typeof credential.value === 'string') {
    return credential.value.trim() || undefined
  }
  return undefined
}

function amount(value) {
  if (typeof value !== 'string' && typeof value !== 'number') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null
}

function parseBalanceResponse(body) {
  try {
    const value = JSON.parse(String(body || ''))
    if (typeof value.is_available !== 'boolean' || !Array.isArray(value.balance_infos) || value.balance_infos.length === 0) {
      return { ok: false, error: 'invalid-response' }
    }
    const balances = value.balance_infos.map((item) => {
      if (!item || typeof item !== 'object' || typeof item.currency !== 'string' || !item.currency.trim()) return null
      const total = amount(item.total_balance)
      const granted = amount(item.granted_balance)
      const toppedUp = amount(item.topped_up_balance)
      if (total === null || granted === null || toppedUp === null) return null
      return { currency: item.currency.trim().toUpperCase(), total, granted, toppedUp }
    })
    if (balances.some((item) => item === null)) return { ok: false, error: 'invalid-response' }
    return { ok: true, available: value.is_available, balances }
  } catch (_) {
    return { ok: false, error: 'invalid-response' }
  }
}

function classifyBalanceError(status, cause) {
  if (status === 401 || status === 403) return 'upstream-auth'
  if (status === 429) return 'upstream-rate-limited'
  if (status >= 500) return 'upstream-server-error'
  if (status >= 400) return 'upstream-request-rejected'
  if (cause && (cause.name === 'AbortError' || /timeout|aborted/i.test(String(cause.message || cause)))) return 'timeout'
  return 'upstream-unreachable'
}

function keyFingerprint(key) {
  return createHash('sha256').update(key).digest('hex')
}

function createBalanceService(options = {}) {
  const resolveKey = options.resolveKey || (async () => undefined)
  const fetchFn = options.fetchFn || ((url, init) => globalThis.fetch(url, init))
  const now = options.now || (() => Date.now())
  const timeoutMs = options.timeoutMs || TIMEOUT_MS
  let cache = null

  async function fetchBalance(force = false) {
    const current = now()
    let key
    try { key = credentialValue(await resolveKey()) } catch (_) { key = undefined }
    if (!key) return { ok: false, error: 'not-configured', fetchedAt: current }

    const fingerprint = keyFingerprint(key)
    if (!force && cache && cache.fingerprint === fingerprint && current - cache.response.fetchedAt < CACHE_TTL_MS) {
      return cache.response
    }

    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    try {
      const response = await fetchFn(BALANCE_URL, {
        method: 'GET',
        headers: { authorization: `Bearer ${key}`, accept: 'application/json' },
        signal: controller.signal,
      })
      if (!response.ok) return { ok: false, error: classifyBalanceError(response.status), fetchedAt: now() }
      const parsed = parseBalanceResponse(await response.text())
      const result = { ...parsed, fetchedAt: now() }
      if (parsed.ok) cache = { fingerprint, response: result }
      return result
    } catch (error) {
      return { ok: false, error: classifyBalanceError(0, error), fetchedAt: now() }
    } finally {
      clearTimeout(timer)
    }
  }

  return { fetchBalance }
}

module.exports = {
  BALANCE_URL,
  CACHE_TTL_MS,
  TIMEOUT_MS,
  credentialValue,
  parseBalanceResponse,
  classifyBalanceError,
  createBalanceService,
}
