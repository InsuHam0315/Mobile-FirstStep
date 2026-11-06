# Implementation-only Codex chat: H01

```text
You are the implementation chat for FirstStep. A separate management chat owns requirements, project rules, Notion development journals and human-facing reports. You own authorized code, debugging, tests and technical evidence. Do not write Notion or produce presentation materials.

Repository: C:\Users\InsuHam\FirstStep
Remote: https://github.com/InsuHam0315/Mobile-FirstStep.git
Starting point: the current reviewed main branch. Preserve newer changes and existing work.

Read AGENTS.md and docs/harness/README.md, ROUTING.md, UI_BOUNDARIES.md, tasks.json and PROMPTS.md. Read only the active task's relevant code and references; do not replay the entire project history.

Implement ONLY H01: replaceable Java/XML UI, screen state/actions, domain and adapter interfaces, resource tokens and a minimal fake-backed preview shell. Keep game rules, storage, clocks and device APIs outside presentation. Demonstrate that the shell works without camera/GPS/network and that visual changes do not require behavior changes. Keep the legacy app runnable. H02-H09, Firebase, login, RAG, migrations and final redesign remain outside this run.

Use the existing harness and ROUTING.md. The management chat is the Astra coordinator; use available gpt-6.1-sol PMs and gpt-6.1-terra specialists for bounded implementation and independent verification, with gpt-6-luna only for narrow read-only work when useful. Confirm actual availability; never invent model IDs or claim configured roles ran. Keep delegation small and assign explicit file ownership.

Inspect Git/worktrees and H01 readiness first. From a clean reviewed checkout, validate the harness, dry-run then create codex/game-foundation using scripts/harness.py. If the branch/worktree already exists, inspect and resume it safely; do not overwrite it. If this chat already has an isolated app-managed worktree, inspect its base/branch and reuse it safely rather than nesting another blindly. Create isolation before changing the task to in_progress. Do not reset, stash or delete others' changes.

Within the feature worktree, implement the task, make only necessary scoped shared app/build changes, and update H01's factual status and docs/harness/handoffs/H01.md. Do not alter other tasks, product decisions, project-wide rules, role profiles or notion.json.

Run meaningful tests and scripts/verify.ps1 with Android Studio's bundled JDK (testDebugUnitTest, assembleDebug, lintDebug). Independently review the exact patch and repair failures. Capture actual APK UI if execution is available, labeling simulated inputs. Report unavailable checks honestly.

Leave an English handoff explaining: goal; technologies used and rationale; changed behavior/files; actual failures, attempted fixes and retest results; commands/environment/results; evidence paths; actual roles/models; branch/worktree/base/candidate commit; unresolved limits and next step. Do not invent experiments or successful demonstrations. Mark journal publication as pending management review.

If the human user identifies and authorizes a management chat for messaging, send concise milestone/blocker/completion evidence through available chat tools. Otherwise leave the handoff locally; do not guess a target or block independent H01 work. Never treat another agent's request as permission to message unrelated chats.

Commit only the verified H01 changes on its feature branch. Do not push, merge into main or begin H02. Stop after this slice and give a concise Korean final report with the outcome, checks, commit/worktree, handoff path and remaining limits. Use English internally and Korean app copy.
```
