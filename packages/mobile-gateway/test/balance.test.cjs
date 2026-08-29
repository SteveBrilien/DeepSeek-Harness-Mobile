'use strict'

const assert = require('node:assert/strict')
const test = require('node:test')
const { BALANCE_URL, credentialValue, parseBalanceResponse, classifyBalanceError, createBalanceService } = require('../src/balance.js')

function fakeFetch(status, body, error) {
  const calls = []
  return {
    calls,
    fn: async (url, init) => {
      calls.push({ url, init })
      if (error) throw error
      return { ok: status >= 200 && status < 300, status, text: async () => body }
    },
  }
}

test('parseBalanceResponse preserves currencies and availability', () => {
  const result = parseBalanceResponse(JSON.stringify({
    is_available: true,
    balance_infos: [
      { currency: 'CNY', total_balance: '4.07', granted_balance: '0', topped_up_balance: '4.07' },
      { currency: 'USD', total_balance: '1.25', granted_balance: '0.25', topped_up_balance: '1.00' },
    ],
  }))
  assert.deepEqual(result, {
    ok: true,
    available: true,
    balances: [
      { currency: 'CNY', total: 4.07, granted: 0, toppedUp: 4.07 },
      { currency: 'USD', total: 1.25, granted: 0.25, toppedUp: 1 },
    ],
  })
})

test('parseBalanceResponse rejects malformed or incomplete bodies', () => {
  assert.equal(parseBalanceResponse('not json').error, 'invalid-response')
  assert.equal(parseBalanceResponse('{}').error, 'invalid-response')
  assert.equal(parseBalanceResponse(JSON.stringify({ is_available: true, balance_infos: [] })).error, 'invalid-response')
  assert.equal(parseBalanceResponse(JSON.stringify({ is_available: true, balance_infos: [{ currency: 'CNY', total_balance: 'x' }] })).error, 'invalid-response')
})

test('classifyBalanceError keeps failures diagnosable without upstream bodies', () => {
  assert.equal(classifyBalanceError(401), 'upstream-auth')
  assert.equal(classifyBalanceError(429), 'upstream-rate-limited')
  assert.equal(classifyBalanceError(503), 'upstream-server-error')
  assert.equal(classifyBalanceError(400), 'upstream-request-rejected')
  assert.equal(classifyBalanceError(0, Object.assign(new Error('aborted'), { name: 'AbortError' })), 'timeout')
  assert.equal(classifyBalanceError(0, new Error('socket hang up')), 'upstream-unreachable')
})

test('balance service caches success for the same credential and force refreshes', async () => {
  const fetch = fakeFetch(200, JSON.stringify({ is_available: true, balance_infos: [{ currency: 'CNY', total_balance: '1', granted_balance: '0', topped_up_balance: '1' }] }))
  let now = 1000
  const service = createBalanceService({ resolveKey: async () => ({ value: 'sk-test' }), fetchFn: fetch.fn, now: () => now })
  assert.equal((await service.fetchBalance()).ok, true)
  now += 10_000
  assert.equal((await service.fetchBalance()).ok, true)
  assert.equal(fetch.calls.length, 1)
  await service.fetchBalance(true)
  assert.equal(fetch.calls.length, 2)
  assert.equal(fetch.calls[0].url, BALANCE_URL)
  assert.equal(fetch.calls[0].init.headers.authorization, 'Bearer sk-test')
})

test('a credential change invalidates the success cache', async () => {
  const fetch = fakeFetch(200, JSON.stringify({ is_available: true, balance_infos: [{ currency: 'USD', total_balance: '2', granted_balance: '1', topped_up_balance: '1' }] }))
  let key = 'sk-one'
  const service = createBalanceService({ resolveKey: async () => key, fetchFn: fetch.fn, now: () => 1000 })
  await service.fetchBalance()
  key = 'sk-two'
  await service.fetchBalance()
  assert.equal(fetch.calls.length, 2)
})

test('failure states are returned and are not cached', async () => {
  const none = createBalanceService({ resolveKey: async () => null, fetchFn: fakeFetch(200, '{}').fn, now: () => 0 })
  assert.equal((await none.fetchBalance()).error, 'not-configured')

  const fetch = fakeFetch(401, '')
  const unauthorized = createBalanceService({ resolveKey: async () => 'sk', fetchFn: fetch.fn, now: () => 0 })
  assert.equal((await unauthorized.fetchBalance()).error, 'upstream-auth')
  await unauthorized.fetchBalance()
  assert.equal(fetch.calls.length, 2)
})

test('timeout is classified and credentialValue rejects blanks', async () => {
  const timeout = Object.assign(new Error('aborted'), { name: 'AbortError' })
  const service = createBalanceService({ resolveKey: async () => 'sk', fetchFn: fakeFetch(0, '', timeout).fn, now: () => 0 })
  assert.equal((await service.fetchBalance()).error, 'timeout')
  assert.equal(credentialValue({ value: '  sk-object  ' }), 'sk-object')
  assert.equal(credentialValue('   '), undefined)
  assert.equal(credentialValue(null), undefined)
})
