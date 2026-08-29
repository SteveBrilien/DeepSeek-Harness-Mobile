import { describe, expect, it } from 'vitest'
import { foldEvents, projectionTitle } from './events'

describe('foldEvents', () => {
  it('correlates parallel tool results by call id', () => {
    const items = foldEvents([
      { type: 'tool/call', seq: 1, data: { callId: 'a', name: 'read', arguments: '{"path":"a"}' } },
      { type: 'tool/call', seq: 2, data: { callId: 'b', name: 'read', arguments: '{"path":"b"}' } },
      { type: 'tool/result', seq: 3, data: { message: { content: [{ type: 'tool-result', toolCallId: 'a', content: [{ type: 'text', text: 'A done' }] }] } } },
    ])
    expect(items[0]).toMatchObject({ callId: 'a', text: 'A done', pending: false })
    expect(items[1]).toMatchObject({ callId: 'b', pending: true })
  })

  it('folds streaming reasoning and markdown text', () => {
    const items = foldEvents([
      { type: 'assistant/chunk', seq: 1, data: { chunk: { type: 'reasoning-delta', text: 'thinking' } } },
      { type: 'assistant/chunk', seq: 2, data: { chunk: { type: 'text-delta', text: '```js\nconst x = 1\n```' } } },
      { type: 'turn/end', seq: 3, data: {} },
    ])
    expect(items.map((item) => item.kind)).toEqual(['reasoning', 'assistant'])
    expect(items.every((item) => !item.pending)).toBe(true)
  })
})

describe('projectionTitle', () => {
  it('uses the rc.2 title projection key', () => {
    expect(projectionTitle({ values: { title: { title: 'Native title' } } })).toBe('Native title')
  })
})
