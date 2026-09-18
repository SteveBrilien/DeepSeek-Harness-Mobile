import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const registrations = new Map();
const disposal = [];
const clickStates = [];
let inputActive = true;
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
const React = {
  useSyncExternalStore(_subscribe, getSnapshot) { return getSnapshot(); },
  useState(value) { return [value, () => {}]; },
  useEffect() {},
};
const jsx = {
  jsx(type, props, key) { return { type, props, key }; },
  jsxs(type, props, key) { return { type, props, key }; },
};
const context = {
  HTMLInputElement: Input,
  document: {
    head: { appendChild(element) { context.style = element; } },
    createElement(tag) {
      assert.equal(tag, 'style');
      return { id: '', textContent: '', remove() { context.style = null; } };
    },
    querySelectorAll(selector) {
    assert.equal(selector, '[data-composer-card] input[type="file"]');
    return inputActive ? [input] : [];
  } },
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
      assert.ok(['conversation.input.left','conversation.input.dock'].includes(name));
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
assert.equal(registrations.size, 2, 'only two public list slots; no single attachment rail takeover');
assert.ok(context.style.textContent.includes('button:has(+ input[type="file"])'));
assert.equal(context.style.textContent.includes('input[type="file"] { display: none'), false);
const trigger = registrations.get('conversation.input.left');
assert.equal(trigger().props.children.type, 'svg', 'attachment trigger must not duplicate upstream Commands plus icon');
const panel = registrations.get('conversation.input.dock');
assert.equal(panel(), null, 'panel closed initially');
trigger().props.onClick();
let shown = panel();
assert.equal(shown.props['data-dshm-attachment-panel'], '');
const buttons = shown.props.children[0].props.children;
assert.equal(buttons.length, 3, 'three equal source tiles');
assert.ok(buttons.every(button => button.props.style.flex === '1 1 0'));
assert.ok(buttons.every(button => button.props.children[0].type === 'svg'), 'all three icons must render inline SVG rather than unsupported emoji glyphs');
assert.deepEqual(Array.from(buttons, button => button.props['data-dshm-attachment-source']), ['camera','album','file']);
buttons[0].props.onClick();
assert.equal(panel(), null);
assert.deepEqual(clickStates[0], { accept:'image/*', capture:'environment', multiple:false });
input.emit('cancel');
assert.equal(input.getAttribute('capture'), null, 'cancel restores upstream capture');
assert.equal(input.getAttribute('accept'), null, 'cancel restores upstream accept');
assert.equal(input.multiple, true, 'cancel restores upstream multiple');
trigger().props.onClick();
shown = panel();
shown.props.children[0].props.children[1].props.onClick();
assert.deepEqual(clickStates[1], { accept:'image/*', capture:null, multiple:true });
input.emit('change');
assert.equal(input.getAttribute('accept'), null, 'selection restores upstream accept');
trigger().props.onClick();
shown = panel();
shown.props.children[0].props.children[2].props.onClick();
assert.deepEqual(clickStates[2], { accept:'', capture:null, multiple:true });
inputActive = false;
trigger().props.onClick();
shown = panel();
shown.props.children[0].props.children[0].props.onClick();
assert.equal(clickStates.length, 3, 'missing upstream input must not fake an upload');
inputActive = true;
for (const stop of disposal.reverse()) stop?.();
assert.equal(input.getAttribute('capture'), null);
assert.equal(input.getAttribute('accept'), null);
assert.equal(input.multiple, true);
assert.equal(context.style, null, 'disabling plugin restores upstream paperclip');
console.log('mobile-attachment-sources: PASS slots,open,3-actions,real-input-attrs,missing-input,unload');
