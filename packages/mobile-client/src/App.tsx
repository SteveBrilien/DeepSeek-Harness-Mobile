import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { HostFrame, MuxFrame } from '@deepseek-ai/dsh-host-apiproxy/api/events'
import type { RpcId, RpcResponse } from '@deepseek-ai/dsh-host-apiproxy/api/rpc'
import type { SessionSummary } from '@deepseek-ai/dsh-host-apiproxy/api/sessions'
import type { ModelSelection, SessionModels } from '@deepseek-ai/dsh-host-apiproxy/api/sessions'
import type { WorkspaceView } from '@deepseek-ai/dsh-host-apiproxy/api/workspace'
import { MobileApiClient, defaultEndpoint, normalizeEndpoint, pair } from './api/MobileApiClient'
import { clearConnection, deviceName, loadConnection, saveConnection, type SavedConnection } from './api/native'
import { Composer as DSHComposer } from './components/Composer'
import { Markdown } from './components/Markdown'
import { SettingsScreen } from './components/Settings'
import { applyTheme, loadTheme, type AppTheme } from './state/theme'
import { purgeCache, readCache, writeCache } from './state/cache'
import { appendEvent, foldEvents, projectionTitle } from './state/events'
import type { ConversationItem } from './types/view'

type ConnectionState = 'cache' | 'syncing' | 'online' | 'offline'
type MainScreen = 'conversation' | 'settings'
type ApprovalPending = Extract<MuxFrame, { type: 'approval/requested' }> & { rpcId: RpcId }
type QuestionPending = Extract<MuxFrame, { type: 'question/requested' }> & { rpcId: RpcId }
type ProjectionCell = { seq: number; value: unknown }
type ProjectionStore = Record<string, Record<string, ProjectionCell>>

function unwrap<T>(response: RpcResponse<T>): T {
  if (!response.result.ok) throw new Error(response.result.error.message)
  return response.result.value
}

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

function formatTime(value: number): string {
  const date = new Date(value)
  const now = new Date()
  if (date.toDateString() === now.toDateString()) return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function titleOf(session: SessionSummary, liveTitles: Record<string, string>): string {
  return liveTitles[String(session.sessionId)] || projectionTitle(session.projections) || (session.blank ? '新会话' : `会话 ${String(session.sessionId).slice(0, 8)}`)
}

function rowSeq(row: unknown): number | null {
  if (!row || typeof row !== 'object') return null
  const entry = row as { event?: { seq?: unknown }; seq?: unknown }
  const value = entry.event?.seq ?? entry.seq
  return typeof value === 'number' ? value : null
}

function mergeRows(current: unknown[], incoming: unknown[]): unknown[] {
  const unsequenced: unknown[] = []
  const sequenced = new Map<number, unknown>()
  for (const row of [...current, ...incoming]) {
    const seq = rowSeq(row)
    if (seq === null) unsequenced.push(row)
    else sequenced.set(seq, row)
  }
  return [...sequenced.entries()].sort(([a], [b]) => a - b).map(([, row]) => row).concat(unsequenced.slice(-12))
}

function setProjectionCell(store: ProjectionStore, sessionId: string, key: string, seq: number, value: unknown): ProjectionStore {
  const previous = store[sessionId]?.[key]
  if (previous && previous.seq > seq) return store
  return { ...store, [sessionId]: { ...(store[sessionId] || {}), [key]: { seq, value } } }
}

function seedProjectionBlock(store: ProjectionStore, sessionId: string, seq: number, values: object): ProjectionStore {
  let next = store
  for (const [key, value] of Object.entries(values)) next = setProjectionCell(next, sessionId, key, seq, value)
  return next
}

function seedSummaryProjections(store: ProjectionStore, sessions: SessionSummary[]): ProjectionStore {
  let next = store
  for (const session of sessions) if (session.projections) next = seedProjectionBlock(next, String(session.sessionId), session.projections.asOfSeq, session.projections.values)
  return next
}

function handleModelSelection(selection: ModelSelection, models: SessionModels, setNotice: (text: string) => void): void {
  const effort = selection.reasoningEffort ? ` · ${selection.reasoningEffort}` : ''
  setNotice(`已切换为 ${selection.provider} / ${selection.model}${effort}${models.routable ? '' : '（当前不可路由）'}`)
}

export default function App() {
  const [connection, setConnection] = useState<SavedConnection | null>(() => loadConnection())

  useEffect(() => { applyTheme(loadTheme()) }, [])

  if (!connection) {
    return <PairingScreen onConnected={(next) => { saveConnection(next); setConnection(next) }} />
  }
  return <MobileShell key={connection.endpoint} connection={connection} onDisconnect={() => {
    clearConnection()
    setConnection(null)
  }} />
}

function PairingScreen({ onConnected }: { onConnected: (connection: SavedConnection) => void }) {
  const [endpoint, setEndpoint] = useState(defaultEndpoint)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const normalized = normalizeEndpoint(endpoint)
      const token = await pair(normalized, code.replace(/\D/g, ''), deviceName())
      onConnected({ endpoint: normalized, token })
    } catch (cause) {
      setError(errorText(cause))
    } finally {
      setBusy(false)
    }
  }

  return <main className="pairing-page">
    <section className="pairing-card" aria-labelledby="pair-title">
      <div className="pairing-brand"><BrandMark /><strong>DeepSeek Harness</strong></div>
      <h1 id="pair-title">连接服务器</h1>
      <p className="pairing-copy">使用服务器生成的一次性配对码，接入已有会话和上下文。</p>
      <form onSubmit={submit}>
        <label className="field-label" htmlFor="endpoint">服务器地址</label>
        <div className="field-shell"><Icon name="server" /><input id="endpoint" type="url" value={endpoint} onChange={(e) => setEndpoint(e.target.value)} placeholder="https://dsh.example.com" autoCapitalize="none" autoCorrect="off" required /></div>
        <label className="field-label" htmlFor="pair-code">一次性配对码</label>
        <div className="field-shell code-field"><Icon name="key" /><input id="pair-code" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="000 000" autoComplete="one-time-code" required minLength={6} /></div>
        {error && <div className="inline-error" role="alert"><Icon name="alert" />{error}</div>}
        <button className="primary-button" disabled={busy || code.length !== 6}>{busy ? <span className="spinner" /> : <Icon name="link" />}{busy ? '正在连接…' : '配对并进入'}</button>
      </form>
      <div className="pairing-security"><Icon name="shield" /><span>远程连接使用 HTTPS，设备令牌安全保存在本机</span></div>
    </section>
  </main>
}

