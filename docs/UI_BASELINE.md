# UI Baseline V0.1

## Main navigation

```text
Chat | Projects | Files | Terminal | More
```

## Chat

- Host official DSH Web Client.
- Preserve DSH-native cards, thinking/tool calls, session behavior and plugin UI.
- Mobile compatibility layer only for real narrow-screen/touch/keyboard/safe-area/orientation problems.
- Input mention surface should unify Projects, Files and Sessions without breaking native DSH mention semantics.

## Projects

Project detail should eventually expose:
- path;
- Git status/remote/branch when applicable;
- Primary/Attached session relationships;
- recent Sessions;
- runtime target/profile;
- common build/run/test actions;
- Files / Terminal / Chat entry points;
- backup state.

## Files

Native surface. Required states:
- tree/list browsing;
- breadcrumb/path;
- selection mode;
- create/rename/copy/cut/paste/delete/move;
- Trash/history where applicable;
- preview/editor;
- storage/provider/permission context;
- explicit risk treatment for privileged system paths.

## Terminal

Terminal profile selector:
- Runtime;
- Android/ADB;
- Recovery;
- Root (when available).

Always show the active execution context to reduce accidental privileged commands.

## More / Recovery

Contains or links to:
- Runtime Health;
- Plugins;
- Backup/Restore;
- Updates;
- Privilege Provider;
- Logs/Diagnostics;
- Settings;
- Recovery Center.

## App-wide plugin UI

DSH Browser plugins remain scoped to the hosted Web Client. Optional Mobile Extensions may expose Android-native overlays/widgets on non-Chat screens. Shared plugin/core state should avoid duplicate monitoring logic.
