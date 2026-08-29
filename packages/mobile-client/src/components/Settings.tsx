import { useEffect, useMemo, useState } from 'react'
import type { AgentPresetEntry } from '@deepseek-ai/dsh-host-apiproxy/api/agent-presets'
import type { SessionModels, SessionSummary } from '@deepseek-ai/dsh-host-apiproxy/api/sessions'
import type { SkillEntry } from '@deepseek-ai/dsh-host-apiproxy/api/skills'
import type { MobileApiClient } from '../api/MobileApiClient'
import type { AppTheme } from '../state/theme'
import { ModelSheet } from './ModelSheet'
import { Icon, Sheet } from './ui'

type ConnectionState = 'cache' | 'syncing' | 'online' | 'offline'
type Panel = 'model' | 'plugins' | 'preset' | 'usage' | 'market' | 'archive' | null

function unwrap<T>(response: { result: { ok: true; value: T } | { ok: false; error: { message: string } } }): T {
  if (!response.result.ok) throw new Error(response.result.error.message)
  return response.result.value
}

function errorText(error: unknown): string { return error instanceof Error ? error.message : String(error) }
function titleOf(session: SessionSummary): string {
  const values = session.projections?.values as Record<string, unknown> | undefined
  const raw = values?.title ?? values?.['session-title']
  const title = typeof raw === 'string' ? raw : raw && typeof raw === 'object' ? (raw as { title?: unknown }).title : undefined
  return typeof title === 'string' ? title : (session.blank ? '新会话' : `会话 ${String(session.sessionId).slice(0, 8)}`)
}

