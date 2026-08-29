// DeepSeek 官方模型的费用估算。价格单位为 USD / 1M tokens。
// 来源：https://api-docs.deepseek.com/quick_start/pricing/
// 快照：2026-08-29。费率会变化，更新时必须同步测试与文档。

export interface TokenBucket {
  uncachedInputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheWriteTokens: number
}

export interface TokenUsageProjection {
  totals: TokenBucket
  last: null | {
    turn: number
    step: number
    buckets: TokenBucket
  }
}

export interface PricingTarget {
  provider: string
  model: string
}

export interface TokenRate {
  currency: 'USD'
  cacheHitPerMillion: number
  cacheMissPerMillion: number
  outputPerMillion: number
  period: 'peak' | 'off-peak'
}

interface ModelRatePair {
  peak: Omit<TokenRate, 'period'>
  offPeak: Omit<TokenRate, 'period'>
}

export const PRICING_SOURCE = 'https://api-docs.deepseek.com/quick_start/pricing/'
export const PRICING_SNAPSHOT_DATE = '2026-08-29'
export const OFFICIAL_PROVIDER = 'deepseek-official'

const RATES: Record<string, ModelRatePair> = {
  'deepseek-v4-flash': {
    offPeak: { currency: 'USD', cacheHitPerMillion: 0.007, cacheMissPerMillion: 0.22, outputPerMillion: 0.66 },
    peak: { currency: 'USD', cacheHitPerMillion: 0.014, cacheMissPerMillion: 0.44, outputPerMillion: 1.32 },
  },
  'deepseek-v4-pro': {
    offPeak: { currency: 'USD', cacheHitPerMillion: 0.022, cacheMissPerMillion: 0.66, outputPerMillion: 1.98 },
    peak: { currency: 'USD', cacheHitPerMillion: 0.044, cacheMissPerMillion: 1.32, outputPerMillion: 3.96 },
  },
  'deepseek-v4-flash-vision-exp': {
    offPeak: { currency: 'USD', cacheHitPerMillion: 0.007, cacheMissPerMillion: 0.22, outputPerMillion: 0.66 },
    peak: { currency: 'USD', cacheHitPerMillion: 0.014, cacheMissPerMillion: 0.44, outputPerMillion: 1.32 },
  },
}

function recordOf(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null
}

function nonNegativeNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null
}

export function usageBucketOf(value: unknown): TokenBucket | null {
  const row = recordOf(value)
  if (!row) return null
  const uncachedInputTokens = nonNegativeNumber(row.uncachedInputTokens)
  const outputTokens = nonNegativeNumber(row.outputTokens)
  const cacheReadTokens = nonNegativeNumber(row.cacheReadTokens)
  const cacheWriteTokens = nonNegativeNumber(row.cacheWriteTokens)
  if (uncachedInputTokens === null || outputTokens === null || cacheReadTokens === null || cacheWriteTokens === null) return null
  return { uncachedInputTokens, outputTokens, cacheReadTokens, cacheWriteTokens }
}

/** Parse the real rc.2 projection shape: { totals, last: { turn, step, buckets } | null }. */
export function usageProjectionOf(value: unknown): TokenUsageProjection | null {
  const row = recordOf(value)
  if (!row) return null
  const totals = usageBucketOf(row.totals)
  if (!totals) return null
  if (row.last === null || row.last === undefined) return { totals, last: null }
  const last = recordOf(row.last)
  if (!last) return null
  const turn = nonNegativeNumber(last.turn)
  const step = nonNegativeNumber(last.step)
  const buckets = usageBucketOf(last.buckets)
  if (turn === null || step === null || !buckets) return null
  return { totals, last: { turn, step, buckets } }
}

export function isOfficialDeepSeekTarget(target: PricingTarget | null | undefined): boolean {
  return Boolean(target && target.provider === OFFICIAL_PROVIDER && Object.prototype.hasOwnProperty.call(RATES, target.model))
}

/** Peak: 01:00-04:00 and 06:00-10:00 UTC, Monday-Friday. */
export function isPeakTime(timestamp: number): boolean {
  const date = new Date(timestamp)
  const day = date.getUTCDay()
  if (day === 0 || day === 6) return false
  const hour = date.getUTCHours()
  return (hour >= 1 && hour < 4) || (hour >= 6 && hour < 10)
}

export function rateFor(target: PricingTarget, timestamp: number): TokenRate | null {
  if (!isOfficialDeepSeekTarget(target)) return null
  const period = isPeakTime(timestamp) ? 'peak' : 'off-peak'
  return { ...RATES[target.model][period], period }
}

export function estimateUsageCost(bucket: TokenBucket, target: PricingTarget, timestamp: number): number | null {
  const rate = rateFor(target, timestamp)
  if (!rate) return null
  // DSH 的 token buckets 互斥。DeepSeek 没有单列 cache-write 价格；写入发生在
  // cache miss 输入上，因此以 miss 费率保守计入，官方 adapter 正常情况下该桶为 0。
  const miss = bucket.uncachedInputTokens + bucket.cacheWriteTokens
  return (
    miss * rate.cacheMissPerMillion
    + bucket.cacheReadTokens * rate.cacheHitPerMillion
    + bucket.outputTokens * rate.outputPerMillion
  ) / 1_000_000
}

export function formatEstimatedUsd(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '—'
  if (value >= 1) return `$${value.toFixed(2)}`
  if (value >= 0.01) return `$${value.toFixed(4)}`
  return `$${value.toFixed(6)}`
}

export function formatTokenCount(value: number): string {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(value)
}
