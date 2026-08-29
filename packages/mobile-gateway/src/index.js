'use strict'

const path = require('node:path')
const os = require('node:os')
const { DeviceRegistry, PairingRateLimiter } = require('./auth.js')
const { createBalanceService } = require('./balance.js')
const { MobileCarrier } = require('./carrier.js')
const { json, readBody, serveStatic } = require('./http.js')

const NAME = 'dsh-mobile-v1-gateway'
const CONTRACT_VERSION = 1
const APP_VERSION = '1.0.0-alpha.2'
const APP_PATH = '/dsh-mobile-v1'
const API_PATH = APP_PATH + '/api'
const PUBLIC_DIR = path.resolve(__dirname, '..', 'public')

function configuredOrigins() {
  const values = new Set(['https://appassets.androidplatform.net'])
  const configured = String(process.env.DSH_MOBILE_PUBLIC_ORIGIN || '').trim()
  if (configured) {
    try { values.add(new URL(configured).origin) } catch (_) {}
  }
  if (process.env.NODE_ENV !== 'production') {
    values.add('http://127.0.0.1:4173')
    values.add('http://localhost:4173')
  }
  return values
}

function corsHeaders(req, origins) {
  const origin = String(req.headers.origin || '')
  if (!origin || !origins.has(origin)) return { vary: 'Origin' }
  return {
    'access-control-allow-origin': origin,
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'access-control-allow-headers': 'Authorization, Content-Type',
    'access-control-max-age': '600',
    vary: 'Origin',
  }
}

function dataDirectory() {
  if (process.env.DSH_MOBILE_DATA_DIR) return path.resolve(process.env.DSH_MOBILE_DATA_DIR)
  const home = process.env.DSH_HOME || path.join(os.homedir(), '.dsh')
  return path.join(home, 'mobile-v1')
}

function requestAddress(req) {
  if (process.env.DSH_MOBILE_TRUST_PROXY === '1') {
    const forwarded = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim()
    if (forwarded) return forwarded
  }
  return req.socket && req.socket.remoteAddress ? req.socket.remoteAddress : 'unknown'
}

function apply(ctx) {
  const origins = configuredOrigins()
  const registry = new DeviceRegistry({
    dataDir: dataDirectory(),
    initialPairingCode: process.env.DSH_MOBILE_PAIRING_CODE,
    logPairingCode(code, expiresAt) {
      console.warn(`[dsh-mobile-v1] pairing code ${code} expires ${new Date(expiresAt).toISOString()}`)
    },
  })
  const pairingLimiter = new PairingRateLimiter()
  const carrier = new MobileCarrier(ctx.apiProxy)
  const balance = createBalanceService({
    resolveKey: async () => {
      const credentials = ctx.get('credentials')
      if (credentials && typeof credentials.resolve === 'function') {
        const resolved = await credentials.resolve('DEEPSEEK_API_KEY')
        if (resolved) return resolved
      }
      return process.env.DEEPSEEK_API_KEY
    },
  })

  const apiHandler = async (req, res) => {
    const url = new URL(req.url || '/', 'http://dsh.local')
    const headers = corsHeaders(req, origins)
    if (req.method === 'OPTIONS') {
      if (req.headers.origin && !origins.has(String(req.headers.origin))) {
        res.writeHead(403, headers); res.end(); return
      }
      res.writeHead(204, headers); res.end(); return
    }

    if (url.pathname === API_PATH + '/health' && req.method === 'GET') {
      json(res, 200, { ok: true, appVersion: APP_VERSION, contractVersion: CONTRACT_VERSION }, headers)
      return
    }

    if (url.pathname === API_PATH + '/pair' && req.method === 'POST') {
      if (req.headers.origin && !origins.has(String(req.headers.origin))) {
        json(res, 403, { ok: false, error: 'origin-not-allowed' }, headers); return
      }
      const address = requestAddress(req)
      if (!pairingLimiter.consume(address)) {
        json(res, 429, { ok: false, error: 'pairing-rate-limited' }, { ...headers, 'retry-after': '600' })
        return
      }
      const body = JSON.parse((await readBody(req, 64 * 1024)).toString('utf8') || '{}')
      const result = registry.pair(body.code, body.deviceName)
      if (result.ok) pairingLimiter.reset(address)
      json(res, result.ok ? 200 : 401, result, headers)
      return
    }

    const device = registry.authenticate(req.headers.authorization)
    if (!device) {
      json(res, 401, { ok: false, error: 'device-auth-required' }, headers)
      return
    }
    if (req.headers.origin && !origins.has(String(req.headers.origin))) {
      json(res, 403, { ok: false, error: 'origin-not-allowed' }, headers)
      return
    }

    if (url.pathname.startsWith(API_PATH + '/rpc/') && req.method === 'POST') {
      const method = decodeURIComponent(url.pathname.slice((API_PATH + '/rpc/').length))
      await carrier.rpc(req, res, method, headers)
      return
    }
    if (url.pathname === API_PATH + '/respond' && req.method === 'POST') {
      await carrier.respond(req, res, headers)
      return
    }
    if (url.pathname === API_PATH + '/events/mux' && req.method === 'GET') {
      await carrier.events(req, res, 'mux', headers)
      return
    }
    if (url.pathname === API_PATH + '/events/host' && req.method === 'GET') {
      await carrier.events(req, res, 'host', headers)
      return
    }
    if (url.pathname === API_PATH + '/balance' && req.method === 'GET') {
      json(res, 200, await balance.fetchBalance(url.searchParams.get('force') === '1'), headers)
      return
    }
    json(res, 404, { ok: false, error: 'not-found' }, headers)
  }

  const staticHandler = (req, res) => {
    const url = new URL(req.url || '/', 'http://dsh.local')
    const relative = url.pathname === APP_PATH || url.pathname === APP_PATH + '/'
      ? 'index.html'
      : url.pathname.slice((APP_PATH + '/').length)
    if (!serveStatic(res, PUBLIC_DIR, relative)) json(res, 503, { ok: false, error: 'mobile-client-not-built' })
  }

  ctx.effect(() => ctx.webServer.register({ kind: 'prefix', path: API_PATH, handler: (req, res) => {
    apiHandler(req, res).catch((error) => {
      if (!res.headersSent) json(res, error.statusCode || 500, { ok: false, error: String(error.message || error) })
      else res.destroy(error)
    })
  } }), NAME + ': api')

  ctx.effect(() => ctx.webServer.register({ kind: 'prefix', path: APP_PATH, handler: staticHandler }), NAME + ': app')
}

module.exports = {
  name: NAME,
  inject: ['webServer', 'apiProxy'],
  apply,
  __test: { API_PATH, APP_PATH, APP_VERSION, CONTRACT_VERSION, configuredOrigins, requestAddress },
}
