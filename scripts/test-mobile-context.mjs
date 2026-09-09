import assert from 'node:assert/strict';
import { cp, mkdir, rm, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = resolve(process.cwd());
const source = resolve(root, 'core/runtime-android/src/main/assets/runtime/dsh-mobile-context');
const temp = resolve(root, '.mcp/tmp/mobile-context-contract');
const plugin = resolve(temp, 'plugin');
const llm = resolve(temp, 'node_modules/@deepseek-ai/dsh-llm');

await rm(temp, { recursive: true, force: true });
await mkdir(llm, { recursive: true });
await cp(source, plugin, { recursive: true });
await writeFile(resolve(llm, 'package.json'), JSON.stringify({
  name: '@deepseek-ai/dsh-llm',
  version: '0.1.2-rc.1',
  type: 'module',
  exports: './index.js',
}));
await writeFile(resolve(llm, 'index.js'), `
let seq = 0;
export function boundContextSummary(summary) {
  return summary.length <= 120 ? summary : summary.slice(0, 119) + '…';
}
export function createUserMessage(input) {
  return Object.freeze({ id: 'mock-' + (++seq), role: 'user', ...input });
}
`);
const contextFile = resolve(temp, 'context.json');
await writeFile(contextFile, JSON.stringify({
  schemaVersion: 1,
  contextVersion: 'mobile-context-v2',
  androidVersion: '11',
  manufacturer: 'vivo',
  deviceModel: 'V2115A',
  dshVersion: '0.1.2-rc.1',
}));
process.env.DSH_MOBILE_CONTEXT_FILE = contextFile;

const mod = await import(pathToFileURL(resolve(plugin, 'lib/index.js')).href + `?test=${Date.now()}`);
const snapshot = mod.readBootSnapshot();
const first = mod.renderStaticContext(snapshot);
const second = mod.renderStaticContext(snapshot);
assert.equal(first, second, 'Mobile system section must remain byte-stable.');
for (const required of ['Linux Runtime', 'Android Local', 'ADB Shell', 'PRoot', 'DSH_HOME']) {
  assert.ok(first.includes(required), `Missing stable context term: ${required}`);
}
for (const forbidden of ['battery', 'current time', 'foreground Activity', 'current network address']) {
  assert.ok(!first.toLowerCase().includes(forbidden.toLowerCase()), `Volatile term leaked into stable section: ${forbidden}`);
}

const registeredSections = [];
const handlers = new Map();
mod.apply({
  inject(deps, factory) {
    assert.deepEqual(deps, ['systemPrompt']);
    factory({ systemPrompt: {
      getSectionOrder(sectionName) {
        assert.equal(sectionName, 'WEB_SURFACE');
        return 100;
      },
      section(value) { registeredSections.push(value); },
    }});
  },
  on(event, handler) {
    handlers.set(event, handler);
    return () => handlers.delete(event);
  },
});
assert.equal(registeredSections.length, 1);
assert.equal(registeredSections[0].text(), registeredSections[0].text());
const onSessionStart = handlers.get('agent/session-start');
assert.equal(typeof onSessionStart, 'function');

function makeAgent(header = {}, firstLiveSeq = 0) {
  const injected = [];
  const agent = {
    session: {
      firstLiveSeq,
      header: { cwd: '/workspace/demo', isSeeded: false, ...header },
    },
    inject(message) { injected.push(message); },
  };
  return { agent, injected };
}

const fresh = makeAgent();
onSessionStart({ agent: fresh.agent, source: 'startup' });
assert.equal(fresh.injected.length, 1);
assert.equal(fresh.injected[0].source.kind, 'plugin');
assert.equal(fresh.injected[0].source.plugin, '@dsh-mobile/dsh-mobile-context');
assert.equal(fresh.injected[0].source.form, 'snapshot');
assert.equal(fresh.injected[0].source.sections.length, 3);
assert.match(fresh.injected[0].content[0].text, /\/workspace\/demo/);

for (const candidate of [
  { ...makeAgent(), source: 'resume' },
  { ...makeAgent({ isSeeded: true, parentSession: 'parent' }), source: 'startup' },
  { ...makeAgent({ origin: 'subagent' }), source: 'startup' },
  { ...makeAgent({}, 2), source: 'startup' },
]) {
  onSessionStart({ agent: candidate.agent, source: candidate.source });
  assert.equal(candidate.injected.length, 0, `Unexpected bootstrap for ${candidate.source}`);
}

const notice = mod.createEnvironmentNotice('x'.repeat(160), 'ADB Shell is now available.');
assert.equal(notice.source.form, 'notice');
assert.ok(notice.source.summary.length <= 120);
assert.equal(notice.content[0].text, 'ADB Shell is now available.');
mod.injectEnvironmentNotice(fresh.agent, 'ADB available', 'ADB Shell is now available.');
assert.equal(fresh.injected.length, 2);

console.log('mobile-context-contract: PASS');
console.log(`stable_bytes=${Buffer.byteLength(first, 'utf8')}`);
console.log(`snapshot_sections=${fresh.injected[0].source.sections.map((section) => section.name).join(',')}`);
console.log(`notice_summary_max=${notice.source.summary.length}`);