function MobileShell({ connection, onDisconnect }: { connection: SavedConnection; onDisconnect: () => void }) {
  const api = useMemo(() => new MobileApiClient(connection.endpoint, connection.token), [connection])
  const cached = useMemo(() => readCache(connection.endpoint), [connection.endpoint])
  const [sessions, setSessions] = useState<SessionSummary[]>(cached?.sessions || [])
  const [rows, setRows] = useState<Record<string, unknown[]>>(cached?.events || {})
  const [selectedId, setSelectedId] = useState<string | null>(cached?.selectedSessionId || cached?.sessions[0]?.sessionId || null)
  const [liveTitles, setLiveTitles] = useState<Record<string, string>>({})
  const [projections, setProjections] = useState<ProjectionStore>({})
  const [workspaces, setWorkspaces] = useState<WorkspaceView[]>([])
  const [archivedSessionIds, setArchivedSessionIds] = useState<string[]>([])
  const [status, setStatus] = useState<ConnectionState>(cached ? 'cache' : 'syncing')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [screen, setScreen] = useState<MainScreen>('conversation')
  const [approval, setApproval] = useState<ApprovalPending | null>(null)
  const [question, setQuestion] = useState<QuestionPending | null>(null)
  const [notice, setNotice] = useState('')
  const [trajectoryOpen, setTrajectoryOpen] = useState(false)
  const [theme, setTheme] = useState<AppTheme>(() => loadTheme())
  const cacheTimer = useRef<number | undefined>(undefined)

  const selected = sessions.find((session) => String(session.sessionId) === selectedId) || null
  const messages = useMemo(() => foldEvents(selectedId ? rows[selectedId] || [] : []), [rows, selectedId])
  const selectedProjections = useMemo(() => Object.fromEntries(Object.entries(selectedId ? projections[selectedId] || {} : {}).map(([key, cell]) => [key, cell.value])), [projections, selectedId])

  const refreshSessions = useCallback(async () => {
    const value = unwrap(await api.sessions.list({}))
    setSessions(value.items)
    setProjections((current) => seedSummaryProjections(current, value.items))
    setLiveTitles((current) => ({ ...current, ...Object.fromEntries(value.items.map((item) => [String(item.sessionId), projectionTitle(item.projections)]).filter((entry): entry is [string, string] => Boolean(entry[1]))) }))
    setSelectedId((current) => current && value.items.some((item) => String(item.sessionId) === current)
      ? current
      : String(value.items[0]?.sessionId || '') || null)
    setStatus('online')
  }, [api])

  const refreshWorkspaces = useCallback(async () => {
    try {
      const value = unwrap(await api.workspace.list({}))
      setWorkspaces(value.items)
      setArchivedSessionIds(value.archivedSessionIds.map(String))
    } catch { setWorkspaces([]); setArchivedSessionIds([]) }
  }, [api])

  useEffect(() => {
    refreshSessions().catch((error) => { setStatus('offline'); setNotice(errorText(error)) })
    void refreshWorkspaces()
  }, [refreshSessions, refreshWorkspaces])

  useEffect(() => { applyTheme(theme) }, [theme])

  useEffect(() => {
    if (!selectedId) return
    let cancelled = false
    api.sessions.history({ sessionId: selectedId as SessionSummary['sessionId'], maxMessages: 100 })
      .then(unwrap)
      .then((value) => {
        if (!cancelled) {
          setRows((current) => ({ ...current, [selectedId]: mergeRows(current[selectedId] || [], value.events) }))
          const block = value.projections
          if (block) {
            setProjections((current) => seedProjectionBlock(current, selectedId, block.asOfSeq, block.values))
            const title = projectionTitle(block)
            if (title) setLiveTitles((current) => ({ ...current, [selectedId]: title }))
          }
        }
      })
      .catch((error) => { if (!cancelled) setNotice(errorText(error)) })
    return () => { cancelled = true }
  }, [api, selectedId])

  useEffect(() => {
    const controller = new AbortController()
    const delay = (ms: number) => new Promise<void>((resolve) => window.setTimeout(resolve, ms))

    const muxLoop = async () => {
      while (!controller.signal.aborted) {
        try {
          for await (const message of api.events.mux({}, controller.signal, () => setStatus('online'))) {
            const frame = message.payload
            if (frame.type === 'session/event') {
              const id = String(frame.sessionId)
              setRows((current) => ({ ...current, [id]: mergeRows(current[id] || [], [{ event: frame.event, view: frame.view }]) }))
            } else if (frame.type === 'session/projection') {
              const id = String(frame.sessionId)
              setProjections((current) => setProjectionCell(current, id, frame.key, frame.seq, frame.value))
              if (frame.key === 'title' || frame.key === 'session-title') {
                const value = typeof frame.value === 'string' ? frame.value : (frame.value as { title?: string } | null)?.title
                if (value) setLiveTitles((current) => ({ ...current, [id]: value }))
              }
            } else if (frame.type === 'approval/requested') {
              setApproval({ ...frame, rpcId: message.rpcId })
            } else if (frame.type === 'approval/resolved') {
              setApproval((current) => current?.approvalId === frame.approvalId ? null : current)
            } else if (frame.type === 'question/requested') {
              setQuestion({ ...frame, rpcId: message.rpcId })
            } else if (frame.type === 'question/resolved') {
              setQuestion((current) => current?.rpcId === frame.questionRpcId ? null : current)
            } else if (frame.type === 'stream/error') {
              setNotice(frame.error.message)
            }
          }
        } catch (error) {
          if (controller.signal.aborted) break
          setStatus('offline')
          setNotice(errorText(error))
          await delay(1_500)
        }
      }
    }

    const hostLoop = async () => {
      while (!controller.signal.aborted) {
        try {
          for await (const message of api.events.host({}, controller.signal, () => setStatus('online'))) {
            applyHostFrame(message.payload, setSessions, setNotice)
            if (message.payload.type === 'host/session-added') refreshSessions().catch(() => {})
            if (message.payload.type === 'host/archived-sessions-changed') setArchivedSessionIds(message.payload.archivedSessionIds.map(String))
            if (message.payload.type === 'host/workspace-changed' || message.payload.type === 'host/workspace-removed' || message.payload.type === 'host/workspace-order-changed') void refreshWorkspaces()
          }
        } catch {
          if (controller.signal.aborted) break
          setStatus('offline')
          await delay(1_500)
        }
      }
    }

    void muxLoop()
    void hostLoop()
    return () => controller.abort()
  }, [api, refreshSessions, refreshWorkspaces])

  useEffect(() => {
    window.clearTimeout(cacheTimer.current)
    cacheTimer.current = window.setTimeout(() => writeCache({ endpoint: connection.endpoint, sessions, events: rows, selectedSessionId: selectedId }), 250)
    return () => window.clearTimeout(cacheTimer.current)
  }, [connection.endpoint, rows, selectedId, sessions])

  useEffect(() => {
    window.DSHMobileBack = () => {
      if (question || approval) return true
      if (trajectoryOpen) { setTrajectoryOpen(false); return true }
      if (drawerOpen) { setDrawerOpen(false); return true }
      if (screen === 'settings') { setScreen('conversation'); return true }
      return false
    }
    return () => { delete window.DSHMobileBack }
  }, [approval, drawerOpen, question, screen, trajectoryOpen])

  const createSession = async (workspace?: WorkspaceView) => {
    if (status === 'offline') { setNotice('离线状态不能创建会话'); return }
    try {
      const value = unwrap(await api.sessions.create(workspace ? { workspaceId: workspace.workspaceId } : {}))
      await refreshSessions()
      setSelectedId(String(value.sessionId))
      setDrawerOpen(false)
      setScreen('conversation')
      setTrajectoryOpen(false)
    } catch (error) { setNotice(errorText(error)) }
  }

  const chooseSession = (id: string) => {
    setSelectedId(id)
    setDrawerOpen(false)
    setScreen('conversation')
  }

  const answerApproval = async (outcome: 'allowed-once' | 'rejected') => {
    if (!approval) return
    try {
      const receipt = await api.respond({ type: 'client-response', rpcId: approval.rpcId, result: { ok: true, value: { sessionId: approval.sessionId, approvalId: approval.approvalId, outcome } } })
      if (!receipt.accepted) throw new Error('该授权请求已失效')
      setApproval(null)
    } catch (error) { setNotice(errorText(error)) }
  }

  if (screen === 'settings') {
    return <SettingsScreen api={api} endpoint={connection.endpoint} status={status} theme={theme} selected={selected} sessions={sessions} archivedSessionIds={archivedSessionIds} onTheme={setTheme} onBack={() => setScreen('conversation')} onClearCache={() => { purgeCache(); setRows({}); setNotice('本地缓存已清除，服务器会话不受影响') }} onDisconnect={onDisconnect} onRefreshSessions={refreshSessions} onOpenSession={chooseSession} onNotice={setNotice} />
  }

  return <div className="app-shell">
    <SessionDrawer open={drawerOpen} sessions={sessions.filter((session) => !archivedSessionIds.includes(String(session.sessionId)))} selectedId={selectedId} liveTitles={liveTitles} onClose={() => setDrawerOpen(false)} onSelect={chooseSession} onCreate={createSession} />
    <header className="top-bar">
      <button className="icon-button" aria-label="打开会话列表" onClick={() => setDrawerOpen(true)}><Icon name="menu" /></button>
      <button className="session-heading" onClick={() => setDrawerOpen(true)}>
        <span>{selected ? titleOf(selected, liveTitles) : 'DeepSeek Harness'}</span>
        <ConnectionBadge status={status} />
      </button>
      <button className="icon-button" aria-label="设置" onClick={() => setScreen('settings')}><Icon name="settings" /></button>
    </header>

    {!selected ? <EmptyState online={status !== 'offline'} onCreate={() => void createSession()} /> : <ConversationView items={messages} running={selected.running} onTrajectory={() => setTrajectoryOpen(true)} />}
    {selected && <DSHComposer api={api} session={selected} online={status !== 'offline'} projections={selectedProjections} workspaces={workspaces} onCreateInWorkspace={async (workspace) => { await createSession(workspace) }} onError={(error) => setNotice(error)} onSelection={(selection, models) => handleModelSelection(selection, models, setNotice)} />}
    {trajectoryOpen && selectedId && <TrajectorySheet rows={rows[selectedId] || []} onClose={() => setTrajectoryOpen(false)} />}
    {approval && String(approval.sessionId) === selectedId && <ApprovalSheet approval={approval} onAnswer={answerApproval} />}
    {question && String(question.sessionId) === selectedId && <QuestionSheet key={String(question.rpcId)} pending={question} api={api} onDone={() => setQuestion(null)} onError={(error) => setNotice(error)} />}
    {notice && <Toast text={notice} onClose={() => setNotice('')} />}
  </div>
}

