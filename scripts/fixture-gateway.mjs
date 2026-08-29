import http from 'node:http'
import { randomUUID } from 'node:crypto'

const PORT = Number(process.env.DSH_MOBILE_FIXTURE_PORT || 4318)
const API = '/dsh-mobile-v1/api'
const TOKEN = 'fixture-device-token'
const SESSION_ID = '15ec6bd5-a9f5-4815-a68a-f627cd36fd71'
const ARCHIVED_SESSION_ID = '9be8d420-7a31-4b87-a6f9-f78cc10a1882'
const startedAt = Date.now()
let currentModel = { provider: 'deepseek', model: 'deepseek-chat', reasoningEffort: 'high' }

const modelGroups = [{
  id: 'deepseek',
  name: 'DeepSeek',
  models: [
    { id: 'deepseek-chat', name: 'DeepSeek Chat', description: '服务器已配置的通用模型', reasoning: { efforts: [{ id: 'low', name: 'Low' }, { id: 'high', name: 'High' }], defaultEffort: 'high' } },
    { id: 'deepseek-reasoner', name: 'DeepSeek Reasoner', description: '服务器已配置的推理模型', reasoning: { efforts: [{ id: 'high', name: 'High' }], defaultEffort: 'high' } },
  ],
}]

const historyEvents = [
  { event: { type: 'user/message', seq: 1, time: startedAt - 20_000, data: { message: { role: 'user', content: [{ type: 'text', text: '检查移动端的流式输出和 JavaScript 代码展示。' }] } } } },
  { event: { type: 'assistant/chunk', seq: 2, time: startedAt - 18_000, data: { chunk: { type: 'reasoning-delta', text: '我先确认界面约束，再执行工具检查。' } } } },
  { event: { type: 'tool/call', seq: 3, time: startedAt - 15_000, data: { callId: 'fixture-tool-1', name: 'read_file', arguments: { path: 'packages/mobile-client/src/App.tsx' } } } },
  { event: { type: 'tool/result', seq: 4, time: startedAt - 12_000, data: { message: { role: 'tool', content: [{ type: 'tool-result', toolCallId: 'fixture-tool-1', content: [{ type: 'text', text: '读取完成：移动端壳使用 DSH ApiProxy。' }] }] } } } },
  { event: { type: 'assistant/message', seq: 5, time: startedAt - 8_000, data: { message: { role: 'assistant', content: [{ type: 'text', text: '### 检查结果\n\n工具轨迹已关联。JavaScript 示例：\n\n```js\nconst stream = await client.events.mux()\nfor await (const frame of stream) render(frame)\n```\n\n**Deep diving...** 的反馈也会使用原生高亮。' }] } } } },
  { event: { type: 'turn/end', seq: 6, time: startedAt - 7_000, data: {} } },
]

function headers(extra = {}) {
  return {
    'access-control-allow-origin': 'http://127.0.0.1:4173',
    'access-control-allow-headers': 'Authorization, Content-Type',
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    vary: 'Origin',
    ...extra,
  }
}

function json(res, status, value) {
  const body = JSON.stringify(value)
  res.writeHead(status, headers({ 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) }))
  res.end(body)
}

function response(request, value) {
  return { type: 'server-response', rpcId: request.rpcId, result: { ok: true, value } }
}

