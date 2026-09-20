import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const registrations = new Map();
const disposal = [];
const clickStates = [];
const domListeners = new Map();
const paperclipAttrs = new Map();
let nativePaperclipClicks = 0;
let inputActive = true;
let lastOpenEffect = false;
const nativeRequests = [];
class FakeElement {
  constructor() { this.disabled = false; this.nextElementSibling = null; }
  closest(selector) {
    if (selector === 'button') return this;
    if (selector === '[data-composer-card]') return {};
    return null;
  }
  setAttribute(name, value) { paperclipAttrs.set(name, value); }
  removeAttribute(name) { paperclipAttrs.delete(name); }
}
const paperclip = new FakeElement();
class Input {
  constructor() {
    this.type = 'file'; this.multiple = true; this.disabled = false;
    this.isConnected = true; this.attributes = new Map();
    this.accept = ''; this.listeners = new Map();
  }
  addEventListener(type, fn) { this.listeners.set(type, fn); }
  removeEventListener(type, fn) { if (this.listeners.get(type) === fn) this.listeners.delete(type); }
  emit(type) { this.listeners.get(type)?.(); }
  getAttribute(name) { return this.attributes.get(name) ?? null; }
  setAttribute(name, value) { this.attributes.set(name, value); if (name === 'accept') this.accept = value; }
  removeAttribute(name) { this.attributes.delete(name); if (name === 'accept') this.accept = ''; }
  click() { clickStates.push({ accept: this.accept, capture: this.getAttribute('capture'), multiple: this.multiple }); }
}
const input = new Input();
paperclip.nextElementSibling = input;
const React = {
  useSyncExternalStore(_subscribe, getSnapshot) { return getSnapshot(); },
  useState(value) { return [value, () => {}]; },
  useEffect(fn, deps) {
    if (deps?.[0] !== lastOpenEffect) { lastOpenEffect = deps[0]; fn(); }
  },
};
const jsx = {
  jsx(type, props, key) { return { type, props, key }; },
  jsxs(type, props, key) { return { type, props, key }; },
};
const context = {
  HTMLInputElement: Input,
  Element: FakeElement,
  document: {
    head: { appendChild(element) { context.style = element; } },
    querySelector(selector) {
      if (selector === '[data-phase="hero"]' || selector === '[data-composer-seat]') return null;
      throw new Error(`unexpected selector ${selector}`);
    },
    addEventListener(type, fn) { domListeners.set(type, fn); },
    removeEventListener(type, fn) { if (domListeners.get(type) === fn) domListeners.delete(type); },
    createElement(tag) {
      assert.equal(tag, 'style');
      return { id: '', textContent: '', remove() { context.style = null; } };
    },
    querySelectorAll(selector) {
      if (selector === '[data-composer-card] input[type="file"]') return inputActive ? [input] : [];
      if (selector.includes('aria-controls')) return [paperclip];
      throw new Error(`unexpected query: ${selector}`);
    },
  },
  window: {
    __ModuleLoader__: {
      load({ id, factory }) {
        assert.equal(id, '@dsh-mobile/dsh-mobile-attachment-sources');
        context.plugin = factory(name => {
          if (name === 'react') return React;
          if (name === 'react/jsx-runtime') return jsx;
          throw new Error(`unexpected dependency: ${name}`);
        });
      },
    },
  },
};
vm.runInNewContext(readFileSync('core/runtime-android/src/main/assets/runtime/dsh-mobile-attachment-sources/lib/client.js','utf8'), context);
assert.deepEqual([...context.plugin.inject], ['slots']);
context.plugin.apply({
  slots: {
    inject(name, register) {
      assert.ok(['conversation.input.dock','conversation.composer.dock'].includes(name));
      register();
    },
    register(meta, Component) {
      assert.ok(meta.id?.startsWith('dshm-attachment-'));
      registrations.set(meta.name, Component);
      return () => registrations.delete(meta.name);
    },
  },
  effect(fn) { disposal.push(fn()); },
});
assert.equal(registrations.size, 2, 'hero and active composer docks only; no replacement control');
assert.equal(context.style.textContent.includes('button:has(+ input[type="file"])'), false,
  'never hide upstream official paperclip');
assert.ok(context.style.textContent.includes('order: 20'), 'hero dock must display after composer');
assert.ok(context.style.textContent.includes('prefers-reduced-motion'), 'motion has reduced-motion fallback');
assert.ok(context.style.textContent.includes('[data-dshm-attachment-source]:active'),
  'camera/album/file cards must offer tactile press feedback');
assert.ok(context.style.textContent.includes('[data-dshm-recent-preview]:active'),
  'recent thumbnails must offer tactile press feedback without a new selection path');
assert.ok(context.style.textContent.includes('scale(.965)'),
  'pressing should transform only the visual layer, never reflow the composer');
assert.ok(context.style.textContent.includes('transition: none;'),
  'reduced-motion must disable card and thumbnail animation');