function applyHostFrame(frame: HostFrame, setSessions: React.Dispatch<React.SetStateAction<SessionSummary[]>>, setNotice: (text: string) => void) {
  if (frame.type === 'host/session-status') {
    setSessions((current) => current.map((session) => session.sessionId === frame.sessionId ? { ...session, running: frame.running, blank: frame.running ? false : session.blank } : session))
  } else if (frame.type === 'host/session-removed') {
    setSessions((current) => current.filter((session) => session.sessionId !== frame.sessionId))
  } else if (frame.type === 'host/agent-error') {
    setNotice(frame.message)
  }
}

function SessionDrawer({ open, sessions, selectedId, liveTitles, onClose, onSelect, onCreate }: { open: boolean; sessions: SessionSummary[]; selectedId: string | null; liveTitles: Record<string, string>; onClose: () => void; onSelect: (id: string) => void; onCreate: () => void }) {
  return <div className={`drawer-layer ${open ? 'is-open' : ''}`} aria-hidden={!open}>
    <button className="drawer-scrim" aria-label="关闭会话列表" onClick={onClose} />
    <aside className="session-drawer" aria-label="会话列表">
      <div className="drawer-header"><div className="drawer-brand"><BrandMark /><strong>DeepSeek Harness</strong></div><button className="icon-button" aria-label="关闭" onClick={onClose}><Icon name="close" /></button></div>
      <button className="new-session-button" onClick={onCreate}><Icon name="plus" />新会话</button>
      <div className="drawer-label">最近会话</div>
      <nav className="session-list">
        {sessions.filter((session) => !session.blank || String(session.sessionId) === selectedId).map((session) => {
          const id = String(session.sessionId)
          return <button key={id} className={`session-row ${id === selectedId ? 'is-active' : ''}`} onClick={() => onSelect(id)}>
            <span className="session-icon"><Icon name="message" /></span>
            <span className="session-row-copy"><strong>{titleOf(session, liveTitles)}</strong><small>{session.cwd || '默认工作区'} · {formatTime(session.updatedAt)}</small></span>
            {session.running && <span className="running-dot" title="运行中" />}
          </button>
        })}
        {!sessions.length && <div className="drawer-empty">服务器上还没有会话</div>}
      </nav>
    </aside>
  </div>
}

