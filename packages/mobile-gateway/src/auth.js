'use strict'

const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')

const PAIRING_TTL_MS = 10 * 60 * 1000
const PAIRING_ATTEMPT_LIMIT = 8

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex')
}

function safeEqual(a, b) {
  const left = Buffer.from(String(a))
  const right = Buffer.from(String(b))
  return left.length === right.length && crypto.timingSafeEqual(left, right)
}

function atomicWrite(file, value) {
  const temporary = file + '.tmp'
  fs.writeFileSync(temporary, JSON.stringify(value, null, 2), { mode: 0o600 })
  fs.renameSync(temporary, file)
  try { fs.chmodSync(file, 0o600) } catch (_) {}
}

class DeviceRegistry {
  constructor(options) {
    this.dataDir = options.dataDir
    this.file = path.join(this.dataDir, 'devices.json')
    this.now = options.now || (() => Date.now())
    this.logPairingCode = options.logPairingCode || (() => {})
    fs.mkdirSync(this.dataDir, { recursive: true, mode: 0o700 })
    this.state = this.load()
    this.rotatePairingCode(options.initialPairingCode)
  }

  load() {
    try {
      const value = JSON.parse(fs.readFileSync(this.file, 'utf8'))
      if (value && value.version === 1 && Array.isArray(value.devices)) return value
    } catch (_) {}
    return { version: 1, devices: [] }
  }

  save() {
    atomicWrite(this.file, this.state)
  }

  rotatePairingCode(preferred) {
    const candidate = String(preferred || '').trim()
    const code = /^\d{6,12}$/.test(candidate)
      ? candidate
      : String(crypto.randomInt(0, 1_000_000)).padStart(6, '0')
    this.pairing = {
      codeHash: sha256(code),
      expiresAt: this.now() + PAIRING_TTL_MS,
    }
    this.logPairingCode(code, this.pairing.expiresAt)
  }

  pair(code, deviceName) {
    if (!this.pairing || this.now() > this.pairing.expiresAt) {
      this.rotatePairingCode()
      return { ok: false, reason: 'pairing-code-expired' }
    }
    if (!safeEqual(sha256(String(code || '').trim()), this.pairing.codeHash)) {
      return { ok: false, reason: 'pairing-code-invalid' }
    }

    const token = crypto.randomBytes(32).toString('base64url')
    const id = crypto.randomUUID()
    const createdAt = new Date(this.now()).toISOString()
    this.state.devices.push({
      id,
      name: String(deviceName || 'Android device').trim().slice(0, 80) || 'Android device',
      tokenHash: sha256(token),
      createdAt,
      lastSeenAt: createdAt,
      revokedAt: null,
    })
    this.save()
    this.rotatePairingCode()
    return { ok: true, deviceId: id, token, createdAt }
  }

  authenticate(header) {
    const match = /^Bearer\s+([A-Za-z0-9_-]{32,})$/i.exec(String(header || '').trim())
    if (!match) return null
    const tokenHash = sha256(match[1])
    const device = this.state.devices.find((entry) => entry.tokenHash === tokenHash && !entry.revokedAt)
    if (!device) return null
    device.lastSeenAt = new Date(this.now()).toISOString()
    return { id: device.id, name: device.name }
  }
}

class PairingRateLimiter {
  constructor(options = {}) {
    this.limit = options.limit || PAIRING_ATTEMPT_LIMIT
    this.windowMs = options.windowMs || PAIRING_TTL_MS
    this.now = options.now || (() => Date.now())
    this.buckets = new Map()
  }

  consume(key) {
    const id = String(key || 'unknown')
    const now = this.now()
    let bucket = this.buckets.get(id)
    if (!bucket || now - bucket.startedAt >= this.windowMs) {
      bucket = { startedAt: now, attempts: 0 }
      this.buckets.set(id, bucket)
    }
    if (bucket.attempts >= this.limit) return false
    bucket.attempts += 1
    return true
  }

  reset(key) {
    this.buckets.delete(String(key || 'unknown'))
  }
}

module.exports = { DeviceRegistry, PairingRateLimiter, PAIRING_TTL_MS, PAIRING_ATTEMPT_LIMIT, sha256 }
