# FirstStep module contracts

This index is management-owned. Each implementation owner maintains its own `docs/modules/<module>/README.md` alongside code changes. Read the relevant module page and current source before wiring a caller. Documents describe contracts; they do not automatically execute code or send messages between chats.

## Current ownership

| Module | Responsibility | Contract-page owner / state |
| --- | --- | --- |
| foundation | Replaceable Java/XML screen and port boundaries | Management verification PM, source-based page being added |
| progress | Local progress, timing, quiz scoring and reward rules | Management verification PM, source-based page being added |
| discovery | Camera overlay and explicitly labeled fallback discovery | Management verification PM, source-based page being added |
| intro-quiz | Narration, captions, quiz and result flow | Management verification PM, source-based page being added |
| map-home | Map, facility lookup and Home presentation | Management verification PM, source-based page being added |
| integration | Registered datasets, navigation and isolated compositions | Management verification PM, source-based page being added |
| firebase | Google identity and server-confirmed first profile | Management verification PM, source-based page being added |
| app-entry | Confirmed account Home to department guide | Original identity owner; implemented navigation checkpoint6fbb872, documentation continuation |
| campus-content | Sourced department guide, DEMO episode, independent campus navigation | Campus-content owner; H10C checkpoint55ba443 integrated, expanded navigation in progress |
| progress-sync | Account-scoped local outbox and backend sync adapters | Implementation PM / Terra owner; independent implementation in progress |

Page paths follow `docs/modules/<module>/README.md`; entries marked being added are not existing documentation or completed implementation claims. Historical detailed evidence remains in `docs/harness/handoffs/` and `docs/harness/reviews/`; link to it rather than duplicating entire histories.

## Required page contents

Describe current responsibility and exclusions, actual public signatures and source paths, input/output validation, dependencies, a caller example grounded in code, storage/account/dataset ownership, implementation state, exact tested versus pending behavior, and integration points. Separate proposed APIs from implemented APIs. Update caller and callee pages when a contract changes.

Current shared constraints: Java/XML; no silent DEMO/fixture/anonymous-to-account migration; no fabricated campus coordinates; camera overlay is not tracked AR. Identity-profile success does not imply cloud progress success. Current implementation-first/design-last/combined-validation order is in `../harness/IMPLEMENTATION_FIRST.md`.

## Immediate integrated behavior

`IdentityActivity` now checks current `SessionState.Stage.COMPLETE` and opens `GuideActivity` by an explicit Intent without account/storage extras. The guide exposes its registered debug-only local DEMO episode separately. This specific installed route and Back return were verified on QA5572. New registration recovery, authenticated logout, live progress sync, field arrival and complete post-design validation are separate pending evidence.
