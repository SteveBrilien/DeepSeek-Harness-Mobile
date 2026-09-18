// Isolation fixture for the APK-managed Browser plugin: exercise actual
// pointer handlers without requiring a device or rewriting upstream DSH.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const handlers = new Map();
const frames = new Map();
let nextFrame = 1;
let clock = 0;
let toggles = 0;
const callbacks = [];
class ElementMock {
  constructor(tag = 'DIV') {
    this.tagName = tag.toUpperCase();
    this.attrs = new Map();
    this.children = [];
    this.parentElement = null;
    this.scrollWidth = this.clientWidth = 360;
    this.dataset = {};
    this.style = {
      values: new Map(),
      setProperty(key, value) { this.values.set(key, value); },
      removeProperty(key) { this.values.delete(key); },
    };
  }
  setAttribute(key, value) { this.attrs.set(key, value); }
  hasAttribute(key) { return this.attrs.has(key); }
  getAttribute(key) { return this.attrs.get(key) ?? null; }
  removeAttribute(key) { this.attrs.delete(key); }
  append(...nodes) { for (const n of nodes) { n.parentElement = this; this.children.push(n); } }
  appendChild(node) { this.append(node); return node; }
  prepend(node) { if (node.parentElement) node.parentElement.children = node.parentElement.children.filter(n => n !== node); node.parentElement = this; this.children.unshift(node); }
  remove() { if (this.parentElement) this.parentElement.children = this.parentElement.children.filter(n => n !== this); this.parentElement = null; }
  contains(node) { for (let p = node; p; p = p.parentElement) if (p === this) return true; return false; }
  closest() { return null; }
  querySelector(query) {
    if (query.includes('[data-dshm-sidebar-col]')) return this.children.find(n => n.hasAttribute('data-dshm-sidebar-col')) ?? null;
    if (query.includes('[data-dshm-center-col]')) return this.children.find(n => n.hasAttribute('data-dshm-center-col')) ?? null;
    return null;
  }
  querySelectorAll() { return []; }
  getBoundingClientRect() { return { width: this === sidebar ? 288 : 360, height: 700 }; }
  addEventListener(name, fn) { const k = this === documentMock ? name : `${this.tagName}:${name}`; (handlers.get(k) ?? handlers.set(k, []).get(k)).push(fn); }
  removeEventListener(name, fn) { const k = this === documentMock ? name : `${this.tagName}:${name}`; handlers.set(k, (handlers.get(k) ?? []).filter(f => f !== fn)); }
  click() { for (const fn of handlers.get(`${this.tagName}:click`) ?? []) fn(); }
}
const html = new ElementMock('html');
const body = new ElementMock('body');
const head = new ElementMock('head');
const frame = new ElementMock('div');
const sidebar = new ElementMock('div');
const center = new ElementMock('div');
const right = new ElementMock('div');
const overlay = new ElementMock('div');
frame.append(sidebar, center, right, overlay);
frame.setAttribute('data-sidebar-collapsed', '');
frame.setAttribute('data-rightbar-collapsed', '');
const documentMock = {
  documentElement: html, body, head,
  createElement: tag => new ElementMock(tag),
  querySelector: selector => selector === '[data-shell-overlay]' ? overlay : null,
  querySelectorAll: () => [],
  addEventListener: ElementMock.prototype.addEventListener,
  removeEventListener: ElementMock.prototype.removeEventListener,
};
const mql = { matches: true, addEventListener() {}, removeEventListener() {} };
const context = {
  document: documentMock,
  HTMLElement: ElementMock, Element: ElementMock, HTMLButtonElement: ElementMock,
  MutationObserver: class { observe() {} disconnect() {} },
  getComputedStyle: () => ({ display: 'block', visibility: 'visible', overflowX: 'hidden' }),
  requestAnimationFrame(fn) { const id = nextFrame++; frames.set(id, fn); return id; },
  cancelAnimationFrame(id) { frames.delete(id); },
  performance: { now: () => clock },
  clearTimeout() {},
  window: { matchMedia: () => mql, setTimeout: () => 321, clearTimeout() {}, dshMobileTheme: null,
    __ModuleLoader__: { load(def) { context.plugin = def.factory(() => ({})); } } },
};
const source = readFileSync('core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/lib/client.js', 'utf8');
vm.runInNewContext(source, context, { filename: 'client.js', timeout: 10000 });
assert.equal(typeof context.plugin.apply, 'function');
context.plugin.apply({
  theme: { getTheme: () => null },
  tokyoNightTheme: {},
  on: () => () => {},
  layout: { toggleSidebar() {
    toggles++;
    if (frame.hasAttribute('data-sidebar-collapsed')) frame.removeAttribute('data-sidebar-collapsed');
    else frame.setAttribute('data-sidebar-collapsed', '');
  } },
  effect(fn) { callbacks.push(fn()); },
});
function tick() { const queue = [...frames.values()]; frames.clear(); for (const fn of queue) fn(); }
function event(target, x, y, type, pointerId = 7) {
  const e = { target, clientX: x, clientY: y, pointerId, isPrimary: true, button: 0,
    cancelable: true, prevented: false, preventDefault() { this.prevented = true; } };
  for (const fn of handlers.get(type) ?? []) fn(e);
  return e;
}
function swipe(target, x1, y1, x2, y2, end = 'pointerup') {
  event(target, x1, y1, 'pointerdown');
  clock += 60;
  const moving = event(target, x2, y2, 'pointermove');
  clock += 100;
  event(target, x2, y2, end);
  tick(); tick();
  return moving;
}
assert.equal(html.getAttribute('data-dshm-drawer'), 'closed');
assert.equal(toggles, 0);
assert.equal(swipe(center, 45, 210, 160, 214).prevented, true, 'right swipe captures horizontal motion');
assert.equal(toggles, 1, 'right swipe opens exactly once');
assert.equal(html.getAttribute('data-dshm-drawer'), 'open');
assert.equal(swipe(sidebar, 230, 210, 110, 210).prevented, true);
assert.equal(toggles, 2, 'left swipe closes exactly once');
assert.equal(html.getAttribute('data-dshm-drawer'), 'closed');
swipe(center, 45, 150, 48, 240);
assert.equal(toggles, 2, 'vertical scrolling not stolen');
swipe(center, 10, 150, 170, 151);
assert.equal(toggles, 2, 'Android system Back edge excluded');
const rail = new ElementMock('div');
rail.closest = selector => selector.includes('data-composer-card') ? rail : null;
swipe(rail, 45, 180, 170, 180);
assert.equal(toggles, 2, 'horizontal media rail excluded');
html.setAttribute('data-dshm-settings', 'open');
swipe(center, 45, 180, 170, 180);
assert.equal(toggles, 2, 'Settings dialog suspends gestures');
html.removeAttribute('data-dshm-settings');
swipe(center, 45, 180, 170, 180, 'pointercancel');
assert.equal(toggles, 2, 'cancel cannot commit');
assert.equal(html.hasAttribute('data-dshm-dragging'), false, 'drag styling always released');
for (const cleanup of callbacks.reverse()) cleanup?.();
assert.equal((handlers.get('pointerdown') ?? []).length, 0, 'unload removes pointer listeners');
assert.equal(html.hasAttribute('data-dsh-mobile-ui'), false, 'unload removes mobile marker');
console.log('mobile-drawer-gesture: PASS open,close,vertical,system-edge,rail,modal,cancel,unload');
