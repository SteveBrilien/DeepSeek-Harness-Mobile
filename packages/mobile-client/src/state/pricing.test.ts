import { describe, expect, it } from 'vitest'
import {
  estimateUsageCost,
  formatEstimatedUsd,
  isOfficialDeepSeekTarget,
  isPeakTime,
  rateFor,
  usageProjectionOf,
} from './pricing'

const flash = { provider: 'deepseek-official', model: 'deepseek-v4-flash' }

describe('official DeepSeek pricing', () => {
  it('uses weekday peak windows in UTC', () => {
    expect(isPeakTime(Date.UTC(2026, 7, 31, 2))).toBe(true) // Monday 02:00 UTC
    expect(isPeakTime(Date.UTC(2026, 7, 31, 5))).toBe(false)
    expect(isPeakTime(Date.UTC(2026, 7, 30, 2))).toBe(false) // Sunday never peaks
    expect(rateFor(flash, Date.UTC(2026, 7, 31, 2))).toMatchObject({ period: 'peak', cacheMissPerMillion: 0.44 })
    expect(rateFor(flash, Date.UTC(2026, 7, 30, 2))).toMatchObject({ period: 'off-peak', cacheMissPerMillion: 0.22 })
  })

  it('does not apply official prices to a third-party provider with the same model name', () => {
    const thirdParty = { provider: 'mhsapi', model: 'deepseek-v4-flash' }
    expect(isOfficialDeepSeekTarget(thirdParty)).toBe(false)
    expect(rateFor(thirdParty, Date.now())).toBeNull()
  })

  it('converts the per-million rate to a real currency amount', () => {
    const bucket = { uncachedInputTokens: 1000, outputTokens: 500, cacheReadTokens: 2000, cacheWriteTokens: 0 }
    const cost = estimateUsageCost(bucket, flash, Date.UTC(2026, 7, 30, 12))
    expect(cost).toBeCloseTo((1000 * 0.22 + 2000 * 0.007 + 500 * 0.66) / 1_000_000, 12)
    expect(cost).toBeLessThan(0.001)
  })
})

describe('DSH tokenUsage projection', () => {
  it('parses totals and the authoritative last interaction buckets', () => {
    const projection = usageProjectionOf({
      totals: { uncachedInputTokens: 8242, outputTokens: 75, cacheReadTokens: 0, cacheWriteTokens: 0 },
      last: { turn: 1, step: 1, buckets: { uncachedInputTokens: 8242, outputTokens: 75, cacheReadTokens: 0, cacheWriteTokens: 0 } },
    })
    expect(projection?.last?.turn).toBe(1)
    expect(projection?.totals.outputTokens).toBe(75)
  })

  it('rejects the incorrect flat shape used by the v1.1 prototype', () => {
    expect(usageProjectionOf({ uncachedInputTokens: 1, outputTokens: 2, cacheReadTokens: 3, cacheWriteTokens: 4 })).toBeNull()
  })

  it('accepts a session with totals but no completed interaction', () => {
    expect(usageProjectionOf({ totals: { uncachedInputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0 }, last: null })?.last).toBeNull()
  })
})

describe('formatEstimatedUsd', () => {
  it('keeps small costs visible instead of rounding them to zero', () => {
    expect(formatEstimatedUsd(0.000564)).toBe('$0.000564')
    expect(formatEstimatedUsd(0.0465)).toBe('$0.0465')
    expect(formatEstimatedUsd(null)).toBe('—')
  })
})
