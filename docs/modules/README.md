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
| [app-entry](app-entry/README.md) | Confirmed account Home to Campus Home; process-scoped account access | Account Home → Campus Home and readback caller included in final31 actualActivity checks; synthetic identity |
| [campus-content](campus-content/README.md) | Sourced guide/DEMO, map/facility navigation, separate authored host | Authored5b961de integrated167923d; final syntheticActivity/SQLite suite PASS; production targets closed |
| [progress-sync](progress-sync/README.md) | Account guard, durable authored progress/outbox, backend and recovery | Core/candidate rules integrated;14 historical rules cases +1 actualAndroidSDK local scenario PASS; production unchanged |
| [home-progress](home-progress/README.md) | Account Home, reminders and facility/map bridge | 6c1e05e countdown repair integrated; finalHome/facility runtime and2 reminder permission/receiver checks PASS |
| [reskin](reskin/README.md) | Latest teammate visuals on actual consumers | 4303c1c integrated7cd25b9; portrait/large-landscape repaired and visually verified |
| [validation](validation/README.md) | Combined actualActivity/storage/local SDK checks | e4b3453 integrated7cd25b9; exact evidence and remaining limits in final review |

Page paths follow `docs/modules/<module>/README.md`; entries marked being added are not existing documentation or completed implementation claims. Historical detailed evidence remains in `docs/harness/handoffs/` and `docs/harness/reviews/`; link to it rather than duplicating entire histories.

## Required page contents

Describe current responsibility and exclusions, actual public signatures and source paths, input/output validation, dependencies, a caller example grounded in code, storage/account/dataset ownership, implementation state, exact tested versus pending behavior, and integration points. Separate proposed APIs from implemented APIs. Update caller and callee pages when a contract changes.

Current shared constraints: Java/XML; no silent DEMO/fixture/anonymous-to-account migration; no fabricated campus coordinates; camera overlay is not tracked AR. Identity-profile success does not imply cloud progress success. Current implementation-first/design-last/combined-validation order is in `../harness/IMPLEMENTATION_FIRST.md`.

## Final combined checkpoint7cd25b9

Required implemented V1 scope and latest presentation are integrated and reviewed. 233 JVM tests,31 actualActivity/storage cases,4 visual cases in portrait and130% landscape,1 real Android Firebase SDK local scenario and2 notification permission/direct-receiver cases passed. Final lint has0errors/107warnings. Repeated runs are not extra unique tests. See [exact review and evidence](../harness/reviews/FINAL_V1-management.md).

The unconfigured QA APK and the separately built Google-configured user-preview APK are different artifacts. Live login on the latter remains a user check. Production authored registry/coordinates and rule activation remain closed; no synthetic progress is promoted. User-held5564/5572/5580 were unchanged. Notification delivery after elapsed alarms/taps/process death and campus physical hardware were not established.

## Functional checkpoint167923d

Authored NORMAL GPS/discovery/intro/awayquiz, explicit sync/readback/reminders, account Home progress and facility/map selection are implemented. All three management sourcefindings are closed. Isolatedcombined43focusedJVM/APK/Androidtestcompilation PASS with52executedtasks. Source-definedversionedapprovedcontent exists; productionregistry and surveyed target provider remainempty. Finaldesignandcombinedruntime under RESKIN_SCOPE and FINAL_VALIDATION_PLAN. Nothing here claims fieldarrival, livecloudor protecteddeviceinstallation.

## Historical checkpoint behavior (preserved evidence)

`IdentityActivity` checks current `SessionState.Stage.COMPLETE` and opens `CampusHomeRoute.intent(this)` without account/storage extras. Campus Home offers the map, facility directory and department guide. The guide exposes its registered debug-only local DEMO separately. Earlier v1 direct-guide navigation and Back were verified on QA5572; that user-held installation remains unchanged. New v3 Campus Home navigation is built and source-reviewed, with runtime pending.

Combined checkpoint934d9b0 passed36 focused JVM tests, APK assembly, Android test compilation and lint (0 errors/95 warnings), all61 Gradle tasks executed. Sync is additive infrastructure, not live account/gameplay synchronization: its production registry remains closed and actual delivery/session binding is the next implementation slice. New-profile recovery, authenticated logout, service synchronization, field arrival and post-design validation remain distinct pending evidence. See [combined checkpoint record](../harness/reviews/ENTRY_SYNC-integration.md).

Later checkpoint5c180ca adds confirmed-owner events and durable authored source/reconciliation with47 focused tests/build/lint PASS. Graph checkpointbe936439 integrates atb240a03: actual IdentityViewModel -> confirmed-owner observer -> process-scoped named Firebase guard/access,52 focused tests/build/lint PASS in the candidate. Management verified identical app/build inputs on integration and did not rerun unchanged tests. The NORMAL gameplay host consumer is now an active implementation under [its scope](../harness/AUTHORED_HOST_SCOPE.md); it is not completed merely by the graph API. No production datasets or server rules activated.

Historical pre-integration continuation (superseded by167923d and final7cd25b9): exact authoredb2795f1 review found two P2s (quiz incorrectly requires continuing proximity; explicit send observer can be displaced). It is not integrated into main. Required GPS policy/adapters, NORMAL heading/SHAKE, explicit readback and intro hooks continue under AUTHORED_FLOW_CONTINUATION. Home summaries/reminders are a parallel caller module, and server rules are an isolated candidate under PROGRESS_RULES_SCOPE. Latest teammate design remains deferred; source/build checks are not actual device/cloud/field verification.
Team design integration `fa3f505` includes the welcome, guide, narration, discovery, booklet and quiz screens. See [design handoff](../harness/handoffs/Design.md) for the source and [integration checks](../harness/tasks.json) for the final emulator results.

