'use strict'

const assert = require('node:assert/strict')
const test = require('node:test')
const { ALLOWED_METHODS } = require('../src/carrier.js')

test('mobile method allowlist contains the vertical slice and excludes credentials', () => {
  for (const method of ['session.list', 'session.create', 'session.history', 'session.prompt', 'session.cancel', 'session.models', 'session.selectModel']) {
    assert.equal(ALLOWED_METHODS.has(method), true, method)
  }
  assert.equal(ALLOWED_METHODS.has('credentials.set'), false)
  assert.equal(ALLOWED_METHODS.has('settings.replace'), false)
})
