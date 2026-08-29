'use strict'

const fs = require('node:fs')
const path = require('node:path')
const { Readable } = require('node:stream')

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
}

function readBody(req, maxBytes = 2 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let size = 0
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > maxBytes) {
        reject(Object.assign(new Error('request body too large'), { statusCode: 413 }))
        req.destroy()
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => resolve(Buffer.concat(chunks)))
    req.on('error', reject)
  })
}

function json(res, status, value, headers) {
  const body = Buffer.from(JSON.stringify(value))
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': String(body.length),
    'cache-control': 'no-store',
    ...headers,
  })
  res.end(body)
}

function safeJoin(root, requestPath) {
  let decoded
  try { decoded = decodeURIComponent(requestPath) } catch (_) { return null }
  const relative = decoded.replace(/^\/+/, '')
  const file = path.resolve(root, relative || 'index.html')
  if (file !== root && !file.startsWith(root + path.sep)) return null
  return file
}

function serveStatic(res, root, requestPath) {
  let file = safeJoin(root, requestPath)
  if (!file) return false
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) file = path.join(root, 'index.html')
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) return false
  const stat = fs.statSync(file)
  res.writeHead(200, {
    'content-type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
    'content-length': String(stat.size),
    'cache-control': path.basename(file) === 'index.html' ? 'no-store' : 'public, max-age=3600',
    'x-content-type-options': 'nosniff',
  })
  fs.createReadStream(file).pipe(res)
  return true
}

async function writeFetchResponse(res, response, extraHeaders) {
  const headers = {}
  response.headers.forEach((value, key) => {
    if (!['content-length', 'content-encoding', 'transfer-encoding'].includes(key.toLowerCase())) headers[key] = value
  })
  res.writeHead(response.status, { ...headers, ...extraHeaders })
  if (!response.body) {
    res.end()
    return
  }
  Readable.fromWeb(response.body).pipe(res)
}

module.exports = { json, readBody, safeJoin, serveStatic, writeFetchResponse }

