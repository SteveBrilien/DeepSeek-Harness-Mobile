import { describe, expect, it, vi } from 'vitest'
import { selectAndConfirmModel, unwrapModelResponse } from './ModelSheet'

describe('model switching', () => {
  it('does not treat an HTTP-level RPC error as success', () => {
    expect(() => unwrapModelResponse({ result: { ok: false as const, error: { message: 'route rejected' } } })).toThrow('route rejected')
  })

  it('confirms the server-side current selection after mutation', async () => {
    const selection = { provider: 'deepseek', model: 'deepseek-v4-flash', reasoningEffort: 'high' }
    const api = { sessions: {
      selectModel: vi.fn().mockResolvedValue({ result: { ok: true, value: { selected: selection } } }),
      models: vi.fn().mockResolvedValue({ result: { ok: true, value: { current: selection, routable: true, groups: [], failures: [] } } }),
    } }
    const result = await selectAndConfirmModel(api as never, 'session-1' as never, selection)
    expect(result.selected).toEqual(selection)
    expect(api.sessions.models).toHaveBeenCalledOnce()
  })

  it('fails closed when the server still reports the old model', async () => {
    const api = { sessions: {
      selectModel: vi.fn().mockResolvedValue({ result: { ok: true, value: { selected: { provider: 'deepseek', model: 'flash' } } } }),
      models: vi.fn().mockResolvedValue({ result: { ok: true, value: { current: { provider: 'deepseek', model: 'pro' }, routable: true, groups: [], failures: [] } } }),
    } }
    await expect(selectAndConfirmModel(api as never, 'session-1' as never, { provider: 'deepseek', model: 'flash' })).rejects.toThrow('服务器没有确认')
  })
})
