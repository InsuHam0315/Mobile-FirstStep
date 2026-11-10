# FirstStep

- Java application code and XML Android Views only; no Compose or Kotlin application code.
- Keep facility coordinates unverified until a field survey confirms them. Demo progress must remain separate from field progress.
- Validate with `gradlew.bat testDebugUnitTest assembleDebug lintDebug` using Android Studio's bundled JDK.
- Never describe camera overlays as tracked AR; ARCore and fallback discovery are separate modes.

## Staged game rebuild harness

- Read `docs/harness/README.md`, `docs/harness/tasks.json`, and only the selected task's references before new-game work.
- Use English for agent prompts, task records, reviews, and handoffs. Keep app copy Korean. Give the user concise Korean final reports; avoid routine narration unless required by higher-priority instructions or a material blocker.
- Work one authorized vertical slice at a time. Do not implement the entire backlog. Firebase, authentication, cloud photos, and Gemini RAG remain pending user selection.
- Keep replaceable Java/XML presentation separate from game rules, storage, clocks, and device APIs. Follow `docs/harness/UI_BOUNDARIES.md`.
- Use the requested coordinator -> Sol PM -> Terra/Luna specialist hierarchy only for bounded useful work. Read `docs/harness/ROUTING.md`; do not silently invent model IDs.
- Assign one branch/worktree and an explicit write scope per implementation task. Workers are not alone: preserve others' edits, and request a PM-owned change for shared files.
- New Git worktrees do not include uncommitted edits. Use the recorded reviewed baseline in the task board, checkpoint any later pre-existing app changes before branching, and never reset/stash/delete them automatically.
- A written agent profile is configuration, not evidence that the role ran. Record the actual model, base SHA, commands, outcomes, and remaining limits in each handoff.