assert.ok(domListeners.has('click'), 'official paperclip click is intercepted in capture phase');
const trigger = () => {
  const event = { target:paperclip, prevented:false, stopped:false,
    preventDefault() { this.prevented = true; },
    stopImmediatePropagation() { this.stopped = true; },
  };
  domListeners.get('click')(event);
  if (!event.stopped) nativePaperclipClicks++;
  assert.equal(event.prevented, true);
  assert.equal(event.stopped, true);
};
const heroSlot = registrations.get('conversation.input.dock');
const activeSlot = registrations.get('conversation.composer.dock');
const hero = () => { const node = heroSlot(); return node.type(node.props); };
const active = () => { const node = activeSlot(); return node.type(node.props); };
assert.equal(hero(), null, 'initial hero dock closed');
assert.equal(active(), null, 'initial active dock closed');
trigger();
let shown = active();
shown = active();
assert.equal(shown.props['data-dshm-attachment-panel'], '');
assert.equal(shown.props['data-dshm-attachment-placement'], 'active');
assert.equal(hero(), null, 'hidden hero slot must not query media in active phase');
assert.equal(shown.props.children[0].props['data-dshm-recent-rail'], 'unavailable',
  'without the native bridge the gallery does not fabricate thumbnails');
let buttons = shown.props.children[2].props.children;
assert.equal(buttons.length, 3);
assert.ok(buttons.every(button => button.props.style.flex === '1 1 0'));
assert.deepEqual(Array.from(buttons, button => button.props['data-dshm-attachment-source']), ['camera','album','file']);
assert.ok(buttons.every(button => button.props.children[0].type === 'svg'));
assert.equal(nativePaperclipClicks, 0, 'official chooser is not opened until a source is chosen');
buttons[0].props.onClick();
assert.equal(active(), null);
assert.deepEqual(clickStates[0], { accept:'image/*', capture:'environment', multiple:false });
input.emit('cancel');
assert.equal(input.getAttribute('capture'), null);
assert.equal(input.getAttribute('accept'), null);
assert.equal(input.multiple, true);
trigger();
shown = active();
shown.props.children[2].props.children[1].props.onClick();
assert.deepEqual(clickStates[1], { accept:'image/*', capture:null, multiple:true });
input.emit('change');
assert.equal(input.getAttribute('accept'), null);
trigger();
shown = active();
shown.props.children[2].props.children[2].props.onClick();
assert.deepEqual(clickStates[2], { accept:'', capture:null, multiple:true });
trigger();
inputActive = false;
shown = active();
shown.props.children[2].props.children[0].props.onClick();
assert.equal(clickStates.length, 3, 'missing official input never creates another upload channel');
inputActive = true;
trigger(); // closed
trigger(); // open
const escape = { key:'Escape', preventDefault() { this.prevented = true; } };
domListeners.get('keydown')(escape);
assert.equal(escape.prevented, true);
assert.equal(active(), null);
// Simulate Android's optional permission-gated bridge without accessing real photos.
context.window.dshMobileRecentMedia = {
  postMessage(payload) { nativeRequests.push(JSON.parse(payload)); },
};
trigger();
shown = active();
shown = active();
assert.equal(nativeRequests.at(-1)?.action, 'list', 'opening panel requests recent media only then');
const listId = nativeRequests.at(-1).id;
context.window.dshMobileRecentMedia.onmessage({ data: JSON.stringify({ schema:1,id:listId,state:'permission-required' }) });
shown = active();
assert.equal(shown.props.children[0].props['data-dshm-recent-permission'], '');
shown.props.children[0].props.onClick();
assert.equal(nativeRequests.at(-1)?.action, 'permission', 'permission only on explicit user tap');
const permissionId = nativeRequests.at(-1).id;
context.window.dshMobileRecentMedia.onmessage({ data: JSON.stringify({ schema:1,id:permissionId,state:'granted' }) });
assert.equal(nativeRequests.at(-1)?.action, 'list');
const retryId = nativeRequests.at(-1).id;
const fakeKey = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
context.window.dshMobileRecentMedia.onmessage({ data: JSON.stringify({schema:1,id:retryId,state:'items',photos:[{key:fakeKey,uri:'content://secret/ignored'}]}) });
assert.equal(nativeRequests.at(-1)?.action, 'thumb');
assert.equal(nativeRequests.at(-1)?.key, fakeKey);
const thumbId = nativeRequests.at(-1).id;
context.window.dshMobileRecentMedia.onmessage({ data: JSON.stringify({ schema:1,id:thumbId,state:'thumbnail', thumbnail:'data:image/jpeg;base64,AA==' }) });
shown = active();
assert.equal(shown.props.children[0].props['data-dshm-recent-rail'], 'ready');
assert.equal(shown.props.children[0].props.children[0].props.children.props.src, 'data:image/jpeg;base64,AA==');
assert.equal(JSON.stringify(shown).includes('content://secret'), false, 'never forward source URIs to DOM');
trigger();
assert.equal(active(), null);
for (const stop of disposal.reverse()) stop?.();
assert.equal(domListeners.size, 0, 'event interception must be removed when plugin is disabled');
assert.equal(input.getAttribute('capture'), null);
assert.equal(input.getAttribute('accept'), null);
assert.equal(input.multiple, true);
assert.equal(context.style, null, 'disabling plugin restores exact official paperclip');

assert.equal(paperclipAttrs.size, 0, 'ARIA attrs restored on unload');
console.log('mobile-attachment-sources: PASS official-icon,popup-below,hero,3-actions,permission,recent-preview,escape,unload');
