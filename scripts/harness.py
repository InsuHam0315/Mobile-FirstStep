"""Small local task/worktree harness. Does not launch models or implement backlog tasks."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOARD = ROOT / "docs/harness/tasks.json"
STATES = {"queued", "ready", "in_progress", "verified", "deferred"}
PROFILE_MODELS = {
    "firststep_implementation_pm": "gpt-6.1-sol",
    "firststep_verification_pm": "gpt-6.1-sol",
    "firststep_implementer": "gpt-6.1-terra",
    "firststep_verifier": "gpt-6.1-terra",
    "firststep_explorer": "gpt-6-luna",
}


def git(*args: str) -> str:
    result = subprocess.run(["git", "-C", str(ROOT), *args], capture_output=True,
                            text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        raise ValueError(result.stderr.strip() or f"git failed: {args[0]}")
    return result.stdout.strip()


def load_board() -> dict:
    board = json.loads(BOARD.read_text(encoding="utf-8-sig"))
    if board.get("schemaVersion") != 1:
        raise ValueError("Unsupported task schema")
    return board


def validate(board: dict) -> list[str]:
    errors = []
    tasks = board.get("tasks", [])
    ids = [t.get("id") for t in tasks]
    branches = [t.get("branch") for t in tasks]
    if not tasks or len(ids) != len(set(ids)) or len(branches) != len(set(branches)):
        errors.append("Task IDs and branch names must be nonempty and unique")
    decisions = board.get("decisions", {})
    for task in tasks:
        tid = task.get("id", "")
        if not re.fullmatch(r"H\d{2}", tid):
            errors.append(f"Invalid task ID: {tid}")
        if not re.fullmatch(r"codex/[a-z0-9]+(?:-[a-z0-9]+)*", task.get("branch", "")):
            errors.append(f"{tid}: invalid branch")
        if task.get("status") not in STATES:
            errors.append(f"{tid}: invalid status")
        for field in ("goal", "owner", "writeScope", "acceptance"):
            if not task.get(field):
                errors.append(f"{tid}: missing {field}")
        for dep in task.get("dependsOn", []):
            if dep not in ids or dep == tid:
                errors.append(f"{tid}: invalid dependency {dep}")
        for decision in task.get("decisionKeys", []):
            if decision not in decisions:
                errors.append(f"{tid}: unknown decision {decision}")
        for scope in task.get("writeScope", []):
            if scope.startswith("/") or "\\" in scope or ":" in scope or ".." in scope.split("/"):
                errors.append(f"{tid}: scope must remain repository-relative")
    by_id = {t["id"]: t for t in tasks}
    visiting, done = set(), set()

    def visit(tid):
        if tid in visiting:
            raise ValueError(f"Dependency cycle at {tid}")
        if tid in done or tid not in by_id:
            return
        visiting.add(tid)
        for dep in by_id[tid].get("dependsOn", []):
            visit(dep)
        visiting.remove(tid)
        done.add(tid)
    try:
        for tid in by_id:
            visit(tid)
    except ValueError as exc:
        errors.append(str(exc))
    if board.get("activeTask") is not None and board["activeTask"] not in ids:
        errors.append("Unknown activeTask")
    if board.get("baselineSha") is not None and not re.fullmatch(r"[0-9a-f]{40}", board["baselineSha"]):
        errors.append("baselineSha must be a full commit SHA or null")
    for name, model in PROFILE_MODELS.items():
        path = ROOT / ".codex/agents" / f"{name}.toml"
        try:
            profile = tomllib.loads(path.read_text(encoding="utf-8"))
            if profile.get("name") != name or profile.get("model") != model:
                errors.append(f"{name}: routing mismatch")
            if not profile.get("description") or not profile.get("developer_instructions"):
                errors.append(f"{name}: incomplete profile")
        except (OSError, ValueError) as exc:
            errors.append(f"{name}: {exc}")
    for name in ("README.md", "ROUTING.md", "PROMPTS.md", "UI_BOUNDARIES.md"):
        if not (ROOT / "docs/harness" / name).is_file():
            errors.append(f"Missing harness document: {name}")
    return errors


def get_task(board, tid):
    for task in board["tasks"]:
        if task["id"] == tid:
            return task
    raise ValueError(f"Unknown task: {tid}")


def readiness(board, task):
    reasons = []
    if not board.get("baselineSha"):
        reasons.append("baseline not checkpointed")
    if task["status"] != "ready" or board.get("activeTask") != task["id"]:
        reasons.append("task not active and ready")
    for dep in task["dependsOn"]:
        if get_task(board, dep)["status"] != "verified":
            reasons.append(f"dependency {dep} not verified")
    for key in task["decisionKeys"]:
        if board["decisions"].get(key) != "approved":
            reasons.append(f"decision {key} not approved")
    return reasons


def new_worktree(board, task, dry_run):
    reasons = readiness(board, task)
    if reasons:
        raise ValueError("Cannot create feature worktree: " + "; ".join(reasons))
    if git("status", "--porcelain", "--untracked-files=all"):
        raise ValueError("Checkout has uncommitted changes; preserve and checkpoint them first")
    if Path(git("rev-parse", "--show-toplevel")).resolve() != ROOT:
        raise ValueError("Harness must reside at its repository root")
    baseline = board["baselineSha"]
    git("rev-parse", "--verify", baseline + "^{commit}")
    git("merge-base", "--is-ancestor", baseline, "HEAD")
    # Each verified dependency must record an integrated commit, preventing stale-base forks.
    for dep in task["dependsOn"]:
        candidate = get_task(board, dep).get("integratedSha")
        if not candidate or not re.fullmatch(r"[0-9a-f]{40}", candidate):
            raise ValueError(f"Dependency {dep} needs its reviewed integratedSha")
        git("merge-base", "--is-ancestor", candidate, "HEAD")
    if git("branch", "--list", task["branch"]):
        raise ValueError("Task branch already exists; inspect it instead of overwriting")
    parent = (ROOT.parent / (ROOT.name + "-worktrees")).resolve()
    target = parent / (task["id"].lower() + "-" + task["branch"].split("/")[1])
    if target.exists() or target.is_symlink():
        raise ValueError("Worktree target already exists; nothing was changed")
    if target.resolve().parent != parent or parent == ROOT:
        raise ValueError("Worktree target escapes intended directory")
    base = git("rev-parse", "HEAD")
    if not dry_run:
        parent.mkdir(parents=True, exist_ok=True)
        git("worktree", "add", "-b", task["branch"], str(target), base)
    print(json.dumps({"dryRun": dry_run, "task": task["id"], "branch": task["branch"],
                      "base": base, "worktree": str(target),
                      "note": "No agents started; no credentials/local settings copied."}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["check", "status", "packet", "new-worktree"])
    parser.add_argument("task", nargs="?")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    board = load_board()
    errors = validate(board)
    if errors:
        raise ValueError("\n".join(errors))
    if args.command == "check":
        print(f"PASS: {len(board['tasks'])} tasks; {len(PROFILE_MODELS)} TOML profiles; dependency and path checks.")
        print("Syntax/contracts only; native profile loading and model access not exercised.")
    elif args.command == "status":
        print("Baseline:", board.get("baselineSha") or "NOT CHECKPOINTED")
        print("Active:", board.get("activeTask") or "none")
        for task in board["tasks"]:
            print(f"{task['id']} {task['status']:12} {task['branch']} | " + "; ".join(readiness(board, task)))
    else:
        if not args.task:
            raise ValueError("Task ID is required")
        task = get_task(board, args.task)
        if args.command == "packet":
            print(json.dumps(task, indent=2))
            print("Readiness:", "; ".join(readiness(board, task)) or "ready")
            print("Use docs/harness/PROMPTS.md. Packet output does not authorize execution.")
        else:
            new_worktree(board, task, args.dry_run)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError) as exc:
        print("ERROR:", exc, file=sys.stderr)
        sys.exit(1)
