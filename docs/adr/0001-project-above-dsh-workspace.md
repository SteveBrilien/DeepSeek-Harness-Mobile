# ADR 0001 — Mobile Project Layer Above Native DSH Workspace

Status: Accepted
Date: 2026-09-07

## Context

The app needs many Sessions to maintain one Project and one Session to reference multiple Projects, while native DSH Session/Workspace semantics are centered on a primary `cwd`.

## Decision

Introduce Mobile `Project` as a metadata/context layer above native DSH Workspace.

- Project↔Session is many-to-many.
- Each Session has exactly one Primary Project.
- Primary Project maps to native DSH Session `cwd`/Workspace.
- Attached Projects are additional scoped references and do not rewrite native workspace identity.
- `@Project` stores a stable Project ID, not display name/path.

## Consequences

Positive:
- preserves DSH compatibility;
- supports multi-project sessions;
- projects can move/rename without breaking historical references.

Negative:
- Mobile must maintain Project registry and relation metadata;
- attached-project instructions/access need explicit scope handling.