function ConversationView({ items, running, onTrajectory }: { items: ConversationItem[]; running: boolean; onTrajectory: () => void }) {
  const tail = useRef<HTMLDivElement>(null)
  useEffect(() => { tail.current?.scrollIntoView({ block: 'end' }) }, [items])
  if (!items.length) return <section className="conversation-empty"><div className="empty-orbit"><BrandMark large /></div><h2>探索未至之境</h2><p>消息、上下文与工具轨迹都保存在服务器上的同一条 DSH 会话中。</p><button className="trajectory-link" onClick={onTrajectory}><Icon name="terminal"/>查看轨迹</button></section>
  return <main className="conversation" aria-live="polite">
    <div className="message-stack">
      <div className="conversation-actions"><button onClick={onTrajectory}><Icon name="terminal"/>轨迹</button></div>
      {items.map((item) => <MessageCard key={item.key} item={item} onTrajectory={onTrajectory} />)}
      {running && <div className="deep-diving"><BrandMark/><span>Deep diving...</span></div>}
      <div ref={tail} />
    </div>
  </main>
}

function MessageCard({ item, onTrajectory }: { item: ConversationItem; onTrajectory: () => void }) {
  if (item.kind === 'user') return <article className="message user-message">{item.text}</article>
  if (item.kind === 'reasoning') return <details className="reasoning-card" open={item.pending}><summary><Icon name="spark" />思考过程{item.pending && <span className="live-pill">LIVE</span>}</summary><Markdown text={item.text}/></details>
  if (item.kind === 'tool') return <button className={`tool-card ${item.pending ? 'is-pending' : ''}`} onClick={onTrajectory}><div className="tool-icon"><Icon name="terminal" /></div><div><strong>{item.title}</strong><p>{item.text || (item.pending ? '正在执行…' : '已完成')}</p></div><span className="tool-status">{item.pending ? <span className="spinner small" /> : <Icon name="check" />}</span></button>
  return <article className="message assistant-message"><div className="assistant-mark"><BrandMark /></div><div className="message-copy"><Markdown text={item.text}/>{item.pending && <span className="stream-caret" />}</div></article>
}

