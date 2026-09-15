import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const sourcePath = process.argv[2];
assert.ok(sourcePath, 'usage: node test-tokyo-night-restore-policy.mjs <client.js>');
const source = fs.readFileSync(sourcePath, 'utf8');

let registration = null;
const themeListeners = new Set();
const settingsListeners = new Set();
const storage = new Map([['dsh-mobile:theme-extension:v1', 'tokyo-night']]);
const appliedThemes = [];
const provided = new Map();

class FakeHTMLElement {
  constructor() {
    this.attributes = new Map();
    this.dataset = {};
    this.textContent = '';
  }
  setAttribute(name, value) { this.attributes.set(name, String(value)); }
  removeAttribute(name) { this.attributes.delete(name); }
  getAttribute(name) { return this.attributes.get(name) ?? null; }
  remove() { this.removed = true; }
}

const body = new FakeHTMLElement();
const head = new FakeHTMLElement();
head.appendChild = (node) => { node.parent = head; };

const localStorage = {
  getItem(key) { return storage.has(key) ? storage.get(key) : null; },
  setItem(key, value) { storage.set(key, String(value)); },
  removeItem(key) { storage.delete(key); },
};

let settingsSnapshot = {
  status: 'loading',
  value: undefined,
  user: undefined,
  revision: undefined,
};
const settingsScope = {
  getSnapshot() { return settingsSnapshot; },
  subscribe(listener) {
    settingsListeners.add(listener);
    return () => settingsListeners.delete(listener);
  },
};

let themeSnapshot = {
  preference: 'system',
  revision: 0,
  active: { id: 'light', colorScheme: 'light' },
};

function publishTheme(next) {
  themeSnapshot = { ...next, revision: themeSnapshot.revision + 1 };
  for (const listener of [...themeListeners]) listener(themeSnapshot);
}

function publishSettings(next) {
  settingsSnapshot = next;
  for (const listener of [...settingsListeners]) listener();
}

const theme = {
  register(definition) {
    assert.equal(definition.id, 'tokyo-night');
    assert.equal(definition.colorScheme, 'dark');
    return () => {};
  },
  getTheme() { return themeSnapshot; },
  setTheme(id) {
    appliedThemes.push(id);
    assert.equal(id, 'tokyo-night');
    publishTheme({
      preference: 'tokyo-night',
      active: { id: 'tokyo-night', colorScheme: 'dark' },
    });
  },
};

const windowObject = {
  __ModuleLoader__: { load(value) { registration = value; } },
  localStorage,
};

const documentObject = {
  body,
  head,
  createElement(tag) {
    assert.equal(tag, 'style');
    return new FakeHTMLElement();
  },
};

const previous = new Map();
for (const [key, value] of Object.entries({
  window: windowObject,
  document: documentObject,
  HTMLElement: FakeHTMLElement,
})) {
  previous.set(key, globalThis[key]);
  globalThis[key] = value;
}

try {
  vm.runInThisContext(source, { filename: sourcePath });
  assert.ok(registration, 'module registration missing');
  const plugin = registration.factory();
  let cleanup = null;
  const ctx = {
    theme,
    settingsScope: { bind(spec) {
      assert.deepEqual(spec, { namespace: 'ui-theme' });
      return settingsScope;
    } },
    provide(name, value) {
      provided.set(name, value);
    },
    on(event, listener) {
      assert.equal(event, 'theme/change');
      themeListeners.add(listener);
      return () => themeListeners.delete(listener);
    },
    effect(effect) { cleanup = effect(); },
  };

  plugin.apply(ctx);
  const extension = provided.get('tokyoNightTheme');
  assert.ok(extension, 'Tokyo Night extension service must be provided');
  assert.equal(appliedThemes.length, 0, 'restore must wait while official ui-theme settings are loading');
  assert.equal(storage.get('dsh-mobile:theme-extension:v1'), 'tokyo-night');

  // Initial settings answer and official ThemeRuntime adoption can arrive in either
  // order. The extension preference must win once that startup wave settles.
  publishSettings({
    status: 'ready',
    value: { preference: 'dark', fontSize: 14 },
    user: { preference: 'dark' },
    revision: 7,
  });
  publishTheme({ preference: 'dark', active: { id: 'dark', colorScheme: 'dark' } });
  await Promise.resolve();
  assert.equal(themeSnapshot.preference, 'tokyo-night');

  // Reproduce the browser failure that motivated the service seam: an even later
  // built-in adoption with the SAME settings revision must still be replayed.
  publishTheme({ preference: 'system', active: { id: 'light', colorScheme: 'light' } });
  await Promise.resolve();
  assert.equal(themeSnapshot.preference, 'tokyo-night', 'late startup adoption must not beat the extension preference');
  assert.equal(body.getAttribute('data-dsh-theme-tokyo-night'), 'active');
  assert.equal(storage.get('dsh-mobile:theme-extension:v1'), 'tokyo-night');

  // ui-theme also owns font size. A revision bump that changes only font size must
  // not be mistaken for an explicit built-in theme selection.
  publishSettings({
    status: 'ready',
    value: { preference: 'dark', fontSize: 15 },
    user: { preference: 'dark', fontSize: 15 },
    revision: 8,
  });
  await Promise.resolve();
  assert.equal(extension.isSelected(), true);
  assert.equal(storage.get('dsh-mobile:theme-extension:v1'), 'tokyo-night');

  // The mobile Appearance adapter clears extension ownership in capture phase
  // before the official built-in button calls ThemeRuntime.setTheme().
  extension.clear();
  publishTheme({ preference: 'system', active: { id: 'light', colorScheme: 'light' } });
  await Promise.resolve();
  assert.equal(themeSnapshot.preference, 'system');
  assert.equal(extension.isSelected(), false);
  assert.equal(storage.has('dsh-mobile:theme-extension:v1'), false);
  assert.equal(body.getAttribute('data-dsh-theme-tokyo-night'), null);

  // Service selection is the one explicit extension entry used by mobile UI.
  extension.select();
  assert.equal(themeSnapshot.preference, 'tokyo-night');
  assert.equal(extension.isSelected(), true);

  // A remote Host preference change has a new ui-theme revision AND a changed
  // preference fingerprint, so it legitimately retires the browser-local extension.
  publishSettings({
    status: 'ready',
    value: { preference: 'light', fontSize: 15 },
    user: { preference: 'light', fontSize: 15 },
    revision: 9,
  });
  publishTheme({ preference: 'light', active: { id: 'light', colorScheme: 'light' } });
  await Promise.resolve();
  assert.equal(themeSnapshot.preference, 'light');
  assert.equal(extension.isSelected(), false);
  assert.equal(storage.has('dsh-mobile:theme-extension:v1'), false);

  assert.equal(typeof cleanup, 'function');
  cleanup();
  assert.equal(settingsListeners.size, 0);
  assert.equal(themeListeners.size, 0);

  console.log('tokyo-night-restore-policy: PASS late-adoption replay + explicit/remote built-in ownership');
} finally {
  for (const [key, value] of previous) {
    if (value === undefined) delete globalThis[key];
    else globalThis[key] = value;
  }
}
