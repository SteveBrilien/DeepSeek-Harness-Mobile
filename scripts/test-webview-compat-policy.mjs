import fs from 'fs';
import vm from 'vm';
import assert from 'assert';

const source = fs.readFileSync(
  new URL('../core/runtime-android/src/main/assets/runtime/dsh-webview-compat/lib/client.js', import.meta.url),
  'utf8',
);

class FakeStyle {
  constructor() {
    this.values = new Map();
    this.priorities = new Map();
    this.order = [];
    this.cssText = '';
  }
  get length() { return this.order.length; }
  item(index) { return this.order[index] || ''; }
  setProperty(name, value, priority = '') {
    if (!this.values.has(name)) this.order.push(name);
    this.values.set(name, String(value));
    this.priorities.set(name, String(priority));
  }
  getPropertyValue(name) { return this.values.get(name) || ''; }
  getPropertyPriority(name) { return this.priorities.get(name) || ''; }
  removeProperty(name) {
    this.values.delete(name);
    this.priorities.delete(name);
    this.order = this.order.filter((item) => item !== name);
  }
}

function numericHeight(style, defaultHeight, viewportUnitHeight) {
  let height = style.getPropertyValue('height');
  if (!height && style.cssText) {
    const match = style.cssText.match(/(?:^|;)height:([^;]+)/);
    height = match ? match[1].trim() : '';
  }
  if (height.endsWith('px')) return Number.parseFloat(height);
  if (height === '100dvh' || height === '100vh') return viewportUnitHeight;
  return defaultHeight;
}

function makeNode({ width = 360, initialHeight = 0, viewportUnitHeight = 0, clientHeight = 0, children = 0 } = {}) {
  const style = new FakeStyle();
  return {
    style,
    clientHeight,
    childElementCount: children,
    appendChild() {},
    setAttribute() {},
    remove() {},
    getBoundingClientRect() {
      return {
        width,
        height: numericHeight(style, initialHeight, viewportUnitHeight),
      };
    },
  };
}

