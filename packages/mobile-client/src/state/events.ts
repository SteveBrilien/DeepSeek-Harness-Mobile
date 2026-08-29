import type { ConversationItem } from '../types/view'

type UnknownRecord = Record<string, unknown>

function record(value: unknown): UnknownRecord {
  return value && typeof value === 'object' ? value as UnknownRecord : {}
}

function blocksText(content: unknown): string {
  if (typeof content === 'string') return content
  if (!Array.isArray(content)) return ''
  return content.map((block) => {
    const item = record(block)
    if (item.type === 'text' && typeof item.text === 'string') return item.text
    if (item.type === 'image') return '[图片]'
    if (item.type === 'tool-result') return blocksText(item.content)
    return ''
  }).filter(Boolean).join('\n')
}

function contentText(message: unknown): string { return blocksText(record(message).content) }

function toolResultCallId(message: unknown): string | undefined {
  const content = record(message).content
  if (!Array.isArray(content)) return undefined
  const block = content.map(record).find((item) => item.type === 'tool-result')
  return typeof block?.toolCallId === 'string' ? block.toolCallId : undefined
}

function compact(value: unknown, max = 360): string {
  let text: string
  try { text = typeof value === 'string' ? value : JSON.stringify(value) }
  catch { text = String(value) }
  text = text.replace(/\s+/g, ' ').trim()
  return text.length > max ? text.slice(0, max) + '…' : text
}

export function appendEvent(items: ConversationItem[], raw: unknown): ConversationItem[] {
  const event = record(raw)
  const data = record(event.data)
  const eventType = typeof event.type === 'string' ? event.type : ''
  const seq = typeof event.seq === 'number' ? event.seq : undefined
  const key = `${seq ?? 'live'}:${eventType}:${items.length}`

  switch (eventType) {
    case 'user/message': {
      const text = contentText(data.message)
      return text ? [...items, { key, kind: 'user', text, seq }] : items
    }
    case 'assistant/message': {
      const text = contentText(data.message)
      if (!text) return items
      const last = items.at(-1)
      if (last?.kind === 'assistant' && last.pending) {
        return [...items.slice(0, -1), { ...last, text, pending: false, seq }]
      }
      return [...items, { key, kind: 'assistant', text, seq }]
    }
    case 'assistant/chunk': {
      const chunk = record(data.chunk)
      const text = typeof chunk.text === 'string' ? chunk.text : ''
      if (!text) return items
      const kind = chunk.type === 'reasoning-delta' ? 'reasoning' : 'assistant'
      const last = items.at(-1)
      if (last?.kind === kind && last.pending) {
        return [...items.slice(0, -1), { ...last, text: last.text + text }]
      }
      return [...items, { key, kind, text, pending: true, seq }]
    }
    case 'tool/call': {
      const title = typeof data.name === 'string' ? data.name : '工具调用'
      const details = data.arguments === undefined ? '' : compact(data.arguments)
      const callId = typeof data.callId === 'string' ? data.callId : undefined
      return [...items, { key, kind: 'tool', title, text: details, pending: true, seq, callId }]
    }
    case 'tool/result': {
      const text = contentText(data.message) || compact(data.result || data.error || '已完成')
      const callId = toolResultCallId(data.message)
      const actual = callId ? items.findIndex((item) => item.kind === 'tool' && item.callId === callId) : items.length - 1 - [...items].reverse().findIndex((item) => item.kind === 'tool' && item.pending)
      if (actual >= 0 && actual < items.length) {
        return items.map((item, i) => i === actual ? { ...item, text, pending: false } : item)
      }
      return [...items, { key, kind: 'tool', title: '工具结果', text, seq, callId }]
    }
    case 'turn/end':
      return items.map((item) => item.pending ? { ...item, pending: false } : item)
    default:
      return items
  }
}

export function foldEvents(events: unknown[]): ConversationItem[] {
  let items: ConversationItem[] = []
  for (const entry of events) items = appendEvent(items, record(entry).event || entry)
  return items
}

export function projectionTitle(projections: unknown): string | undefined {
  const values = record(record(projections).values)
  const candidate = values['session-title'] ?? values.title
  if (typeof candidate === 'string') return candidate
  const title = record(candidate).title
  return typeof title === 'string' ? title : undefined
}
