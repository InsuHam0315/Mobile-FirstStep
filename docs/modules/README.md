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
| [app-entry](app-entry/README.md) | Confirmed account Home to Campus Home; process-scoped account access | Identity owner;be936439 graph integratedb240a03, device/service timing pending |
| [campus-content](campus-content/README.md) | Sourced guide/DEMO, map/facility navigation, separate authored host | Navigation7a8a3ce integrated02eadec; gated NORMAL host actively implementing under AUTHORED_HOST_SCOPE |
| [progress-sync](progress-sync/README.md) | Account guard, durable authored progress/outbox, backend and recovery | b996ac9 integrated5c180ca; actual host consumer active in campus-content, live activation/runtime pending |

Page paths follow `docs/modules/<module>/README.md`; entries marked being added are not existing documentation or completed implementation claims. Historical detailed evidence remains in `docs/harness/handoffs/` and `docs/harness/reviews/`; link to it rather than duplicating entire histories.

## Required page contents

Describe current responsibility and exclusions, actual public signatures and source paths, input/output validation, dependencies, a caller example grounded in code, storage/account/dataset ownership, implementation state, exact tested versus pending behavior, and integration points. Separate proposed APIs from implemented APIs. Update caller and callee pages when a contract changes.

Current shared constraints: Java/XML; no silent DEMO/fixture/anonymous-to-account migration; no fabricated campus coordinates; camera overlay is not tracked AR. Identity-profile success does not imply cloud progress success. Current implementation-first/design-last/combined-validation order is in `../harness/IMPLEMENTATION_FIRST.md`.

## Immediate integrated behavior

`IdentityActivity` checks current `SessionState.Stage.COMPLETE` and opens `CampusHomeRoute.intent(this)` without account/storage extras. Campus Home offers the map, facility directory and department guide. The guide exposes its registered debug-only local DEMO separately. Earlier v1 direct-guide navigation and Back were verified on QA5572; that user-held installation remains unchanged. New v3 Campus Home navigation is built and source-reviewed, with runtime pending.

Combined checkpoint934d9b0 passed36 focused JVM tests, APK assembly, Android test compilation and lint (0 errors/95 warnings), all61 Gradle tasks executed. Sync is additive infrastructure, not live account/gameplay synchronization: its production registry remains closed and actual delivery/session binding is the next implementation slice. New-profile recovery, authenticated logout, service synchronization, field arrival and post-design validation remain distinct pending evidence. See [combined checkpoint record](../harness/reviews/ENTRY_SYNC-integration.md).

Later checkpoint5c180ca adds confirmed-owner events and durable authored source/reconciliation with47 focused tests/build/lint PASS. Graph checkpointbe936439 integrates atb240a03: actual IdentityViewModel -> confirmed-owner observer -> process-scoped named Firebase guard/access,52 focused tests/build/lint PASS in the candidate. Management verified identical app/build inputs on integration and did not rerun unchanged tests. The NORMAL gameplay host consumer is now an active implementation under [its scope](../harness/AUTHORED_HOST_SCOPE.md); it is not completed merely by the graph API. No production datasets or server rules activated.
