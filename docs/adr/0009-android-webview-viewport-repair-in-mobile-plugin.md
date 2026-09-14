# ADR 0009 — Keep Android WebView viewport repair inside the DSH mobile plugin

Status: Superseded for the raw-baseline phase by ADR 0010.

## Context

On the fixed Android 11 / OriginOS device, an authenticated `dsh web` document can finish loading with a healthy WebView viewport (`window.innerHeight` around 670–708 px at the tested sizes) while `html`, `body`, and `#root` all compute to `0px` high. The page is therefore present but visually collapsed.

During diagnosis, `ChatScreen` temporarily used `evaluateJavascript()` after page load to assign pixel heights to those DOM nodes. That proved the root cause and restored rendering, but it violates ADR 0008: Android would again own a cosmetic DOM/CSS repair tied to the current DSH document structure.

The vendored `dsh-client-ui-mobile` 0.1.9 package is already the approved narrow-screen adaptation layer and is loaded through the supported DSH/Cordis client plugin mechanism. A first local derivative placed the root-height contract only in its stylesheet. True-device validation then showed that other mobile-plugin rules were active while the root chain still computed to `0px`, demonstrating that a later DSH root style could win the cascade/load order.

## Decision

1. Remove viewport-mutating JavaScript from `ChatScreen`. Android may retain read-only diagnostics and lifecycle/back-navigation integration, but it does not rewrite DSH layout CSS or DOM geometry for normal presentation.
2. Keep the viewport repair in the mobile Cordis client plugin. Its narrow-screen stylesheet retains a `100dvh` fallback, and its client lifecycle establishes an explicit root viewport contract for widths up to 768 px: `html`, `body`, and `#root` receive inline-important `height: 100dvh`, `min-height: 100dvh`, and `max-height: none`.
3. The lifecycle effect snapshots the pre-existing inline values and restores them when the mobile breakpoint is left or the plugin effect is disposed. It also handles a late-created `#root`, so ownership is bounded and reversible rather than an untracked one-shot mutation.
4. Identify the package as the local derivative `0.1.9-dshm.2`, not as an invented upstream release. The `dshm.1` CSS-only experiment is superseded because device evidence showed it could lose the cascade.
5. Record changed package/client hashes and the exact local modification in `THIRD_PARTY_NOTICES.md`.
6. Reconcile the derivative through the same offline/versioned Web-profile mechanism as the rest of the mobile plugin. An APK update must replace stale managed copies atomically while preserving unrelated user profile fields/bundles.
7. If upstream ships an equivalent fix, prefer returning to an unmodified upstream package and remove the local derivative.

## Boundary

Android owns WebView lifecycle, local authentication, fatal renderer/network handling, native navigation, and diagnostic evidence. DSH/Cordis owns the DSH Web document and narrow-screen presentation. Read-only `evaluateJavascript()` probes are permitted for diagnostics; Android-side DOM/style mutation for normal presentation is not. A DOM/style mutation performed by the DSH/Cordis browser plugin itself is within the presentation layer and must remain scoped, reversible, and compatibility-tested.

## Consequences

The repair now follows the same ownership boundary as the rest of the mobile adaptation while surviving DSH styles that load after the plugin stylesheet. It no longer depends on delayed Android reinjection after WebView lifecycle events. The tradeoff is a small maintained downstream browser-plugin patch whose lifecycle and selector assumptions must be revalidated when DSH or the upstream mobile plugin changes.

This ADR amends ADR 0008's statement that the 0.1.9 runtime files are copied verbatim. All other ADR 0008 decisions remain in force.
