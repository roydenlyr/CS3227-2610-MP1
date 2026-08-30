# Git Workflow Instructions Summary

## Scope

Added the owner-approved branching and commit policy to `AGENTS.md`. No application
code, product behavior, architecture, persistence, or build configuration changed.

## Owner decisions applied

- `master` is the stable integration and submission branch.
- Substantial work uses short-lived, task-scoped branches from the latest approved
  `master`.
- Authorized commits are atomic and use short, descriptive, imperative messages.
- Tests and diff review precede commits, and failing work is not committed unless a
  work-in-progress commit is explicitly requested.
- History-changing, remote, merge, branch-deletion, and destructive Git operations
  require explicit owner authorization.

## Implementation assumption

The instruction to commit each logical unit applies only after a development task
explicitly authorizes Codex to create local commits. This preserves the owner's
stated authorization boundary.

## Verification

- Inspected the existing branch and working-tree state before editing.
- Reviewed the new section for consistency with the existing project workflow.
- Reviewed the resulting diff and checked it for whitespace errors.

## Limitations

No branch or commit was created because this documentation task did not explicitly
authorize either. Unrelated pre-existing customer-catalog changes were preserved.
