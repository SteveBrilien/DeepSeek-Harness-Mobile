# ADR 0011 — Separate minimal WebView compatibility from mobile UI adaptation

Status: Accepted for 0.4.0-preview.1.

## Evidence

ADR 0010 removed `dsh-client-ui-mobile` from the active Web profile and true-device testing then loaded the official DSH Web Client without any mobile-layout overrides. The result isolated the failure: Android WebView had a healthy 1080×2010 native surface and a 360×670 JavaScript viewport; DSH completed loading with hundreds of DOM elements and no plugin-loading state, but `html`, `body`, and `#root` all computed to 0 px height. Therefore the outer collapse exists independently of the previous mobile UI rules.

The earlier narrow-strip failures in secondary DSH screens remain a separate concern and must not be addressed while establishing the rendering baseline.

## Decision

1. Keep `dsh-client-ui-mobile` dormant. It remains absent from active dependencies, bundles, and profile `node_modules`.
2. Add a separate package, `@dsh-mobile/dsh-webview-compat` 0.1.0, whose only browser responsibility is the Android-WebView viewport root contract.
3. The compatibility plugin activates only when the user agent identifies Android WebView (`Android` plus the `wv` token).
4. It owns only `height`, `min-height`, and `max-height` on `html`, `body`, and `#root`, setting them to `100dvh`, `100dvh`, and `none` with inline-important priority. It observes a late-created `#root`, snapshots previous inline values, and restores only values it still owns when the effect is disposed.
5. It must not alter DSH frame columns, sidebar/details visibility, dialogs, settings, composer, tool rows, touch targets, typography, or other visual layout.
6. The active profile mode becomes `dsh-webview-compat-v1`; the mode and compatibility-plugin version participate in the Web presentation generation so stale WebView resource cache is invalidated once after an APK/profile change.
7. The acceptance target is now the official DSH layout plus this root-only compatibility contract. Once conversation, navigation, models, plugins, settings/dialogs, workspace controls, and composer interactions are stable, mobile UI work begins from zero in a separate browser plugin.

## Consequences

The app can distinguish platform compatibility from responsive redesign. A failure with this plugin active is no longer attributable to the broad mobile CSS previously used by `dsh-client-ui-mobile`. The official DSH page may still look desktop-oriented or overflow on a phone; that is acceptable for the functional baseline. Any later responsive change must be incremental and must keep the baseline interaction suite green.
