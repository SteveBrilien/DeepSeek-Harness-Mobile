'use strict'

const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')
const { DeviceRegistry, PairingRateLimiter, PAIRING_TTL_MS } = require('../src/auth.js')

test('pairing issues a token once and stores only its digest', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dsh-mobile-auth-'))
  let now = 1_800_000_000_000
  const codes = []
  const registry = new DeviceRegistry({
    dataDir: dir,
    now: () => now,
    initialPairingCode: '123456',
    logPairingCode: (code) => codes.push(code),
  })
  const paired = registry.pair('123456', 'Pixel test')
  assert.equal(paired.ok, true)
  assert.ok(paired.token.length >= 32)
  assert.deepEqual(registry.authenticate('Bearer ' + paired.token), { id: paired.deviceId, name: 'Pixel test' })
  const raw = fs.readFileSync(path.join(dir, 'devices.json'), 'utf8')
  assert.equal(raw.includes(paired.token), false)
  assert.equal(registry.pair('123456', 'replay').ok, false)
  assert.equal(codes.length, 2, 'a fresh code is emitted after successful pairing')

  now += PAIRING_TTL_MS + 1
  assert.equal(registry.pair(codes.at(-1), 'expired').reason, 'pairing-code-expired')
})

test('pairing limiter bounds guesses per address and resets after its window', () => {
  let now = 1000
  const limiter = new PairingRateLimiter({ limit: 2, windowMs: 5000, now: () => now })
  assert.equal(limiter.consume('phone'), true)
  assert.equal(limiter.consume('phone'), true)
  assert.equal(limiter.consume('phone'), false)
  assert.equal(limiter.consume('other'), true)
  now += 5000
  assert.equal(limiter.consume('phone'), true)
  limiter.reset('phone')
  assert.equal(limiter.consume('phone'), true)
})
