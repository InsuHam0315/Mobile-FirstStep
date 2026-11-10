# FirstStep module contracts

This index is management-owned. Each implementation owner maintains its own `docs/modules/<module>/README.md` alongside code changes. Read the relevant module page and current source before wiring a caller. Documents describe contracts; they do not automatically execute code or send messages between chats.

## Current ownership

| Module | Responsibility | Contract-page owner / state |
| --- | --- | --- |
| [foundation](foundation/README.md) | Replaceable Java/XML screen and port boundaries | Source-based contract recorded; implementation evidence/limits in linked page |
| [progress](progress/README.md) | Local progress, timing, quiz scoring and reward rules | Source-based contract recorded; implementation evidence/limits in linked page |
| [discovery](discovery/README.md) | Camera overlay and explicitly labeled fallback discovery | Source-based contract recorded; implementation evidence/limits in linked page |
| [intro-quiz](intro-quiz/README.md) | Narration, captions, quiz and result flow | Source-based contract recorded; implementation evidence/limits in linked page |
| [map-home](map-home/README.md) | Map, facility lookup and Home presentation | Source-based contract recorded; implementation evidence/limits in linked page |
| [integration](integration/README.md) | Registered datasets, navigation and isolated compositions | Source-based contract recorded; implementation evidence/limits in linked page |
| [firebase](firebase/README.md) | Google identity and server-confirmed first profile | Source-based contract recorded; implementation evidence/limits in linked page |
| [app-entry](app-entry/README.md) | Confirmed account Home to department guide | Identity owner; implemented guide binding6fbb872, campus-home consumer update active |
| campus-content | Sourced department guide, DEMO episode, independent campus navigation | Campus-content owner; navigation7a8a3ce complete, assembled in app-entry8158893 for consumer checks |
| progress-sync | Account-scoped local outbox and backend sync adapters | Implementation PM / Terra owner; independent implementation in progress |

Page paths follow `docs/modules/<module>/README.md`; entries marked being added are not existing documentation or completed implementation claims. Historical detailed evidence remains in `docs/harness/handoffs/` and `docs/harness/reviews/`; link to it rather than duplicating entire histories.

## Required page contents

Describe current responsibility and exclusions, actual public signatures and source paths, input/output validation, dependencies, a caller example grounded in code, storage/account/dataset ownership, implementation state, exact tested versus pending behavior, and integration points. Separate proposed APIs from implemented APIs. Update caller and callee pages when a contract changes.

Current shared constraints: Java/XML; no silent DEMO/fixture/anonymous-to-account migration; no fabricated campus coordinates; camera overlay is not tracked AR. Identity-profile success does not imply cloud progress success. Current implementation-first/design-last/combined-validation order is in `../harness/IMPLEMENTATION_FIRST.md`.

## Immediate integrated behavior

`IdentityActivity` now checks current `SessionState.Stage.COMPLETE` and opens `GuideActivity` by an explicit Intent without account/storage extras. The guide exposes its registered debug-only local DEMO episode separately. This specific installed route and Back return were verified on QA5572. New registration recovery, authenticated logout, live progress sync, field arrival and complete post-design validation are separate pending evidence.
