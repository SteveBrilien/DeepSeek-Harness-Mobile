# ADR 0010 — Prove the raw DSH Web baseline before mobile layout adaptation

Status: Accepted for 0.3.0-alpha.18.

## Context

True-device Android 11 testing produced two different failure classes while `dsh-client-ui-mobile` was active: the document root could collapse to zero height, and some secondary DSH surfaces could collapse to a narrow strip even when the main conversation had rendered successfully before. The mobile plugin contains broad responsive selectors for frame, sidebar, details, overlay, dialog, settings, composer and tool rows. That makes it difficult to determine whether a blank or collapsed surface is caused by Android WebView, the official DSH client, or our responsive overrides.

The local Runtime, Node.js process, authenticated `dsh web` endpoint and browser plugin runtime have already demonstrated successful startup. The next diagnostic milestone is therefore not another layout patch; it is a clean official-client baseline.

## Decision

1. The active `web` profile keeps `@dsh-mobile/dsh-mobile-context`, because it is non-visual integration metadata.
2. `dsh-client-ui-mobile` is removed from active `dependencies`, `dsh.profile.bundles`, and `profiles/web/node_modules` during reconciliation.
3. The APK may retain the mobile UI package as a dormant, versioned payload for later work, but dormant assets must not execute in the baseline.
4. The profile mode marker becomes `native-dsh-web-v2`. This mode participates in the presentation generation so Android WebView clears stale HTTP resource cache once when switching from the mobile-plugin profile.
5. Android does not inject CSS or mutate DSH DOM to make the baseline look mobile-friendly. The raw DSH page may overflow or look desktop-oriented; that is acceptable while validating completeness and interaction.
6. Baseline acceptance requires the official DSH Web Client to render and remain interactive through the important native DSH surfaces: home/conversation, session navigation, models, plugins, settings/dialogs, workspace selectors and composer interactions.
7. Only after baseline acceptance do we reintroduce mobile UI changes through a new minimal DSH/Cordis browser plugin. Adaptations are added incrementally with before/after interaction tests; broad global selectors are not restored wholesale.

## Consequences

- Blank-page and narrow-strip failures become attributable: if they remain with the mobile UI bundle absent, the problem is in WebView/official DSH/runtime integration; if they disappear, the previous responsive plugin caused or amplified them.
- The baseline is intentionally not a polished mobile UI.
- Existing user profile fields and unrelated bundles remain preserved; only the APK-managed mobile UI bundle is removed.
- `0.1.9-dshm.2` remains auditable in the APK but dormant, so later plugin redevelopment can reuse or replace it without pretending it is an upstream release.

## Follow-up mobile plugin policy

The next mobile plugin starts from zero visual overrides. Add one capability at a time in this order: safe-area/viewport evidence, navigation drawer, conversation/composer width, dialogs/settings, touch-target improvements. Every step must preserve the raw-baseline interaction suite before the next adaptation is added.

## Device result

The raw-profile experiment succeeded in removing the mobile UI package, but the official client still collapsed `html`, `body`, and `#root` to zero height in Android WebView while the JavaScript viewport remained 360×670. This rules out the broad mobile-layout rules as the cause of the outer root collapse. ADR 0011 therefore adds a separate root-only WebView compatibility plugin while keeping the mobile UI package dormant.
