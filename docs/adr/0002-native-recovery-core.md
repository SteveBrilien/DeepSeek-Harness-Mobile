# ADR 0002 — Native Recovery Core Must Not Depend on DSH Runtime

Status: Accepted
Date: 2026-09-07

## Context

A terminal or file manager implemented inside Debian/Node/DSH cannot repair that environment when the environment itself is broken.

## Decision

Implement File Manager, Text Editor, Recovery Terminal, Log Viewer, Backup/Restore, Runtime Repair and update bootstrap in the Android Native Core. They must remain available when DSH, Node, Debian or PRoot is missing/corrupt.

## Consequences

- Recovery functionality must keep a deliberately small dependency graph.
- Runtime repair commands and logs need native access paths.
- Some functionality may be duplicated at presentation level (for example DSH web terminal vs native Recovery Terminal), but state ownership must remain clear.