function runCase({ viewportUnitHeight, visualViewportHeight = 670, expectedMode, expectedOwnedHeight, expectCssPatch }) {
  let registration = null;
  const messages = [];
  const root = makeNode({ initialHeight: 0, viewportUnitHeight, children: 1 });
  const html = makeNode({ initialHeight: 0, viewportUnitHeight, clientHeight: 670 });
  const body = makeNode({ initialHeight: 0, viewportUnitHeight });
  const settingsStyle = new FakeStyle();
  settingsStyle.setProperty('height', 'min(800px, 100vh - 48px)');
  const nestedStyle = new FakeStyle();
  nestedStyle.setProperty('max-height', 'min(52vh, 480px)');
  const dynamicStyle = new FakeStyle();
  dynamicStyle.setProperty('height', 'min(500px, 100dvh - 32px)');
  const fakeStyleSheets = [{
    cssRules: [
      { style: settingsStyle },
      { cssRules: [{ style: nestedStyle }, { style: dynamicStyle }] },
    ],
  }];
  const visualListeners = new Map();
  const windowListeners = new Map();
  let nextTimer = 1;
  let nextFrame = 1;

  const fakeWindow = {
    __ModuleLoader__: { load(value) { registration = value; } },
    __DSH_BOOT__: { rev: 'boot-test' },
    innerWidth: 360,
    innerHeight: 670,
    visualViewport: {
      height: visualViewportHeight,
      addEventListener(name, fn) { visualListeners.set(name, fn); },
      removeEventListener(name) { visualListeners.delete(name); },
    },
    CSS: { supports() { return true; } },
    dshMobilePresentation: {
      postMessage(raw) { messages.push(JSON.parse(raw)); },
    },
    setTimeout(fn) { fn(); return nextTimer++; },
    clearTimeout() {},
    requestAnimationFrame(fn) { fn(); return nextFrame++; },
    cancelAnimationFrame() {},
    addEventListener(name, fn) { windowListeners.set(name, fn); },
    removeEventListener(name) { windowListeners.delete(name); },
  };

  const fakeDocument = {
    documentElement: html,
    body,
    readyState: 'complete',
    scripts: [],
    styleSheets: fakeStyleSheets,
    adoptedStyleSheets: [],
    head: null,
    getElementById(id) { return id === 'root' ? root : null; },
    createElement() {
      return makeNode({ initialHeight: 0, viewportUnitHeight });
    },
  };

  class FakeMutationObserver {
    constructor(fn) { this.fn = fn; }
    observe() {}
    disconnect() {}
  }

  const previous = new Map();
  const globals = {
    window: fakeWindow,
    document: fakeDocument,
    navigator: { userAgent: 'Mozilla/5.0 (Linux; Android 11; Test Build; wv) AppleWebKit/537.36' },
    CSS: fakeWindow.CSS,
    MutationObserver: FakeMutationObserver,
    location: { href: 'http://127.0.0.1:3080/' },
  };
  for (const [key, value] of Object.entries(globals)) {
    previous.set(key, globalThis[key]);
    globalThis[key] = value;
  }

  try {
    vm.runInThisContext(source, { filename: 'dsh-webview-compat/client.js' });
    assert.ok(registration, 'module registration missing');
    const plugin = registration.factory();
    let cleanup = null;
    plugin.apply({
      effect(fn) { cleanup = fn(); },
    });

    const applied = messages.find((message) => message.phase === 'root-contract-applied');
    assert.ok(applied, 'root-contract-applied message missing');
    assert.equal(applied.repairMode, expectedMode);
    assert.equal(root.style.getPropertyValue('height'), expectedOwnedHeight);
    assert.equal(root.style.getPropertyPriority('height'), 'important');
    assert.equal(applied.metrics.rootHeight, 670);
    if (expectCssPatch) {
      assert.equal(settingsStyle.getPropertyValue('height'), 'min(800px, 670px - 48px)');
      assert.equal(nestedStyle.getPropertyValue('max-height'), 'min(348.4px, 480px)');
      assert.equal(dynamicStyle.getPropertyValue('height'), `min(500px, ${visualViewportHeight}px - 32px)`);
      assert.equal(applied.metrics.verticalViewportPatchedDeclarations, 3);
    } else {
      assert.equal(settingsStyle.getPropertyValue('height'), 'min(800px, 100vh - 48px)');
      assert.equal(nestedStyle.getPropertyValue('max-height'), 'min(52vh, 480px)');
      assert.equal(dynamicStyle.getPropertyValue('height'), 'min(500px, 100dvh - 32px)');
      assert.equal(applied.metrics.verticalViewportPatchedDeclarations, 0);
    }
    assert.ok(messages.some((message) => message.phase === 'presentation-ready'), 'presentation-ready missing');
    assert.ok(!messages.some((message) => message.phase === 'presentation-degraded'), 'unexpected degraded state');

    assert.equal(typeof cleanup, 'function');
    cleanup();
    assert.equal(root.style.getPropertyValue('height'), '', 'cleanup must restore root height');
    assert.equal(html.style.getPropertyValue('height'), '', 'cleanup must restore html height');
    assert.equal(body.style.getPropertyValue('height'), '', 'cleanup must restore body height');
    assert.equal(settingsStyle.getPropertyValue('height'), 'min(800px, 100vh - 48px)', 'cleanup must restore stylesheet height');
    assert.equal(nestedStyle.getPropertyValue('max-height'), 'min(52vh, 480px)', 'cleanup must restore nested stylesheet value');
    assert.equal(dynamicStyle.getPropertyValue('height'), 'min(500px, 100dvh - 32px)', 'cleanup must restore dynamic viewport value');
  } finally {
    for (const [key, value] of previous) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
  }
}

runCase({
  viewportUnitHeight: 0,
  expectedMode: 'measured-layout-px',
  expectedOwnedHeight: '670px',
  expectCssPatch: true,
});
runCase({
  viewportUnitHeight: 0,
  visualViewportHeight: 400,
  expectedMode: 'measured-layout-px',
  expectedOwnedHeight: '670px',
  expectCssPatch: true,
});
runCase({
  viewportUnitHeight: 670,
  expectedMode: 'native-100dvh',
  expectedOwnedHeight: '100dvh',
  expectCssPatch: false,
});

console.log('webview-compat-policy: PASS measured-px root + vertical viewport CSS fallback + native preservation');
