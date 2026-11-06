# H06 one-target technical integration

Start only after management dispatches H06 from accepted H05 integration 4e7654fc88f49adb23dd6f4608d76a8ad9f0e91e and its current main planning descendant. Branch: integration. Korean commits [integration v1], v2, etc. Read AGENTS.md, ROUTING.md, CORE_GAME_SCOPE.md, PRODUCT_DECISIONS.md, UI_BOUNDARIES.md and tasks.json. Management owns acceptance, publication, main integration, Notion and future scope. Implementation owns this bounded code/test slice and English handoff only.

## Deliverable and honesty boundary

Connect one debug-accessible, explicitly simulated technical loop: H05 home target -> simulated arrival -> H03 discovery/summon -> H04 introduction -> waiting/quiz/retry/result -> authoritative H05 refresh. Target is exactly digital_information_software, the selected future digital-information/software content target. Existing scripts/questions are fixtures, not verified school facts. This slice completes technical navigation, not authored campus content or the whole production game.

Keep Java/XML presentation replaceable. Reuse accepted H02 timing/scoring/reward/storage rules and H03/H04 device/presentation adapters. NORMAL retains 15-minute eligibility; DEMO is explicit instant eligibility. Notifications are not eligibility authority. One completed target must leave the five-destination ending locked. Do not implement five-target expansion, real arrival coordinates, new school scripts/questions, Firebase/Google/profile onboarding, cloud/RAG, legacy photo/ARCore migration, or final reskin. Those require separate evidence/scope decisions. Preserve existing release and prototype entry points/data.

## Context and storage isolation contract

Use an immutable validated integration context: datasetId=h04_fixture_v1, runId=canonical UUID, targetId=digital_information_software, mode=NORMAL|DEMO, clock=REAL|explicitly labeled DEBUG_FAKE. Reuse H04's game_intro_debug_run contract and h04-debug-<UUID>.db so home/intro/quiz in the same integration run read the same fixture records. Require this valid context for the H06 route; missing/invalid context must not silently fall back into the default progress database. Validate at boundaries. Do not accept arbitrary path/database names from Intent extras.

Generate and retain a dedicated run context for a usable debug entry; reopening/restarting that entry must resume the same run, not silently mint an empty run each launch. Automated tests may supply separate explicit UUIDs. Persist navigation context safely across recreation/process restart, distinguishing pending discovery from accepted result. Keep NORMAL/DEMO isolation. NORMAL describes timing, not authenticity: all H06 progress remains visibly example progress even under real-time timing.

Do not reset/copy/import/promote firststep-game-progress.db, previous fixture databases, or legacy records. Future authored content must use a separate trusted storage namespace/composition; H06 does not create or enable it. Reusing target IDs or changing GameContent.version never transfers fixture timestamps/stamps to authored progress. This fixture namespace choice is technical isolation, not a product decision to migrate real users. No H02 schema/reward change is required.

## Route and result contract

Capture target, mode and run identity before discovery. Bind DiscoveryIntroTarget/result to the pending request, preserve discovery method/source/fallback and arrivalSimulated=true, and reject wrong target (including synthetic_cover_demo), mismatched run/mode, malformed, stale, duplicate or canceled results. Keep provenance in the integration navigation state; do not present transient navigation validation as persisted GPS evidence or account security.

Discovery completion needs an explicit once-only continuation. Summoning alone never writes introDoneAt or grants a stamp. Canceled discovery returns safely without advancing progress. Use existing H04 typed result/host extras where possible; additive bounded context plumbing is authorized. After introduction completion, waiting/eligible quiz can be reopened from home without renewed arrival/discovery, including away from the building. Returning from quiz/result refreshes stored state and preserves selection. Failure retry and repeated callbacks must not duplicate rewards or postpone eligibility.

## Write ownership

New game/composition/integration and focused presentation/context contracts; bounded H05 GameHomeActivity/HomeHost navigation and H03 DiscoveryActivity completion/result handling; narrowly additive H04 host/intro context plumbing as needed; debug manifest/entry wiring; small game_integration resources; focused unit/instrumentation tests and verification runner. Existing shared files are owned by the implementation lead for this slice. Workers are not alone: preserve others' edits and coordinate shared paths. Preserve H01-H05 accepted behavior, map attribution/touch fixes, favorite atomicity, public APIs, assets and databases. Report substantive new shared-rule requirements instead of silently widening scope.

## Acceptance and evidence

- Real API35 APK traverses the shared-run loop, shows simulated arrival and fixture content, and returns to refreshed home with exactly one stamp and locked ending.
- Cancellation, wrong target/context, malformed/stale/duplicate result, recreation and process restart are tested. No pending discovery becomes an intro completion by itself.
- NORMAL eligibility boundary is tested with the existing injectable clock; actual fake-time input is labeled, never described as a real 15-minute wait. DEMO path, failed quiz/retry, quiz re-entry and mode isolation are covered.
- Default/legacy databases and previous fixture runs are preserved. Tests demonstrate no silent default-database fallback/import and same-run persistence, without deleting shared data to manufacture screenshots.
- Camera/sensor absence or denial remains recoverable through H03's explicit fallback. Camera overlay is not tracked AR. Screenshot claims distinguish actual APK rendering from simulated inputs.
- Run scripts/verify.ps1 with Studio JDK/SDK, focused instrumentation and independent exact-patch review. Include portrait/large-font landscape and back/recreation where navigation changed. Preserve command logs, before/after invariants and relevant captures under .local/harness/H06.

Report exact committed SHA/hash, checks/counts/cached-vs-fresh tasks, actual agents, failures/fixes and limits in docs/harness/handoffs/H06.md. Only update H06 factual status in tasks.json. Notify management at meaningful tested commits for branch publication. Do not push/merge, write Notion, begin H07/H08/H09 or expand to real/multiple campus content. Stop at H06 technical handoff; authored content and expansion remain explicit follow-up gates.