function TrajectorySheet({ rows, onClose }: { rows: unknown[]; onClose: () => void }) {
  const events = rows.map((row) => {
    const entry = row && typeof row === 'object' ? row as Record<string, unknown> : {}
    const event = (entry.event && typeof entry.event === 'object' ? entry.event : row) as Record<string, unknown>
    return { event, view: entry.view }
  }).filter((entry) => typeof entry.event.type === 'string').reverse()
  return <div className="interaction-layer" onPointerDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="interaction-sheet trajectory-sheet" role="dialog" aria-modal="true" aria-label="会话轨迹"><div className="sheet-handle"/><header><div><div className="eyebrow">DSH 原始事件账本</div><h2>会话轨迹</h2></div><button className="icon-button" onClick={onClose} aria-label="关闭"><Icon name="close"/></button></header><p className="trajectory-intro">按事件序号展示流式消息、工具调用与结果；工具卡优先保留 Host 提供的 presentation view。</p><div className="trajectory-list">{events.map(({ event, view }, index) => <details key={`${String(event.seq)}-${String(event.type)}-${index}`} className={String(event.type).startsWith('tool/') ? 'is-tool' : ''}><summary><span className="trajectory-seq">#{String(event.seq ?? '—')}</span><strong>{String(event.type)}</strong><time>{typeof event.time === 'number' ? new Date(event.time).toLocaleTimeString('zh-CN', { hour12: false }) : ''}</time></summary><pre>{JSON.stringify(view ? { data: event.data, view } : event.data, null, 2)}</pre></details>)}</div></section></div>
}

function ApprovalSheet({ approval, onAnswer }: { approval: ApprovalPending; onAnswer: (outcome: 'allowed-once' | 'rejected') => void }) {
  return <div className="interaction-layer"><section className="interaction-sheet" role="dialog" aria-modal="true" aria-labelledby="approval-title"><div className="sheet-handle" /><div className="approval-symbol"><Icon name="shield" /></div><div className="eyebrow">需要授权</div><h2 id="approval-title">允许执行 {approval.toolName}？</h2>{approval.reason && <p>{approval.reason}</p>}<div className="approval-meta"><span>仅针对当前请求</span><span>·</span><span>服务器端执行</span></div><div className="sheet-actions"><button className="secondary-button danger" onClick={() => onAnswer('rejected')}>拒绝</button><button className="primary-button" onClick={() => onAnswer('allowed-once')}><Icon name="check" />仅本次允许</button></div></section></div>
}

function QuestionSheet({ pending, api, onDone, onError }: { pending: QuestionPending; api: MobileApiClient; onDone: () => void; onError: (error: string) => void }) {
  const [answers, setAnswers] = useState<Record<string, string[]>>({})
  const [custom, setCustom] = useState<Record<string, string>>({})
  const complete = pending.questions.every((question) => (answers[question.id]?.length || 0) > 0 || (custom[question.id] || '').trim())
  const submit = async () => {
    try {
      const receipt = await api.respond({ type: 'client-response', rpcId: pending.rpcId, result: { ok: true, value: { sessionId: pending.sessionId, answer: { answers: pending.questions.map((question) => ({ id: question.id, selected: answers[question.id] || [], ...(custom[question.id]?.trim() ? { custom: custom[question.id].trim() } : {}) })) } } } })
      if (!receipt.accepted) throw new Error('该问题已失效')
      onDone()
    } catch (error) { onError(errorText(error)) }
  }
  return <div className="interaction-layer"><section className="interaction-sheet question-sheet" role="dialog" aria-modal="true"><div className="sheet-handle" /><div className="eyebrow">Agent 需要你的决定</div>{pending.questions.map((question) => <div className="question-block" key={question.id}><h2>{question.header || question.question}</h2>{question.header && <p>{question.question}</p>}{question.detail && <div className="question-detail">{question.detail}</div>}<div className="option-list">{question.options?.map((option) => { const selected = answers[question.id]?.includes(option.label); return <button key={option.label} className={selected ? 'is-selected' : ''} onClick={() => setAnswers((current) => { const before = current[question.id] || []; const next = question.multiSelect ? (selected ? before.filter((item) => item !== option.label) : [...before, option.label]) : [option.label]; return { ...current, [question.id]: next } })}><span><strong>{option.label}</strong>{option.description && <small>{option.description}</small>}</span>{selected && <Icon name="check" />}</button> })}</div>{!question.options?.length && <textarea className="question-input" value={custom[question.id] || ''} onChange={(e) => setCustom((current) => ({ ...current, [question.id]: e.target.value }))} placeholder="输入回答" />}</div>)}<button className="primary-button" disabled={!complete} onClick={() => void submit()}>提交回答</button></section></div>
}

function EmptyState({ online, onCreate }: { online: boolean; onCreate: () => void }) {
  return <main className="empty-state"><div className="empty-orbit"><BrandMark large /></div><h2>连接已就绪</h2><p>{online ? '服务器上还没有可显示的会话。创建一个会话，开始连续的 Agent 工作。' : '当前离线。恢复服务器连接后即可创建会话。'}</p><button className="primary-button compact" disabled={!online} onClick={onCreate}><Icon name="plus" />创建会话</button></main>
}

function ConnectionBadge({ status }: { status: ConnectionState }) {
  const label = { cache: '缓存', syncing: '同步中', online: '已连接', offline: '离线' }[status]
  return <span className={`connection-badge ${status}`}><i />{label}</span>
}

function Toast({ text, onClose }: { text: string; onClose: () => void }) {
  useEffect(() => { const id = window.setTimeout(onClose, 5000); return () => window.clearTimeout(id) }, [onClose])
  return <button className="toast" onClick={onClose}><Icon name="alert" /><span>{text}</span><Icon name="close" /></button>
}

function BrandMark({ large = false }: { large?: boolean }) {
  return <span className={`brand-mark ${large ? 'large' : ''}`}><svg viewBox="0 0 23.16 17.04" aria-hidden="true"><path d="M22.9168 1.43018C22.6713 1.31018 22.5658 1.53918 22.4223 1.65519C22.3733 1.69269 22.3318 1.74169 22.2903 1.78669C21.9317 2.1697 21.5127 2.42121 20.9657 2.39121C20.1657 2.34621 19.4827 2.59771 18.8787 3.20973C18.7502 2.45521 18.3236 2.0047 17.6746 1.71569C17.3351 1.56568 16.9916 1.41518 16.7536 1.08867C16.5876 .856163 16.5421 .597155 16.4591 .341647C16.4061 .187643 16.3536 .0301382 16.1761 .00363739C15.9836-.0263635 15.9081 .135141 15.8326 .270145C15.5306 .822162 15.4136 1.43018 15.4251 2.0462C15.4516 3.43174 16.0366 4.53527 17.1991 5.3203C17.3311 5.4103 17.3651 5.5003 17.3236 5.63181C17.2441 5.90231 17.1501 6.16482 17.0671 6.43533C17.0141 6.60784 16.9351 6.64584 16.7501 6.57033C16.1121 6.30383 15.5611 5.90931 15.074 5.4328C14.2475 4.63328 13.5 3.75075 12.568 3.05973C12.349 2.89822 12.13 2.74822 11.9034 2.60522C10.9524 1.68169 12.028 .923165 12.277 .833162C12.5375 .739159 12.3675 .41615 11.5259 .42015C10.6844 .42365 9.91439 .705658 8.93286 1.08117C8.78935 1.13767 8.63835 1.17867 8.48384 1.21267C7.59332 1.04367 6.66829 1.00617 5.70226 1.11517C3.88321 1.31768 2.43016 2.1777 1.36213 3.64575C.0790928 5.4103-.222916 7.41536 .146595 9.50642C.535106 11.7105 1.66014 13.535 3.38869 14.9616C5.18125 16.4406 7.24581 17.1657 9.60138 17.0266C11.0319 16.9441 12.6245 16.7526 14.421 15.2321C14.874 15.4576 15.3496 15.5476 16.1381 15.6151C16.7456 15.6716 17.3306 15.5851 17.7836 15.4911C18.4931 15.3411 18.4441 14.6841 18.1876 14.5636C16.1081 13.595 16.5646 13.9891 16.1496 13.67C17.2061 12.42 18.8202 10.1979 19.3182 7.17235C19.3672 6.83834 19.4297 6.36783 19.4222 6.09732C19.4182 5.93231 19.4562 5.86831 19.6447 5.84931C20.1657 5.78931 20.6712 5.64681 21.1357 5.3913C22.4833 4.65528 23.0268 3.44624 23.1548 1.9972C23.1738 1.77569 23.1508 1.54668 22.9168 1.43018ZM11.1749 14.4736C9.15936 12.889 8.18184 12.3675 7.77832 12.39C7.40081 12.4125 7.46881 12.8445 7.55182 13.126C7.63882 13.404 7.75182 13.5955 7.91033 13.8396C8.01983 14.0011 8.09533 14.2411 7.80083 14.4216C7.15181 14.8231 6.02327 14.2866 5.97027 14.2601C4.65673 13.4865 3.5587 12.4655 2.78467 11.069C2.03715 9.72493 1.60314 8.28289 1.53164 6.74384C1.51264 6.37233 1.62214 6.24082 1.99215 6.17332C2.47916 6.08332 2.98118 6.06432 3.46769 6.13582C5.52476 6.43633 7.27581 7.35586 8.74385 8.8129C9.58188 9.64243 10.2159 10.634 10.8689 11.6025C11.5634 12.631 12.3105 13.611 13.262 14.4146C13.598 14.6961 13.866 14.9101 14.1225 15.0681C13.349 15.1546 12.058 15.1731 11.1749 14.4736ZM12.141 8.25988C12.141 8.09488 12.273 7.96338 12.439 7.96338C12.6015 7.96338 12.7335 8.09488 12.7335 8.25988C12.7335 8.42489 12.6015 8.55639 12.4355 8.55639C12.2695 8.55639 12.141 8.42489 12.141 8.25988ZM15.1415 9.79893C14.949 9.87793 14.7565 9.94544 14.5715 9.95294C14.2845 9.96794 13.9715 9.85143 13.8015 9.70893C13.5375 9.48742 13.3485 9.36342 13.2695 8.97691C13.2355 8.8119 13.2545 8.55639 13.2845 8.40989C13.3525 8.09438 13.277 7.89187 13.0545 7.70787C12.8735 7.55786 12.643 7.51636 12.39 7.51636C12.2955 7.51636 12.209 7.47486 12.1445 7.44136C12.039 7.38886 11.9519 7.25735 12.035 7.09585C12.0615 7.04335 12.19 6.91584 12.22 6.89334C12.5635 6.69784 12.9595 6.76184 13.326 6.90834C13.6655 7.04735 13.9225 7.30236 14.292 7.66287C14.6695 8.09838 14.7375 8.21838 14.9525 8.54539C15.1225 8.8009 15.277 9.06341 15.3831 9.36392C15.4471 9.55142 15.3641 9.70493 15.1415 9.79893Z" /></svg></span>
}

function Icon({ name }: { name: string }) {
  const paths: Record<string, React.ReactNode> = {
    menu: <><path d="M4 7h16M4 12h16M4 17h12"/></>, settings: <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6 1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/></>,
    close: <path d="m6 6 12 12M18 6 6 18"/>, plus: <path d="M12 5v14M5 12h14"/>, message: <path d="M20 15a4 4 0 0 1-4 4H8l-4 3v-7a4 4 0 0 1-1-2.6V7a4 4 0 0 1 4-4h9a4 4 0 0 1 4 4v8Z"/>, arrowUp: <path d="m6 10 6-6 6 6M12 4v16"/>, 'arrow-up': <path d="m6 10 6-6 6 6M12 4v16"/>, stop: <rect x="7" y="7" width="10" height="10" rx="2"/>, check: <path d="m5 12 4 4L19 6"/>, alert: <><path d="M10.3 3.6 2.6 17a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 3.6a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4M12 17h.01"/></>, server: <><rect x="3" y="4" width="18" height="6" rx="2"/><rect x="3" y="14" width="18" height="6" rx="2"/><path d="M7 7h.01M7 17h.01"/></>, key: <><circle cx="8" cy="12" r="4"/><path d="M12 12h9M17 12v3M20 12v2"/></>, link: <><path d="M10 13a5 5 0 0 0 7.5.5l2-2a5 5 0 0 0-7-7l-1 1"/><path d="M14 11a5 5 0 0 0-7.5-.5l-2 2a5 5 0 0 0 7 7l1-1"/></>, shield: <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/>, spark: <path d="m12 3 1.4 4.1L17.5 8.5l-4.1 1.4L12 14l-1.4-4.1-4.1-1.4 4.1-1.4L12 3ZM18 14l.8 2.2L21 17l-2.2.8L18 20l-.8-2.2L15 17l2.2-.8L18 14Z"/>, terminal: <><rect x="3" y="4" width="18" height="16" rx="3"/><path d="m7 9 3 3-3 3M13 15h4"/></>, back: <path d="m15 18-6-6 6-6"/>, layers: <path d="m12 2 9 5-9 5-9-5 9-5Zm-9 10 9 5 9-5M3 17l9 5 9-5"/>, database: <><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"/></>, chevron: <path d="m9 18 6-6-6-6"/>, logout: <><path d="M10 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5M14 8l4 4-4 4M18 12H8"/></>,
  }
  return <svg className="icon" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>
}
