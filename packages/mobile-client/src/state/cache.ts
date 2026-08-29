import type { SessionSummary } from '@deepseek-ai/dsh-host-apiproxy/api/sessions'
import { clearNativeCache, readNativeCache, writeNativeCache } from '../api/native'

const CACHE_VERSION = 1
const MAX_SESSIONS = 30
const MAX_EVENTS_PER_SESSION = 300

export interface CacheSnapshot {
  version: number
  endpoint: string
  selectedSessionId: string | null
  sessions: SessionSummary[]
  events: Record<string, unknown[]>
  updatedAt: number
}

export function readCache(endpoint: string): CacheSnapshot | null {
  try {
    const snapshot = JSON.parse(readNativeCache() || '') as CacheSnapshot
    if (snapshot.version !== CACHE_VERSION || snapshot.endpoint !== endpoint || !Array.isArray(snapshot.sessions)) return null
    return snapshot
  } catch {
    return null
  }
}

export function writeCache(snapshot: Omit<CacheSnapshot, 'version' | 'updatedAt'>): void {
  const sessions = [...snapshot.sessions]
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, MAX_SESSIONS)
  const allowed = new Set(sessions.map((session) => String(session.sessionId)))
  const events = Object.fromEntries(Object.entries(snapshot.events)
    .filter(([sessionId]) => allowed.has(sessionId))
    .map(([sessionId, rows]) => [sessionId, rows.slice(-MAX_EVENTS_PER_SESSION)]))
  writeNativeCache(JSON.stringify({
    ...snapshot,
    sessions,
    events,
    version: CACHE_VERSION,
    updatedAt: Date.now(),
  } satisfies CacheSnapshot))
}

export function purgeCache(): void {
  clearNativeCache()
}
