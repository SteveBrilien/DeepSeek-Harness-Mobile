# ADR 0008 — Adapt DSH Web through a Cordis mobile layout plugin

Status: Accepted for the 0.3.0 alpha line.

## Context

The official DeepSeek Harness Web Client is still desktop-first in the 0.1 series. On narrow Android WebView widths the desktop rail/sidebar and fixed-width layout can leave the conversation/composer occupying only a small portion of the viewport. Android-side `evaluateJavascript()` CSS overrides had also accumulated build-specific selectors and made the app responsible for reimplementing upstream UI details.

Upstream discussions and community work show the same class of problems: collapsible mobile sidebars, full-width chat/composer, touch targets, settings layout and phone viewport behavior. We evaluated two Cordis-oriented community approaches:

- `Canary-Builds/dsh-mobile-ui`: useful reference for a fullscreen/drawer model, but its own documentation warns that some selectors are tied to specific generated CSS hashes/builds.
- `GithungDang/dsh-client-ui-mobile`: additive Cordis client plugin for the Harness 0.1 series. It registers against DSH layout/slot APIs, hides the desktop rail on narrow screens, turns the built-in sidebar into an overlay drawer, expands touch targets and adapts settings/tool rows while leaving desktop widths unchanged.

The DSH frontend already includes a standard mobile viewport meta tag. Therefore the solution should not be a WebView zoom trick that merely shrinks the desktop interface.

## Decision

1. Keep the official DSH Web Client as the application home/chat surface.
2. Remove the Android-side cosmetic DOM/CSS injection layer.
3. Explicitly keep WebView at the page viewport (`useWideViewPort=true`, `loadWithOverviewMode=false`, `textZoom=100`) and let DSH own document scale.
4. Vendor the published `dsh-client-ui-mobile` 0.1.9 runtime package verbatim under its MIT license and add it to the DSH `web` profile through DSH's supported plugin mechanism.
5. Include the plugin in the prevalidated embedded web-profile seed so a fresh install does not need a registry transaction for mobile layout.
6. Existing runtimes receive the local package on the next DSH start and retain their official DSH profile semantics.
7. DSH/Runtime upgrades must validate that the mobile plugin still loads and that `dsh web` reaches authenticated ready state before an A/B slot is promoted.

## Consequences

- The mobile layout tracks DSH concepts rather than duplicating them in Android.
- WebView fills the native home content area; mobile responsiveness is handled inside the DSH plugin system.
- There is a compatibility dependency on DSH 0.1 client layout conventions. If upstream changes CSS-module naming or slot/layout contracts, the plugin must be upgraded or temporarily disabled rather than patched ad hoc in `ChatScreen`.
- Native Android pages (workspace, terminal, settings, recovery) still follow the approved DSH visual baseline but remain native surfaces.

## Supply-chain controls

The vendored package version, license and file SHA-256 values are recorded in `THIRD_PARTY_NOTICES.md`. The runtime E2E suite verifies package presence/version and an authenticated `dsh web` startup with the mobile profile enabled.
