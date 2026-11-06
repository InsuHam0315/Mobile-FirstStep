# FirstStep Codex implementation harness

Status (2026-11-06): H01 feature implementation is awaiting a test-isolation repair and management acceptance; it is not integrated. The full new game is not implemented.

## Start here

For module work after the currently running H01, also read `BRANCH_COMMITS.md` for stable module branch names and versioned commit subjects. H01 is exempt from this prospective naming change.

1. Read this file and `tasks.json`; select one authorized task, not the whole backlog.
2. Read `ROUTING.md` and `NOTION_WORKFLOW.md`, then only its task packet and relevant code. Use `PROMPTS.md` for short dispatches; `NEW_CHAT_PROMPT.md` is the H01 entry prompt.
3. Use the reviewed baseline SHA in `tasks.json`. If new uncommitted app work exists, review/checkpoint it and advance the baseline before branching. Do not silently omit facilities, camera, or map improvements.
4. Create the task branch/worktree using `scripts/harness.py new-worktree TASK_ID`. The command fails on dirty checkouts, inactive tasks, missing dependencies, or an unrecorded baseline.
5. Implement within ownership and run relevant checks with independent review. In two-chat mode, the implementation lead owns scoped shared app/build/manifest changes and the active task's factual status inside its feature worktree. Management owns project-wide decisions and integration; follow ROUTING.md and INTEGRATION.md. The user authorizes management to integrate and push a passing module and advance sequentially without routine approval.
6. The implementation chat records an English evidence handoff; management reviews it, integrates under INTEGRATION.md and writes the Korean branch journal using `notion.json`. Implementation agents must not write Notion. Create at most one journal per actual branch, not one page per task. A task is complete only when its acceptance criteria and required checks pass; preserve unresolved device/field limitations. Integration and next-module dispatch require passed gates, not another routine user approval.

No continuous background agent or paid API service is started by these files. Native `.codex/agents/*.toml` profiles supply role instructions when a supporting Codex session loads them. Current-session dispatch can instead use the built-in collaboration tools with the same model and task instructions. Profile discovery/model access must be checked in the actual session; parsing TOML alone does not establish runtime loading.

## Language and token budget discipline

- Internal prompts, code identifiers, repository technical docs, task packets, reviews, and handoffs: English. Notion branch journals are the explicit exception: readable, substantive Korean development narratives for the user and team, following `NOTION_WORKFLOW.md`.
- App text, content, TTS scripts, and final user reports: Korean.
- English is a preference, not a guaranteed token saving. Reduce context by sending paths + contracts + acceptance criteria rather than replaying the full chat/PDF.
- Specialists receive a fresh bounded context. Reuse a specialist for related follow-ups; do not duplicate an already-completed exploration.
- Default: coordinator + one active PM + one specialist. Add a second specialist only for independent, non-overlapping work. Stop idle branches of delegation.
- Specialist handoffs: approximately 200 words, plus essential evidence/paths. No repeated summaries or raw logs in prompts; keep full logs under ignored `.local/harness/`.

## Baseline and product decisions

The 2026-11-06 game PDF is a design input, not permission to delete working code. The requested plan is a staged rebuild inside the existing repository, retaining Java/XML and reusable device code.

Candidate core: five destination episodes, free order, fairy discovery/summoning, TTS + captions, per-building quiz eligibility after 15 minutes, three-question quizzes, stamps, ending. It is not a shipped implementation.

Pending explicit selections: legacy facility directory/favorites, photo capture as an optional feature, real-map fallback, legacy ARCore, Google login/Firebase Auth, Firestore, cloud photos, Gemini RAG, school identity, old backend retention, mandatory student ID. The PDF excludes servers while earlier discussion requested Firebase/chat. Do not resolve that conflict by silently enabling either architecture.

The initial harness branch is `codex/game-harness`. At the user's explicit request, pre-existing app edits and the harness were checkpointed together in `6dc8af1` and included on `main`. The task board records that baseline; feature worktrees must descend from it. Pushes are performed only within user authorization, not automatically by task scripts.

## Planned branch sequence

| Task | Branch | Delivery | Depends on |
|---|---|---|---|
| H00 | codex/game-harness | Roles, packets, boundaries, safe worktree tooling | none |
| H01 | codex/game-foundation | UI state/actions, domain/adapter contracts, resource tokens, minimal shell | H00 + baseline |
| H02 | progress | Local per-building progress, clock rules, quiz rules, migration policy | H01 + core selection |
| H03 | discovery | Camera preview, orientation, summon adapters, fallback | H02 |
| H04 | intro-quiz | TTS captions, quiz UI, delayed eligibility and notifications | H02 |
| H05 | map-home | Illustrated map, home, stamps, profile, ending | H02 |
| H06 | integration | One end-to-end building, then five, recovery and visual tests | H03-H05 |
| H07 | firebase | Optional identity/sync adapter | H06 + explicit cloud selection |
| H08 | chatbot | Optional sourced Gemini RAG feature/backend | H06 + explicit chatbot selection |
| H09 | reskin | Apply user references to presentation and assets | H06 + design references |

Create branches just in time from a reviewed integration commit. Do not create all branches from today's old HEAD. Implement one module at a time, integrating a passing module before the next eligible module. Reordering the planned sequence requires a documented scope decision; a reskin must pass the same behavior checks.

## Gates

- H00: manifest/profile checks; safe worktree command refusal/success scenarios. No app tests claimed.
- App code: targeted meaningful tests, then `scripts/verify.ps1` (unit tests, assembleDebug, lintDebug) on the exact integration SHA. New errors block completion; record existing warnings separately.
- Screen changes: real APK screenshots, small/large text, touch targets, scrolling, rotation/recreation, missing permissions/devices. A fake adapter screenshot must be labeled as simulated input.
- Timing: eligibility comes from persisted timestamps and a clock abstraction, not a notification callback. Delayed notifications do not lock an eligible quiz.
- Sensor/GPS/camera: repeatable simulated checks plus explicitly separate real-device and campus validation. Never mark coordinates verified from code or map imagery.
- Cloud: user selection, real configuration, rules/auth isolation tests, offline behavior, and evidence before claiming deployed sync/auth.
- Integration: coordinator reviews diff scope + reviewer verdict + evidence. No destructive cleanup or force push.

## Commands

Run with Python 3.11+ (standard library only), from any directory:

```text
python scripts/harness.py check
python scripts/harness.py status
python scripts/harness.py packet H01
python scripts/harness.py new-worktree H01 --dry-run
```

`packet` prints a short English packet and does not start an agent. `new-worktree` creates Git isolation only, never starts an agent, copies credentials, or merges code. Set `ANDROID_HOME` or create ignored `local.properties` in the new worktree before an Android build. Use Android Studio's JDK with `scripts/verify.ps1`.

## Official references

- [Codex subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents): project role profiles and model instructions.
- [Codex worktrees](https://learn.chatgpt.com/docs/environments/git-worktrees): worktree behavior. Our script uses ordinary Git worktrees, not the desktop app's automatic uncommitted-change copying.

These files do not alter the user's global Codex configuration, credentials, permissions, or active root model.
