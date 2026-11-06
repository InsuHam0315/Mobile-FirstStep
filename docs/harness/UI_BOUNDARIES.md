# Replaceable UI boundaries

Status: **planned architecture**, not a completed refactor or implemented game.

## Authority and scope

[README.md](README.md) and [tasks.json](tasks.json) define H01-H09 branches, dependencies, decisions, and file ownership. This guide adds boundary rules; it creates no alternate schedule or write scope. Read only the authorized task packet and relevant code.

The current app is Activity-centric: Activities render Java/XML Views and coordinate substantial application/device work. The new `game` packages below are a target structure. Keep Java/XML and the existing `app` module; use logical packages before adding Gradle modules. Do not move legacy code wholesale to make a diagram true.

Firebase/Auth, Firestore, cloud photos, RAG/model choices, and legacy-feature retention remain pending in the task board. No UI task implicitly selects or enables them.

## Target packages and dependency direction

All paths below are under `com.example.firststep.game`; the task board remains the ownership authority.

| Package | Responsibility |
|---|---|
| `game.ui.<screen>` | Activity/View binder, rendering, accessibility, action forwarding |
| `game.presentation` | Immutable screen state, actions, coordinator, explicit effects |
| `game.domain` | Stable models, pure game policies, repository/device/clock ports |
| `game.data.local` | Versioned progress, checkpoints, and local storage adapters |
| `game.device` | Camera/orientation/summon adapters and cancellable device sessions |
| `game.composition` | Contract-to-adapter wiring; no game rules |
| `game.speech`, `game.notifications` | H04 platform adapters behind relevant contracts |
| `game.auth`, `game.data.remote` | H07 only after explicit selections |
| `game.chat`, `game.ui.chat` | H08 only after explicit selection |

```text
Java/XML UI -> screen state/actions coordinator -> pure domain + ports
                                                   ^ implementations
                          local / device / optional selected adapters
```

Domain and port contracts must not import Android Views/Context, Room, CameraX, HTTP, Firebase, or model SDKs. Reuse existing implementations through bounded adapters when appropriate. H01 establishes shared contracts; later tasks request coordinator-approved contract changes rather than editing another task's ownership silently.

## State, actions, effects

Use Java 11 final POJOs, interfaces, enums, and explicit callback/threading contracts. A new reactive framework is unnecessary.

- **State:** immutable renderable values, stable IDs, stage, loading/error status, available actions, and semantic messages. No widgets, credentials, or live device objects.
- **Actions:** intent such as `OpenBuilding(buildingId)`, `AnswerSelected(optionId)`, or `SummonRequested`. Send identity, not list position or localized label.
- **Effects:** one-time navigation, permission, speech, notification, or device requests. Rendering the same state must not repeat them.
- **Results:** carry request IDs and applicable record-version/session context. Reject canceled, superseded, or stale callbacks; include account/reset context only where the selected feature requires it.
- Adapters perform blocking work off the main thread; presentation delivers state on the main thread. The coordinator orchestrates policies without duplicating them.

Views may format values, bind resources, forward events, host platform callbacks, and retain transient focus/scroll state. XML/View code must not query storage, evaluate answers/eligibility, award stamps, calculate sensor thresholds, or decide file ownership. A disabled button is feedback, not policy enforcement.

## Legacy versus new-game behavior

Unchanged legacy screens retain their current contracts until an authorized integration/migration task changes them: ordered missions, existing IDs, account/mode isolation, checkpoint/photo ownership, and truthful actual/fallback labels. This is a compatibility limit, **not the new game's specification**.

H02 may introduce free-order per-building progression, new stable IDs, versioned records, and its approved migration/isolation policy. Do not copy legacy prerequisite or index-based gameplay dispatch into the new domain. Never reinterpret an old completion as a new-game stamp without a reviewed policy.

H03 defines CameraX preview/overlay discovery and summon adapters. An overlay may replace the old discovery experience under its authorized task; call it a camera overlay, never tracked AR. Retention/removal of legacy ARCore and optional photo capture remains a product decision. Do not make old AR, GPS, or photo gates mandatory for the new game by accident.

Core timing/scoring/free-order behavior is implemented only when its task's decision gates are satisfied. Reskin work cannot change those rules.

## Persistence and lifecycle

- H02 owns durable per-building progress, timestamps, quiz/retry rules, duplicate-reward protection, and versioning. Use a clock port; a notification callback is not the source of eligibility.
- Store resumable stage/draft metadata separately from completed progress. Reconcile pending writes before showing a durable reward; do not promise lossless recovery of every input.
- On pause/exit, cancel device sessions and speech as appropriate. Resume with fresh observations; restored UI state must not assert old arrival or sensor success.
- Rendering cannot capture, save, restart a timer, award a stamp, or replay speech. Explicit actions/effects own those operations.
- Use request IDs to reject callbacks after screen exit, recreation, or a replacement request. Optional photo work must define draft ownership, retake/cancel cleanup, and protection of referenced files.
- Device absence, permission denial, unreliable/too-dark input, and interruption need recoverable states. Simulated input and real-device success remain distinct evidence.
- If H07 is selected, its account/offline/reset rules belong behind ports; do not bake a provider or bearer token into screen state.

## IDs, tokens, and reskin limits

- Give buildings, episodes, questions, options, and attempts stable IDs independent of wording and shuffle order. Keep versioned storage identity separate from display names.
- Use semantic resource IDs (`game_quiz_submit`, `game_map_buildings`) and stable row IDs for automation.
- Keep Korean copy in string resources, and palette/type/spacing/shapes in shared `game_*.xml` value resources. Use role-based names such as `game_color_surface` and `game_space_medium`, not a particular shade or asset name.
- Keep layouts under each task's `game_*.xml` ownership. Do not let a token cleanup become a global legacy resource rewrite.
- Preserve content descriptions, focus order, touch targets, contrast, large-font layout, scrolling, and system/IME insets.
- H09 changes presentation, tokens, and assets within its approved scope. If a visual request needs domain/device/storage changes, hand that requirement back for a separate task decision.

## Acceptance and handoff

Use the canonical task criteria; these are cross-cutting gates, not another backlog:

1. H01 can render with fake adapters without camera/GPS/network. Re-rendering has no duplicate effects; domain/port code remains Android-independent.
2. H02 verifies independent waiting buildings, persisted-clock eligibility, 2/3 scoring, stable shuffled answer identity, retries, duplicate rewards, and the chosen migration policy.
3. H03-H06 verify denial/absence/recovery, lifecycle/recreation, stale callback rejection, and one complete building before expanding to five. Late/denied notifications never block an eligible quiz.
4. A token/layout-only reskin leaves behavior tests unchanged. Check actual APK screens with small/large text, keyboard/insets, scrolling, and accessibility; label fake-adapter screenshots as simulated.
5. Preserve unchanged legacy flows until integration is authorized. Record real-device/campus checks separately; never mark coordinates verified from imagery or code.
6. Run task-relevant checks and the README's integration verification gate. This documentation task starts no builds, branches, devices, or services.

Handoffs use English and name task ID, owned paths, contract changes, evidence, and remaining limitations. Follow `tasks.json` for branches and dependencies; only the coordinator changes shared ownership/integration contracts. Do not revert other workers' edits.
