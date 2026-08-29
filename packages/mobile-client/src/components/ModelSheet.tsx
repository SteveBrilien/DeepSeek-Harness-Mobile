import { useEffect, useMemo, useState } from 'react'
import type { ModelCatalogModel, ModelSelection, SessionModels, SessionSummary } from '@deepseek-ai/dsh-host-apiproxy/api/sessions'
import type { MobileApiClient } from '../api/MobileApiClient'
import { Icon, Sheet } from './ui'

export function unwrapModelResponse<T>(response: { result: { ok: true; value: T } | { ok: false; error: { message: string } } }): T {
  if (!response.result.ok) throw new Error(response.result.error.message)
  return response.result.value
}

export async function selectAndConfirmModel(api: MobileApiClient, sessionId: SessionSummary['sessionId'], selection: ModelSelection): Promise<{ selected: ModelSelection; models: SessionModels }> {
  const value = unwrapModelResponse(await api.sessions.selectModel({ sessionId, ...selection }))
  const models = unwrapModelResponse(await api.sessions.models({ sessionId }))
  if (models.current.provider !== value.selected.provider || models.current.model !== value.selected.model || models.current.reasoningEffort !== value.selected.reasoningEffort) {
    throw new Error('服务器没有确认新的模型选择，请检查 DSH provider 配置')
  }
  return { selected: value.selected, models }
}

function providerGlyph(name: string): string {
  const words = name.replace(/[^a-z0-9]+/gi, ' ').trim().split(/\s+/)
  return (words.length > 1 ? words.slice(0, 2).map((word) => word[0]).join('') : name.slice(0, 2)).toUpperCase()
}

export function ModelSheet({ api, session, onClose, onSelected, onError }: { api: MobileApiClient; session: SessionSummary; onClose: () => void; onSelected: (selection: ModelSelection, models: SessionModels) => void; onError: (message: string) => void }) {
  const [catalog, setCatalog] = useState<SessionModels | null>(null)
  const [candidate, setCandidate] = useState<{ provider: string; model: ModelCatalogModel } | null>(null)
  const [effort, setEffort] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    api.sessions.models({ sessionId: session.sessionId }).then(unwrapModelResponse).then((value) => {
      if (!cancelled) setCatalog(value)
    }).catch((error) => { if (!cancelled) onError(error instanceof Error ? error.message : String(error)) })
    return () => { cancelled = true }
  }, [api, onError, session.sessionId])

  const selectedModel = useMemo(() => candidate?.model || catalog?.groups.find((group) => group.id === catalog.current.provider)?.models.find((model) => model.id === catalog.current.model), [candidate, catalog])
  const selectedProvider = candidate?.provider || catalog?.current.provider
  const effectiveEffort = effort ?? (candidate ? candidate.model.reasoning?.defaultEffort : catalog?.current.reasoningEffort ?? selectedModel?.reasoning?.defaultEffort)

  const choose = (provider: string, model: ModelCatalogModel) => {
    setCandidate({ provider, model })
    setEffort(model.reasoning?.defaultEffort)
  }

  const apply = async () => {
    if (!catalog || !selectedProvider || !selectedModel || busy) return
    setBusy(true)
    try {
      const result = await selectAndConfirmModel(api, session.sessionId, {
        provider: selectedProvider,
        model: selectedModel.id,
        ...(effectiveEffort === undefined ? {} : { reasoningEffort: effectiveEffort }),
      })
      onSelected(result.selected, result.models)
      onClose()
    } catch (error) { onError(error instanceof Error ? error.message : String(error)) }
    finally { setBusy(false) }
  }

  return <Sheet eyebrow="会话模型" title="模型与推理等级" onClose={onClose}>
    {!catalog ? <div className="sheet-loading"><span className="spinner"/>正在读取服务器模型目录…</div> : <>
      <div className="model-current"><Icon name="model"/><span><small>当前生效</small><strong>{catalog.current.provider} / {catalog.current.model}</strong></span>{catalog.routable ? <i className="route-ok">可路由</i> : <i className="route-error">不可路由</i>}</div>
      <div className="model-catalog">
        {catalog.groups.map((group) => <section key={group.id} className="model-provider"><header><span className="provider-glyph">{providerGlyph(group.name)}</span><span><strong>{group.name}</strong><small>{group.id}</small></span></header><div className="model-list">
          {group.models.map((model) => { const active = selectedProvider === group.id && selectedModel?.id === model.id; return <button key={model.id} className={active ? 'is-selected' : ''} onClick={() => choose(group.id, model)}><span><strong>{model.name}</strong>{model.description && <small>{model.description}</small>}</span>{active && <Icon name="check"/>}</button> })}
        </div></section>)}
        {!catalog.groups.length && <div className="sheet-empty">服务器没有返回可选模型。请在服务器端 DSH 中配置 provider。</div>}
      </div>
      {selectedModel?.reasoning?.efforts?.length ? <label className="reasoning-picker"><span><strong>推理等级</strong><small>由当前模型适配器提供</small></span><select value={effectiveEffort || ''} onChange={(event) => setEffort(event.target.value || undefined)}>{selectedModel.reasoning.efforts.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label> : <div className="reasoning-picker is-static"><span><strong>推理等级</strong><small>当前模型没有公开可切换的推理等级</small></span><b>默认</b></div>}
      {!!catalog.failures.length && <details className="provider-failures"><summary>{catalog.failures.length} 个供应商目录读取失败</summary>{catalog.failures.map((failure) => <p key={failure.id}><strong>{failure.name}</strong>：{failure.message}</p>)}</details>}
      <button className="primary-button" disabled={busy || !selectedModel} onClick={() => void apply()}>{busy ? <span className="spinner"/> : <Icon name="check"/>}{busy ? '正在确认…' : '应用到当前会话'}</button>
    </>}
  </Sheet>
}
