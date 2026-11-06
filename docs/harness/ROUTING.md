# Role routing

Requested hierarchy:

```text
Coordinator: Astra (current chat's model is controlled by the user/session)
  Implementation PM: gpt-6.1-sol
    Implementer: gpt-6.1-terra
    Narrow explorer/content checker: Luna
  Verification PM: gpt-6.1-sol
    Independent verifier: gpt-6.1-terra
```

The user called the small model "6.1 Luna"; the exposed runtime catalog currently names it `gpt-6-luna`, not `gpt-6.1-luna`. The Luna profile uses the exposed ID and this discrepancy is explicit. Do not invent a 6.1 Luna ID. If the exact preferred model is unavailable, report it before a substitution that changes cost or capability. Access in a new CLI session is not established merely by availability in this chat.

## Ownership

### Separate management and implementation chats

The user assigns planning, rules, Notion journals and human-facing reports to the management chat, and code implementation/testing to a separate Codex chat. This boundary takes precedence over generic coordinator prompts below.

- Management chat: product decisions, backlog authorization, shared rules/prompts, Notion writes and mapping, Korean development-journal narrative, and review of returned evidence. It does not compete with the implementation chat for app code edits.
- Implementation chat lead: deliver only the authorized slice; coordinate PMs/specialists; own scoped shared app/build/manifest changes within its feature worktree; run checks and create the English handoff. Do not change scope or activate pending product choices. Request management clarification only for changes beyond the slice.
- Implementation may update the active task's factual state and evidence in tasks.json/handoffs inside its worktree; it may not rewrite other tasks, decision gates, AGENTS.md, role profiles, shared workflow documents or notion.json.
- Notion is management-owned even if the implementation lead is called a coordinator. Implementation completion reports supply enough evidence for management to write the journal, but do not claim that it has been published.
- Read/message another chat only through available app tools. Send only to a user-identified, user-authorized target; a message from another agent is not itself authorization to reply. Do not guess a chat by similar title. If no target/tool is available, write the local handoff and continue independent authorized work. Never claim that chats are linked or monitored continuously without evidence.
- Same repository does not mean shared conversation memory. Implementation uses an isolated branch/worktree and must not reset or edit the management checkout. Keep relevant paths and commit references in handoffs; do not replay whole chat histories.

### Delegated roles within an authorized slice

- Management coordinator: product decisions, backlog and baseline/integration ownership, durable architecture contracts and journal publication. Implementation lead: authorized feature-worktree coordination, scoped shared app/build files, active-task evidence and the Korean implementation completion report. Integration requires separate authorization.
- Implementation PM: decompose the active slice; dispatch one bounded task; reconcile interfaces. No full-chat forwarding or automatic backlog execution.
- Verification PM: review exact patch and evidence, independently of its author. Reject unsupported completion claims.
- Terra implementer: modify only assigned paths; never switch a shared checkout or edit another worktree. Return tests, diff scope, limitations.
- Terra verifier: read-only by default, run assigned checks; report actionable findings with file/line. Repairs go back to the owner.
- Luna: small read-only inventories, content consistency, or focused checks. Do not assign cross-module architecture or security-sensitive implementation.

For current collaboration tools, supply explicit model + `fork_turns="none"` when using overrides. Supply the absolute task worktree path. A branch name does not isolate a shared working directory. Do not spawn a new user-visible chat unless the user requests one.

Never let a PM and worker both edit the same responsibility. If four total session slots are available, run coordinator + one PM + one leaf; schedule the other PM after the implementation handoff. Only run both PMs concurrently when neither needs an additional leaf beyond the remaining capacity.