export function SettingsScreen({ api, endpoint, status, theme, selected, sessions, archivedSessionIds, onTheme, onBack, onClearCache, onDisconnect, onRefreshSessions, onOpenSession, onNotice }: {
  api: MobileApiClient
  endpoint: string
  status: ConnectionState
  theme: AppTheme
  selected: SessionSummary | null
  sessions: SessionSummary[]
  archivedSessionIds: string[]
  onTheme: (theme: AppTheme) => void
  onBack: () => void
  onClearCache: () => void
  onDisconnect: () => void
  onRefreshSessions: () => Promise<void>
  onOpenSession: (sessionId: string) => void
  onNotice: (message: string) => void
}) {
  const [panel, setPanel] = useState<Panel>(null)
  const [models, setModels] = useState<SessionModels | null>(null)
  const [presets, setPresets] = useState<readonly AgentPresetEntry[] | null>(null)
  const [skills, setSkills] = useState<readonly SkillEntry[] | null>(null)
  const [busyPreset, setBusyPreset] = useState('')

  useEffect(() => {
    let cancelled = false
    if (selected) api.sessions.models({ sessionId: selected.sessionId }).then(unwrap).then((value) => { if (!cancelled) setModels(value) }).catch(() => {})
    api.agentPresets.list({}).then(unwrap).then((value) => { if (!cancelled) setPresets(value.presets) }).catch(() => { if (!cancelled) setPresets([]) })
    return () => { cancelled = true }
  }, [api, selected])

  useEffect(() => {
    if (panel !== 'plugins' || !selected || skills) return
    let cancelled = false
    api.skills.list({ sessionId: selected.sessionId }).then(unwrap).then((value) => { if (!cancelled) setSkills(value.skills) }).catch((error) => { if (!cancelled) { setSkills([]); onNotice(errorText(error)) } })
    return () => { cancelled = true }
  }, [api, onNotice, panel, selected, skills])

  const archived = useMemo(() => {
    const ids = new Set(archivedSessionIds)
    return sessions.filter((session) => ids.has(String(session.sessionId)))
  }, [archivedSessionIds, sessions])

  const choosePreset = async (preset: AgentPresetEntry) => {
    if (!selected || !selected.blank || preset.broken || busyPreset) return
    setBusyPreset(preset.id)
    try {
      unwrap(await api.agentPresets.select({ sessionId: selected.sessionId, agentPreset: preset.id }))
      await onRefreshSessions()
      onNotice(`当前新会话已切换为 ${preset.name || preset.id}`)
    } catch (error) { onNotice(errorText(error)) }
    finally { setBusyPreset('') }
  }

  return <div className="settings-page"><header className="top-bar"><button className="icon-button" aria-label="返回" onClick={onBack}><Icon name="back" /></button><h1>设置</h1><span className="icon-spacer" /></header><main className="settings-content">
    <section><div className="settings-section-title">通用</div><div className="settings-card"><div className="settings-theme-row"><span><strong>主题</strong><small>仅控制移动端外观，立即生效</small></span><div className="theme-segment">{([{ id: 'light', label: '浅色' }, { id: 'tokyo-night', label: 'Tokyo Night' }, { id: 'dark', label: '深色' }] as const).map((item) => <button key={item.id} className={theme === item.id ? 'is-active' : ''} onClick={() => onTheme(item.id)}>{item.label}</button>)}</div></div></div></section>
    <section><div className="settings-section-title">DeepSeek Harness</div><div className="settings-card settings-native-sections">
      <SettingsLink icon="model" title="模型" detail={models ? `${models.current.provider} / ${models.current.model}` : selected ? '读取服务器模型目录…' : '请先创建或选择会话'} onClick={() => setPanel('model')} />
      <SettingsLink icon="plugin" title="插件" detail={skills ? `${skills.length} 项当前会话公开能力` : '查看当前会话由插件公开的 Skills'} onClick={() => setPanel('plugins')} />
      <SettingsLink icon="route" title="Agent 预设" detail={selected?.agentPreset || presets?.find((item) => item.isDefault)?.name || '服务器默认预设'} onClick={() => setPanel('preset')} />
      <SettingsLink icon="chart" title="API 用量" detail="等待 DSH 服务器提供权威计量接口" onClick={() => setPanel('usage')} />
      <SettingsLink icon="store" title="插件市场" detail="等待 DSH 服务器提供安全的市场协议" onClick={() => setPanel('market')} />
      <SettingsLink icon="archive" title="归档对话" detail={`${archivedSessionIds.length} 条服务器归档`} onClick={() => setPanel('archive')} />
    </div></section>
    <section><div className="settings-section-title">连接</div><div className="settings-card"><div className="settings-row"><span className="settings-row-icon"><Icon name="server" /></span><span><strong>DSH 服务器</strong><small>{endpoint}</small></span><ConnectionBadge status={status} /></div><div className="settings-row"><span className="settings-row-icon"><Icon name="layers" /></span><span><strong>协议基线</strong><small>DSH 0.1.1-rc.2 · Mobile Contract v1</small></span></div></div></section>
    <section><div className="settings-section-title">本地数据</div><div className="settings-card"><button className="settings-row interactive" onClick={onClearCache}><span className="settings-row-icon"><Icon name="database" /></span><span><strong>清除本地镜像缓存</strong><small>不会删除服务器上的会话、上下文和模型设置</small></span><Icon name="chevron" /></button></div></section>
    <section><div className="settings-section-title">设备</div><div className="settings-card"><button className="settings-row interactive danger-text" onClick={onDisconnect}><span className="settings-row-icon"><Icon name="logout" /></span><span><strong>解除此设备的连接</strong><small>需要新的配对码才能重新进入</small></span></button></div></section><div className="settings-footnote">DeepSeek Harness Mobile v1.0.0-alpha.2</div>

    {panel === 'model' && selected && <ModelSheet api={api} session={selected} onClose={() => setPanel(null)} onError={onNotice} onSelected={(selection, next) => { setModels(next); onNotice(`已切换为 ${selection.provider} / ${selection.model}${selection.reasoningEffort ? ` · ${selection.reasoningEffort}` : ''}`) }}/>} 
    {panel === 'model' && !selected && <CapabilitySheet title="模型" icon="model" onClose={() => setPanel(null)} text="请先创建或选择一条 DSH 会话。模型目录和当前选择都属于会话，移动端不会猜测全局默认值。" />}
    {panel === 'plugins' && <Sheet eyebrow="DSH 当前会话能力" title="插件" onClose={() => setPanel(null)}>{!selected ? <div className="sheet-empty">请先选择会话。</div> : skills === null ? <div className="sheet-loading"><span className="spinner"/>读取插件公开能力…</div> : <><div className="sheet-option-list read-only-list">{skills.map((skill) => <div className="sheet-readonly-row" key={skill.name}><Icon name="plugin"/><span><strong>/{skill.name}</strong><small>{skill.description}{skill.whenToUse ? ` · ${skill.whenToUse}` : ''}</small></span><b>{skill.modelInvocable ? 'Agent' : '仅用户'}</b></div>)}</div>{!skills.length && <div className="sheet-empty">当前会话没有公开可调用的 Skill。</div>}<p className="sheet-note">rc.2 没有面向远程客户端的插件清单 API；这里严格展示 <code>skill.list</code>，不读取 Agent Preset 组成文件，也不枚举 Cordis 注册表。</p></>}</Sheet>}
    {panel === 'preset' && <Sheet eyebrow="DSH Agent 组合" title="Agent 预设" onClose={() => setPanel(null)}><div className="sheet-option-list">{presets?.map((preset) => { const active = selected?.agentPreset === preset.id || (!selected?.agentPreset && preset.isDefault); const disabled = !selected?.blank || Boolean(preset.broken); return <button key={preset.id} className={active ? 'is-selected' : ''} disabled={disabled || Boolean(busyPreset)} onClick={() => void choosePreset(preset)}><Icon name="route"/><span><strong>{preset.name || preset.id}</strong><small>{preset.description || preset.id}{preset.broken ? ` · ${preset.broken}` : ''}</small></span>{busyPreset === preset.id ? <span className="spinner small"/> : active ? <Icon name="check"/> : <b>{preset.trust === 'system' ? '内置' : '用户'}</b>}</button> })}</div>{presets === null && <div className="sheet-loading"><span className="spinner"/>读取服务器预设…</div>}{presets?.length === 0 && <div className="sheet-empty">当前部署没有 Agent 预设。</div>}<p className="sheet-note">DSH 只允许空白会话切换预设；已开始的会话会锁定原工具组合，避免历史工具调用与新组合不一致。</p></Sheet>}
    {panel === 'usage' && <CapabilitySheet title="API 用量" icon="chart" onClose={() => setPanel(null)} text="DSH 0.1.1-rc.2 的 ApiProxy 没有供应商用量或费用接口。本页不会把上下文 token 当成账单，也不会在手机端直连供应商账户；待服务器发布权威计量 API 后接入。" />}
    {panel === 'market' && <CapabilitySheet title="插件市场" icon="store" onClose={() => setPanel(null)} text="DSH 0.1.1-rc.2 没有远程插件市场协议。移动端不会扫描服务器目录或允许远程安装任意插件；后续需要带签名、权限审查和服务器确认的市场 API。" />}
    {panel === 'archive' && <Sheet eyebrow="服务器持久化" title="归档对话" onClose={() => setPanel(null)}><div className="sheet-option-list">{archived.map((session) => <button key={String(session.sessionId)} onClick={() => { setPanel(null); onOpenSession(String(session.sessionId)) }}><Icon name="archive"/><span><strong>{titleOf(session)}</strong><small>{session.cwd || '默认工作区'} · {new Date(session.updatedAt).toLocaleString('zh-CN')}</small></span><Icon name="chevron"/></button>)}</div>{!archived.length && <div className="sheet-empty">服务器当前没有可显示的归档对话。</div>}<p className="sheet-note">归档集合来自 <code>workspace.list</code>；rc.2 目前只发布 archiveSession、没有 unarchive RPC，因此本页可查看归档会话但不伪造“取消归档”。</p></Sheet>}
  </main></div>
}

function SettingsLink({ icon, title, detail, onClick }: { icon: string; title: string; detail: string; onClick: () => void }) {
  return <button className="settings-row interactive" onClick={onClick}><span className="settings-row-icon"><Icon name={icon}/></span><span><strong>{title}</strong><small>{detail}</small></span><Icon name="chevron"/></button>
}

function CapabilitySheet({ title, icon, text, onClose }: { title: string; icon: string; text: string; onClose: () => void }) {
  return <Sheet eyebrow="服务器能力状态" title={title} onClose={onClose}><div className="capability-status"><span><Icon name={icon}/></span><strong>当前协议暂未提供</strong><p>{text}</p></div></Sheet>
}

function ConnectionBadge({ status }: { status: ConnectionState }) {
  const label = { cache: '缓存', syncing: '同步中', online: '已连接', offline: '离线' }[status]
  return <span className={`connection-badge ${status}`}><i />{label}</span>
}
