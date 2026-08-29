import { describe, expect, it } from 'vitest'
import { normalizeEndpoint } from './MobileApiClient'

describe('normalizeEndpoint', () => {
  it('adds the app path exactly once to an origin', () => {
    expect(normalizeEndpoint('http://127.0.0.1:4318')).toBe('http://127.0.0.1:4318/dsh-mobile-v1')
    expect(normalizeEndpoint('http://127.0.0.1:4318/')).toBe('http://127.0.0.1:4318/dsh-mobile-v1')
  })

  it('keeps an existing app path stable', () => {
    expect(normalizeEndpoint('https://dsh.example.com/dsh-mobile-v1')).toBe('https://dsh.example.com/dsh-mobile-v1')
  })

  it('rejects remote cleartext endpoints', () => {
    expect(() => normalizeEndpoint('http://dsh.example.com')).toThrow('HTTPS')
  })
})