async function body(req) {
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', `http://127.0.0.1:${PORT}`)
  console.log(req.method, url.pathname)
  if (req.method === 'OPTIONS') { res.writeHead(204, headers()); res.end(); return }
  if (url.pathname === API + '/health') { json(res, 200, { ok: true, appVersion: 'fixture', contractVersion: 1 }); return }
  if (url.pathname === API + '/pair' && req.method === 'POST') {
    const value = await body(req)
    if (value.code !== '123456') { json(res, 401, { ok: false, error: 'invalid-pairing-code' }); return }
    json(res, 200, { ok: true, token: TOKEN, deviceId: 'browser-fixture' })
    return
  }
  if (req.headers.authorization !== `Bearer ${TOKEN}`) { json(res, 401, { ok: false, error: 'device-auth-required' }); return }
  if (url.pathname === API + '/events/mux' || url.pathname === API + '/events/host') {
    res.writeHead(200, headers({ 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' }))
    res.write(': fixture stream\n\n')
    const timer = setInterval(() => res.write(': keepalive\n\n'), 10_000)
    req.on('close', () => clearInterval(timer))
    return
  }
  if (url.pathname.startsWith(API + '/rpc/') && req.method === 'POST') {
    const request = await body(req)
    const method = decodeURIComponent(url.pathname.slice((API + '/rpc/').length))
    if (method === 'session.list') {
      json(res, 200, response(request, { items: [
        { sessionId: SESSION_ID, updatedAt: startedAt, running: false, blank: false, cwd: '/srv/projects/deepseek-harness', agentPreset: 'standard', projections: { asOfSeq: 6, values: { title: { title: '移动端原生适配检查' } } } },
        { sessionId: ARCHIVED_SESSION_ID, updatedAt: startedAt - 86_400_000, running: false, blank: false, cwd: '/srv/projects/deepseek-harness', agentPreset: 'minimal', projections: { asOfSeq: 3, values: { title: { title: '已归档的部署排查' } } } },
      ] }))
      return
    }
    if (method === 'session.history') { json(res, 200, response(request, { events: historyEvents, hasMore: false, projections: { asOfSeq: 6, values: { title: { title: '移动端原生适配检查' }, permissions: { currentValue: 'default', options: [{ value: 'default', name: '默认权限', description: '按 DSH 服务器规则请求授权' }, { value: 'yolo', name: '自动允许', description: '仅建议在隔离环境使用' }] }, plan: { active: false }, contextPressure: { projectedTokens: 32768, contextWindow: 131072 }, imageLimits: { maxImageBytes: 10485760, maxImagesPerMessage: 8, maxMessageImageBytes: 20971520, maxImagePixels: 40000000, maxImageDimension: 12000, mediaTypes: ['image/png', 'image/jpeg', 'image/webp', 'image/gif'] } } } })); return }
    if (method === 'workspace.list') { json(res, 200, response(request, { items: [{ workspaceId: 'fixture-workspace', path: '/srv/projects/deepseek-harness', title: 'deepseek-harness', sessionIds: [SESSION_ID, ARCHIVED_SESSION_ID], createdAt: new Date(startedAt - 86_400_000).toISOString(), updatedAt: new Date(startedAt).toISOString() }], archivedSessionIds: [ARCHIVED_SESSION_ID] })); return }
    if (method === 'session.models') { json(res, 200, response(request, { current: currentModel, routable: true, groups: modelGroups, failures: [] })); return }
    if (method === 'session.selectModel') { currentModel = { provider: request.payload.provider, model: request.payload.model, ...(request.payload.reasoningEffort ? { reasoningEffort: request.payload.reasoningEffort } : {}) }; json(res, 200, response(request, { selected: currentModel })); return }
    if (method === 'agentPreset.list') { json(res, 200, response(request, { presets: [{ id: 'standard', trust: 'system', isDefault: true, name: '标准模式', description: '完整的编码 Agent，支持工具、Skills、计划与子代理。' }, { id: 'minimal', trust: 'system', isDefault: false, name: '极简模式', description: '仅保留基础文件与 Shell 能力。' }, { id: 'creator', trust: 'system', isDefault: false, name: '创造模式', description: '用于创建和检查自定义 Agent preset。' }], authorable: true, hasDocument: true })); return }
    if (method === 'skill.list') { json(res, 200, response(request, { skills: [{ name: 'frontend-design', description: '构建与检查前端界面。', whenToUse: '需要实现移动端 UI 时', modelInvocable: true }, { name: 'review', description: '执行只读代码审查。', modelInvocable: true }, { name: 'handoff', description: '整理交接记录。', modelInvocable: false }] })); return }
    if (method === 'session.create') { json(res, 200, response(request, { sessionId: randomUUID() })); return }
    if (method === 'session.prompt' || method === 'session.cancel') { json(res, 200, response(request, { accepted: true })); return }
    json(res, 403, { ok: false, error: 'fixture-method-not-supported' })
    return
  }
  json(res, 404, { ok: false, error: 'not-found' })
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`DSH Mobile browser fixture: http://127.0.0.1:${PORT}/dsh-mobile-v1`)
  console.log('Pairing code: 123456')
})
