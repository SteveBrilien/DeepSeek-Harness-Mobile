'use strict'

const { createRequire } = require('node:module')
const { realpathSync } = require('node:fs')
const { pathToFileURL } = require('node:url')
const { readBody, writeFetchResponse } = require('./http.js')

const ALLOWED_METHODS = new Set([
  'host.describe',
  'workspace.list',
  'session.list',
  'session.create',
  'session.history',
  'session.fork',
  'session.rename',
  'session.prompt',
  'session.attachment',
  'session.updateQueue',
  'session.cancel',
  'session.search',
  'session.models',
  'session.selectModel',
  'command.list',
  'command.execute',
  'agentPreset.list',
  'agentPreset.select',
  'skill.list',
])

async function loadToFetchHandler() {
  const anchors = []
  if (process.argv[1]) {
    try { anchors.push(realpathSync(process.argv[1])) } catch (_) { anchors.push(process.argv[1]) }
  }
  anchors.push(__filename)
  let lastError
  for (const anchor of anchors) {
    try {
      const req = createRequire(anchor)
      const url = pathToFileURL(req.resolve('@deepseek-ai/dsh-host-apiproxy')).href
      const module = await import(url)
      if (typeof module.toFetchHandler !== 'function') throw new Error('toFetchHandler export missing')
      return module.toFetchHandler
    } catch (error) {
      lastError = error
    }
  }
  throw new Error('cannot resolve DSH ApiProxy carrier: ' + String(lastError && lastError.message || lastError))
}

class MobileCarrier {
  constructor(apiProxy, options = {}) {
    this.apiProxy = apiProxy
    this.allowedMethods = options.allowedMethods || ALLOWED_METHODS
    this.fetchHandlerPromise = options.fetchHandler
      ? Promise.resolve(options.fetchHandler)
      : loadToFetchHandler().then((factory) => factory(apiProxy))
  }

  async rpc(req, res, method, responseHeaders) {
    if (!this.allowedMethods.has(method)) {
      res.writeHead(403, responseHeaders)
      res.end('method not exposed to mobile client')
      return
    }
    if (!String(req.headers['content-type'] || '').toLowerCase().startsWith('application/json')) {
      res.writeHead(415, responseHeaders)
      res.end('application/json required')
      return
    }
    const body = await readBody(req)
    let envelope
    try { envelope = JSON.parse(body.toString('utf8')) } catch (_) {
      res.writeHead(400, responseHeaders)
      res.end('invalid json')
      return
    }
    if (!envelope || envelope.type !== 'client-request' || envelope.method !== method) {
      res.writeHead(400, responseHeaders)
      res.end('rpc envelope method mismatch')
      return
    }
    const handler = await this.fetchHandlerPromise
    const upstream = await handler.fetch(new Request('http://dsh.local/api/' + encodeURIComponent(method), {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body,
    }))
    await writeFetchResponse(res, upstream, responseHeaders)
  }

  async respond(req, res, responseHeaders) {
    const body = await readBody(req)
    const handler = await this.fetchHandlerPromise
    const upstream = await handler.fetch(new Request('http://dsh.local/api/respond', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body,
    }))
    await writeFetchResponse(res, upstream, responseHeaders)
  }

  async events(req, res, stream, responseHeaders) {
    const controller = new AbortController()
    req.once('close', () => controller.abort())
    const handler = await this.fetchHandlerPromise
    const upstream = await handler.fetch(new Request('http://dsh.local/api/events.' + stream, {
      method: 'GET',
      signal: controller.signal,
    }))
    await writeFetchResponse(res, upstream, responseHeaders)
  }
}

module.exports = { ALLOWED_METHODS, MobileCarrier, loadToFetchHandler }
