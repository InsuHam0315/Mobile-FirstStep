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

- Coordinator: decisions, task board, baseline/integration commits, shared build files, architecture contracts, final Korean report.
- Implementation PM: decompose the active slice; dispatch one bounded task; reconcile interfaces. No full-chat forwarding or automatic backlog execution.
- Verification PM: review exact patch and evidence, independently of its author. Reject unsupported completion claims.
- Terra implementer: modify only assigned paths; never switch a shared checkout or edit another worktree. Return tests, diff scope, limitations.
- Terra verifier: read-only by default, run assigned checks; report actionable findings with file/line. Repairs go back to the owner.
- Luna: small read-only inventories, content consistency, or focused checks. Do not assign cross-module architecture or security-sensitive implementation.

For current collaboration tools, supply explicit model + `fork_turns="none"` when using overrides. Supply the absolute task worktree path. A branch name does not isolate a shared working directory. Do not spawn a new user-visible chat unless the user requests one.

Never let a PM and worker both edit the same responsibility. If four total session slots are available, run coordinator + one PM + one leaf; schedule the other PM after the implementation handoff. Only run both PMs concurrently when neither needs an additional leaf beyond the remaining capacity.
