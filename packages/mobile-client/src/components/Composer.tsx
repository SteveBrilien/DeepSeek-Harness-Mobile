import { useEffect, useMemo, useRef, useState } from 'react'
import type { ModelSelection, PromptContentPart, SessionModels, SessionSummary } from '@deepseek-ai/dsh-host-apiproxy/api/sessions'
import type { WorkspaceView } from '@deepseek-ai/dsh-host-apiproxy/api/workspace'
import type { MobileApiClient } from '../api/MobileApiClient'
import { ModelSheet } from './ModelSheet'
import { Icon, Sheet } from './ui'

type ProjectionValues = Record<string, unknown>
type DraftAttachment = { id: string; file: File; kind: 'image' | 'text'; preview?: string; text?: string }

function unwrap<T>(response: { result: { ok: true; value: T } | { ok: false; error: { message: string } } }): T {
  if (!response.result.ok) throw new Error(response.result.error.message)
  return response.result.value
}

function asRecord(value: unknown): Record<string, unknown> { return value && typeof value === 'object' ? value as Record<string, unknown> : {} }
function permissionProjection(value: unknown): { currentValue: string; options: Array<{ value: string; name: string; description?: string }> } | null {
  const row = asRecord(value)
  const options = Array.isArray(row.options) ? row.options.map(asRecord).filter((item) => typeof item.value === 'string').map((item) => ({ value: String(item.value), name: typeof item.name === 'string' ? item.name : String(item.value), ...(typeof item.description === 'string' ? { description: item.description } : {}) })) : []
  return typeof row.currentValue === 'string' && options.length ? { currentValue: row.currentValue, options } : null
}
function planActive(value: unknown): boolean {
  const plan = asRecord(value)
  return Boolean(plan.pending ? !plan.active : plan.active)
}
function contextInfo(value: unknown): { used: number; window: number; percent: number } | null {
  const row = asRecord(value)
  const used = typeof row.projectedTokens === 'number' ? row.projectedTokens : row.pressureTokens
  const window = row.contextWindow
  if (typeof used !== 'number' || typeof window !== 'number' || window <= 0) return null
  return { used, window, percent: Math.min(100, Math.round(used / window * 100)) }
}
function imageLimits(value: unknown): { maxImageBytes: number; maxImagesPerMessage: number; maxMessageImageBytes: number; mediaTypes: string[] } {
  const row = asRecord(value)
  return {
    maxImageBytes: typeof row.maxImageBytes === 'number' ? row.maxImageBytes : 10 * 1024 * 1024,
    maxImagesPerMessage: typeof row.maxImagesPerMessage === 'number' ? row.maxImagesPerMessage : 8,
    maxMessageImageBytes: typeof row.maxMessageImageBytes === 'number' ? row.maxMessageImageBytes : 20 * 1024 * 1024,
    mediaTypes: Array.isArray(row.mediaTypes) ? row.mediaTypes.filter((item): item is string => typeof item === 'string') : ['image/png', 'image/jpeg', 'image/webp', 'image/gif'],
  }
}
function formatTokens(value: number): string { return value >= 1000 ? `${Math.round(value / 100) / 10}k` : String(value) }
function fileId(): string { return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}` }
function readDataUrl(file: File): Promise<string> { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onerror = () => reject(reader.error); reader.onload = () => resolve(String(reader.result || '')); reader.readAsDataURL(file) }) }
function readText(file: File): Promise<string> { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onerror = () => reject(reader.error); reader.onload = () => resolve(String(reader.result || '')); reader.readAsText(file) }) }
function imageMediaType(value: string): 'image/png' | 'image/jpeg' | 'image/webp' | 'image/gif' {
  if (value === 'image/png' || value === 'image/jpeg' || value === 'image/webp' || value === 'image/gif') return value
  throw new Error('DSH rc.2 仅接受 JPEG、PNG、WebP 或 GIF 图片')
}

export function Composer({ api, session, online, projections, workspaces, onCreateInWorkspace, onError, onSelection }: { api: MobileApiClient; session: SessionSummary; online: boolean; projections: ProjectionValues; workspaces: WorkspaceView[]; onCreateInWorkspace: (workspace?: WorkspaceView) => Promise<void>; onError: (message: string) => void; onSelection: (selection: ModelSelection, models: SessionModels) => void }) {
  const [text, setText] = useState('')
  const [attachments, setAttachments] = useState<DraftAttachment[]>([])
  const [sending, setSending] = useState(false)
  const [queueMode, setQueueMode] = useState<'queue' | 'steer'>('queue')
  const [sheet, setSheet] = useState<'attach' | 'commands' | 'permissions' | 'workspace' | 'model' | 'context' | null>(null)
  const [models, setModels] = useState<SessionModels | null>(null)
  const [commandBusy, setCommandBusy] = useState(false)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const cameraInput = useRef<HTMLInputElement>(null)
  const galleryInput = useRef<HTMLInputElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const permissions = useMemo(() => permissionProjection(projections.permissions), [projections.permissions])
  const isPlan = planActive(projections.plan)
  const context = contextInfo(projections.contextPressure)
  const limits = useMemo(() => imageLimits(projections.imageLimits), [projections.imageLimits])
  const workspace = workspaces.find((item) => item.sessionIds.some((id) => String(id) === String(session.sessionId)))

  useEffect(() => { setModels(null); setAttachments((before) => { before.forEach((item) => item.preview && URL.revokeObjectURL(item.preview)); return [] }) }, [session.sessionId])
  useEffect(() => { if (!session.running) setQueueMode('queue') }, [session.running])
  useEffect(() => { let cancelled = false; api.sessions.models({ sessionId: session.sessionId }).then(unwrap).then((value) => { if (!cancelled) setModels(value) }).catch(() => {}); return () => { cancelled = true } }, [api, session.sessionId])

  const runCommand = async (line: string) => {
    if (!online || commandBusy) return
    setCommandBusy(true)
    try {
      unwrap(await api.sessions.prompt({ sessionId: session.sessionId, mode: 'queue', content: [{ type: 'text', text: line }], clientTimeZone: Intl.DateTimeFormat().resolvedOptions().timeZone }))
      setSheet(null)
    } catch (error) { onError(error instanceof Error ? error.message : String(error)) }
    finally { setCommandBusy(false) }
  }

  const intake = async (files: FileList | null, source: 'image' | 'file') => {
    if (!files?.length) return
    try {
      const next: DraftAttachment[] = []
      let imageCount = attachments.filter((item) => item.kind === 'image').length
      let imageBytes = attachments.filter((item) => item.kind === 'image').reduce((sum, item) => sum + item.file.size, 0)
      for (const file of Array.from(files)) {
        if (file.type.startsWith('image/')) {
          imageMediaType(file.type)
          if (!limits.mediaTypes.includes(file.type)) throw new Error(`${file.name} 的格式未被当前 DSH 部署允许`)
          if (file.size > limits.maxImageBytes) throw new Error(`${file.name} 超过单图 ${Math.round(limits.maxImageBytes / 1024 / 1024)} MB 限制`)
          if (++imageCount > limits.maxImagesPerMessage) throw new Error(`每条消息最多 ${limits.maxImagesPerMessage} 张图片`)
          imageBytes += file.size
          if (imageBytes > limits.maxMessageImageBytes) throw new Error(`图片总大小超过 ${Math.round(limits.maxMessageImageBytes / 1024 / 1024)} MB`)
          next.push({ id: fileId(), file, kind: 'image', preview: URL.createObjectURL(file) })
        } else if (source === 'file' && (/^text\//.test(file.type) || /\.(md|txt|json|js|jsx|ts|tsx|css|html|xml|yaml|yml|toml|py|java|kt|kts|gradle|sh|ps1|csv)$/i.test(file.name))) {
          if (file.size > 1024 * 1024) throw new Error(`${file.name} 超过文本文件 1 MB 限制`)
          next.push({ id: fileId(), file, kind: 'text', text: await readText(file) })
        } else throw new Error(`${file.name} 不是 rc.2 可安全提交的图片或文本文件`)
      }
      setAttachments((before) => [...before, ...next].slice(0, 8))
      setSheet(null)
    } catch (error) { onError(error instanceof Error ? error.message : String(error)) }
  }

  const removeAttachment = (id: string) => setAttachments((before) => { const removed = before.find((item) => item.id === id); if (removed?.preview) URL.revokeObjectURL(removed.preview); return before.filter((item) => item.id !== id) })
  const send = async () => {
    const trimmed = text.trim()
    if ((!trimmed && !attachments.length) || sending || !online) return
    setSending(true)
    try {
      const content: PromptContentPart[] = []
      for (const item of attachments) {
        if (item.kind === 'image') {
          const dataUrl = await readDataUrl(item.file)
          content.push({ type: 'image', mediaType: imageMediaType(item.file.type), data: dataUrl.slice(dataUrl.indexOf(',') + 1), name: item.file.name })
        } else content.push({ type: 'text', text: `\n\n--- 文件：${item.file.name} ---\n${item.text || ''}\n--- 文件结束 ---` })
      }
      if (trimmed) content.push({ type: 'text', text: trimmed })
      unwrap(await api.sessions.prompt({ sessionId: session.sessionId, mode: session.running ? queueMode : 'queue', content, clientTimeZone: Intl.DateTimeFormat().resolvedOptions().timeZone }))
      setText('')
      setAttachments((before) => { before.forEach((item) => item.preview && URL.revokeObjectURL(item.preview)); return [] })
      if (textarea.current) textarea.current.style.height = ''
    } catch (error) { onError(error instanceof Error ? error.message : String(error)) }
    finally { setSending(false) }
  }

  const resize = (target: HTMLTextAreaElement) => { target.style.height = 'auto'; target.style.height = `${Math.min(target.scrollHeight, 150)}px` }
  const modelText = models ? `${models.current.model}${models.current.reasoningEffort ? ` · ${models.current.reasoningEffort}` : ''}` : '模型'

  return <footer className="composer-zone native-composer-zone">
    <div className="composer-meta-row">
      <button onClick={() => setSheet('workspace')}><Icon name="folder"/><span>{workspace?.title || session.cwd?.split(/[\\/]/).filter(Boolean).at(-1) || '工作区'}</span><Icon name="down"/></button>
      {session.running && <span className="composer-running">运行中 · {queueMode === 'queue' ? '后续排队' : '引导当前任务'}</span>}
    </div>
    <div className="composer native-composer">
      {!!attachments.length && <div className="attachment-rail">{attachments.map((item) => <div className="attachment-chip" key={item.id}>{item.preview ? <img src={item.preview} alt=""/> : <span><Icon name="file"/></span>}<small>{item.file.name}</small><button aria-label={`移除 ${item.file.name}`} onClick={() => removeAttachment(item.id)}><Icon name="close"/></button></div>)}</div>}
      <textarea ref={textarea} rows={2} value={text} onChange={(event) => { setText(event.target.value); resize(event.target) }} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void send() } }} placeholder={online ? (isPlan ? '描述你希望规划的内容' : session.running ? '添加后续指令…' : '描述你想要构建的内容') : '离线：恢复连接后发送'}/>
      <div className="composer-toolbar"><div className="composer-tools">
        <button className="composer-round" aria-label="添加附件或命令" onClick={() => setSheet('attach')}><Icon name="plus"/></button>
        <button className={isPlan ? 'composer-chip active' : 'composer-chip'} onClick={() => void runCommand(isPlan ? '/plan off' : '/plan')}><Icon name="route"/><span>{isPlan ? '计划模式' : '标准模式'}</span><Icon name="down"/></button>
        <button className="composer-chip permission-chip" onClick={() => setSheet('permissions')}><Icon name="shield"/><span>{permissions?.currentValue || '权限'}</span><Icon name="down"/></button>
      </div><div className="composer-trailing">
        <button className="model-trigger" onClick={() => setSheet('model')} title={modelText}><span>{modelText}</span><Icon name="down"/></button>
        {context && <button className="context-meter" aria-label={`上下文已用 ${context.percent}%`} onClick={() => setSheet('context')}><svg viewBox="0 0 18 18"><circle cx="9" cy="9" r="7"/><circle className="meter-fill" cx="9" cy="9" r="7" strokeDasharray={`${43.98 * context.percent / 100} 43.98`} transform="rotate(-90 9 9)"/></svg></button>}
        {session.running && (text.trim() || attachments.length) ? <button className="queue-send" aria-label="提交后续指令" disabled={sending || !online} onClick={() => void send()}><Icon name="arrow-up"/></button> : null}
        <button className="primary-action" aria-label={session.running ? '停止运行' : '发送'} disabled={!online || (!session.running && !text.trim() && !attachments.length) || sending} onClick={() => session.running ? api.sessions.cancel({ sessionId: session.sessionId }).then(unwrap).catch((error) => onError(error instanceof Error ? error.message : String(error))) : void send()}>{sending ? <span className="spinner small"/> : <Icon name={session.running ? 'stop' : 'arrow-up'}/>}</button>
      </div></div>
    </div>
    <input ref={cameraInput} hidden type="file" accept="image/*" capture="environment" onChange={(event) => void intake(event.target.files, 'image')}/>
    <input ref={galleryInput} hidden type="file" accept="image/*" multiple onChange={(event) => void intake(event.target.files, 'image')}/>
    <input ref={fileInput} hidden type="file" accept="image/*,text/*,.md,.json,.js,.jsx,.ts,.tsx,.css,.html,.xml,.yaml,.yml,.toml,.py,.java,.kt,.kts,.gradle,.sh,.ps1,.csv" multiple onChange={(event) => void intake(event.target.files, 'file')}/>

    {sheet === 'attach' && <Sheet eyebrow="添加内容" title="图片、文件与命令" onClose={() => setSheet(null)}><div className="attachment-actions"><button onClick={() => cameraInput.current?.click()}><Icon name="camera"/><strong>拍照</strong><small>使用相机拍摄图片</small></button><button onClick={() => galleryInput.current?.click()}><Icon name="image"/><strong>相册</strong><small>选择一张或多张图片</small></button><button onClick={() => fileInput.current?.click()}><Icon name="paperclip"/><strong>文件</strong><small>图片或文本/代码文件</small></button><button onClick={() => setSheet('commands')}><Icon name="command"/><strong>命令</strong><small>插入 DSH 斜杠命令</small></button></div><p className="sheet-note">图片由 DSH 持久化，并由 DeepSeek 适配器自动使用 Files API；文本文件转为明确标记的文本上下文。</p></Sheet>}
    {sheet === 'commands' && <Sheet eyebrow="DSH 命令" title="快捷命令" onClose={() => setSheet(null)}><div className="command-list">{[['/help','查看服务器命令帮助'],['/compact','压缩当前会话上下文'],['/goal ','设置或编辑当前目标'],['/plan','进入计划模式']].map(([line, note]) => <button key={line} onClick={() => { setText(line); setSheet(null); requestAnimationFrame(() => textarea.current?.focus()) }}><Icon name="command"/><span><strong>{line}</strong><small>{note}</small></span><Icon name="chevron"/></button>)}</div></Sheet>}
    {sheet === 'permissions' && <Sheet eyebrow="工具权限" title="权限预设" onClose={() => setSheet(null)}>{permissions ? <div className="sheet-option-list">{permissions.options.filter((item) => item.value !== 'custom').map((item) => <button key={item.value} className={item.value === permissions.currentValue ? 'is-selected' : ''} onClick={() => void runCommand(`/permission ${item.value}`)}><Icon name="shield"/><span><strong>{item.name}</strong>{item.description && <small>{item.description}</small>}</span>{item.value === permissions.currentValue && <Icon name="check"/>}</button>)}</div> : <div className="sheet-empty">当前 DSH 组合没有发布权限预设。不会显示或猜测 Cordis 内部字段。</div>}</Sheet>}
    {sheet === 'workspace' && <Sheet eyebrow="工作区" title="为新会话选择工作区" onClose={() => setSheet(null)}><div className="sheet-option-list"><button onClick={() => void onCreateInWorkspace()}><Icon name="folder"/><span><strong>服务器默认工作区</strong><small>创建一条新的会话</small></span><Icon name="chevron"/></button>{workspaces.map((item) => <button key={String(item.workspaceId)} className={workspace?.workspaceId === item.workspaceId ? 'is-selected' : ''} onClick={() => void onCreateInWorkspace(item)}><Icon name="folder"/><span><strong>{item.title}</strong><small>{item.path}</small></span><Icon name="chevron"/></button>)}</div><p className="sheet-note">DSH 会话的工作目录不会在中途偷偷改变；选择工作区会创建一条上下文清晰的新会话。</p></Sheet>}
    {sheet === 'context' && context && <Sheet eyebrow="上下文窗口" title={`已使用 ${context.percent}%`} onClose={() => setSheet(null)}><div className="context-panel"><div className="context-big-ring" style={{ '--meter': `${context.percent * 3.6}deg` } as React.CSSProperties}><strong>{context.percent}%</strong></div><div><strong>约 {formatTokens(context.used)} / {formatTokens(context.window)} tokens</strong><p>来自 DSH 的 <code>contextPressure</code> 投影；这是动态参考值，不是本地估算。</p></div></div></Sheet>}
    {sheet === 'model' && <ModelSheet api={api} session={session} onClose={() => setSheet(null)} onError={onError} onSelected={(selection, next) => { setModels(next); onSelection(selection, next) }}/>} 
  </footer>
}
