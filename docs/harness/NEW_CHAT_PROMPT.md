# New Codex chat: H01 only

```text
Continue FirstStep using its existing implementation harness. Implement only H01
(replaceable UI and game foundation), then stop and report in Korean.

Repository: C:\Users\InsuHam\FirstStep
Remote: https://github.com/InsuHam0315/Mobile-FirstStep.git
Starting branch: main. Inspect current local/remote state; preserve newer work.
Do not reset, force-push, overwrite worktrees, or replay the previous chat.

Read AGENTS.md; docs/harness/{README,ROUTING,UI_BOUNDARIES,NOTION_WORKFLOW}.md;
docs/harness/tasks.json; docs/harness/notion.json; and the H01 task packet.
Use PROMPTS.md for bounded specialist dispatches. Read other files only as needed.

Use the requested hierarchy: Astra coordinator, gpt-6.1-sol PMs,
gpt-6.1-terra implementation/verification specialists, and gpt-6-luna for narrow
read-only exploration. The root model is selected by the session/user, not by a
repo file. Check actual availability; never invent gpt-6.1-luna or claim profiles
ran merely because TOML files exist. Disclose unavailable roles before substitution.
Use short English internal prompts, technical records and handoffs; Korean app copy
and final report. Prefer coordinator + one PM + one leaf within available slots.

Keep ongoing work in the EXISTING Notion board inside AI기반모바일프로그래밍:
https://www.notion.so/410ed980d97e801dbd3ce4f854c92a90
H01: https://www.notion.so/410ed980d97e8112b811d8591461a8c2
Fetch its current schema and row. Follow NOTION_WORKFLOW.md. Only the coordinator
updates the row at actual start, meaningful blockers and verified completion.
No duplicate board/tasks. Record scope, branch/worktree/base/candidate, checks,
review, device limits and integration status. Notion failure must leave a local
pending-sync record and be disclosed; it must not become a fabricated success.

Check the harness and H01 readiness. From a clean reviewed main checkout, run
python scripts/harness.py new-worktree H01 --dry-run, then create the worktree.
If the task branch/worktree exists, inspect and resume it safely. Create isolation
BEFORE marking the manifest in_progress; maintain task state in the task worktree.
Assign explicit write ownership and tell every worker to preserve others' edits.
Escalate shared files through the PM; the coordinator owns those changes.

H01 scope: Java/XML only. Add UI state/actions, domain and adapter interfaces,
resource tokens and a minimal fake-backed preview shell. Presentation must not
own game rules, storage, clocks or sensors. Prove the shell works without camera,
GPS or network and that styling can change without behavior changes. Keep the
legacy app runnable. Do not implement H02-H09, Firebase, login, RAG, migrations,
new sensor flows or a final redesign. Pending product choices remain pending.

Use meaningful contract/behavior tests, then testDebugUnitTest, assembleDebug and
lintDebug with Android Studio's bundled JDK (scripts/verify.ps1). Independently
review the exact patch; repair failures before completion. For the preview, capture
real APK UI where runtime execution is available and label fake input explicitly.
Never claim unavailable emulator/device/field checks passed.

Write docs/harness/handoffs/H01.md, reconcile tasks.json and Notion, and commit
only the authorized H01 changes on codex/game-foundation. Do not merge into main,
push, or begin H02 in this run. Distinguish verified local work from integration.
Final Korean report: implemented behavior, branch/worktree/commit, exact checks and
review verdict, Notion link/sync result, remaining limitations and next bounded step.
```
