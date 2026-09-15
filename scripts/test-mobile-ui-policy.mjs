import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(process.cwd(), 'core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile');
const manifest = JSON.parse(await readFile(resolve(root, 'package.json'), 'utf8'));
const client = await readFile(resolve(root, 'lib/client.js'), 'utf8');

assert.equal(manifest.name, 'dsh-client-ui-mobile');
assert.equal(manifest.version, '0.2.0-dshm.1');
assert.ok(manifest.dsh?.client, 'mobile UI must remain a DSH client plugin');
assert.ok(client.includes('data-shell-overlay'), 'shell discovery must use the semantic overlay marker');
assert.ok(client.includes('data-dshm-shell'), 'shell must be explicitly tagged before styling');
assert.ok(client.includes('data-dshm-settings-panel'), 'settings must be explicitly tagged before styling');
assert.ok(client.includes('aria-expanded'), 'mobile synchronization must observe expanded/collapsed interaction state');
assert.ok(client.includes('data-dshm-sidebar-search'), 'sidebar search must be semantically tagged before responsive styling');
assert.ok(client.includes('role=\\"dialog\\"') || client.includes("role=\"dialog\""), 'settings discovery must be dialog-scoped');
assert.ok(client.includes('data-dsh-mobile-ui'), 'all mobile rules must be gated by the mobile root marker');

for (const pattern of [/\[class\$=/, /\[class\*=/, /\[class\^=/]) {
  assert.equal(pattern.test(client), false, `broad CSS-module selector forbidden: ${pattern}`);
}
for (const unit of ['100vh', '100dvh', '100svh', '100lvh']) {
  assert.equal(client.includes(unit), false, `viewport unit forbidden in mobile UI layer: ${unit}`);
}
assert.equal(client.includes('html,body,#root'), false, 'mobile UI must not own the root viewport contract');

console.log('mobile-ui-policy: PASS');
console.log(`version=${manifest.version}`);
console.log('scope=shell,settings');
