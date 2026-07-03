# AI Agent Workflow

This repository uses a Codex CLI command-first workflow with GitHub Issue / Pull Request tracking and controlled multi-agent review.

The goal is not to say that AI was used. The goal is to make AI-assisted development auditable through conversations, issues, pull requests, review decisions, tests, and logs.

## Core Model

Roles are not permanent registry entries. A role is created by a conversation boundary and its first instruction.

- `/new`: create a fresh conversation.
- `/plan`: enter planning mode in the active conversation.
- `/review`: review the current working tree.
- `/diff`: inspect local changes.
- `/resume`: re-enter a saved conversation.
- `/agent`: switch to an existing agent thread.
- `/status`: inspect active model, approval policy, writable roots, and context state.

Important: `/plan` is planning mode, not a write lock. If `/permissions` does not expose a true Read Only mode, do not rely on it for Planner or Reviewer work. Start those roles with a read-only sandbox instead.

Recommended role launch model:

```bash
codex --sandbox read-only
```

Use this for Planner and Reviewer conversations. Use normal workspace-write/default mode only for Implementer and Fixer after human approval.

Role mapping:

- Planner: start Codex with `--sandbox read-only`, then `/new`, then `/plan`.
- Implementer: start Codex in a write-capable mode after human approval, then implement the approved Planner Issue.
- Reviewer: start Codex with `--sandbox read-only`, then `/new`, then `/review` or read-only review agents.
- Fixer: start Codex in a write-capable mode after a Review Finding Issue is selected for fixing.

Do not combine CLI commands with duplicate prompt labels. In Codex CLI, use `/plan` for planning and `/review` for review. `Plan only` and `Review only` are fallback phrases only for non-CLI environments.

## GitHub Tracking Model

GitHub is the traceability layer.

- Planner output becomes a GitHub Issue.
- Implementer output becomes a Pull Request linked to the Planner Issue.
- Reviewer output becomes a PR review decision and, only when needed, a follow-up Review Finding Issue.
- Fixer output becomes a follow-up PR linked to the Review Finding Issue.

### Issue Labels

Use labels as workflow markers.

| Source | Purpose | Required labels |
|---|---|---|
| Planner | New feature or planned work | `enhancement` |
| Reviewer | Follow-up finding from PR review | `review-finding` |
| Reviewer | Actual defect | `review-finding`, `bug` |
| Reviewer | Documentation gap | `review-finding`, `documentation` |
| Reviewer | Needs decision | `review-finding`, `question` |
| Reviewer | Finding rejected or no longer relevant | `review-finding`, `invalid` |

Do not create a new Issue for every review comment. Create a Review Finding Issue only when the finding should be tracked outside the current PR.

## Standard Flow

### 1. Planner

Start a read-only Codex session:

```bash
codex --sandbox read-only
```

Then in Codex:

```text
/new
/status
/plan <task>

Read README.md and AGENTS.md before planning.
Return objective, architecture rules, likely files, expected behavior, risks, tests, and verification commands.
```

Expected result: plan only, no implementation, no file edits.

After planning, verify locally:

```bash
git status --short
```

Expected: no output.

Then create a GitHub Issue:

```md
## Objective

## Architecture Rules

## Acceptance Criteria

## Tests Required

## Verification

## AI Trace
- Planner conversation: <local log/session reference>
```

Issue label: `enhancement`.

### 2. Plan Review

For risky changes, review the plan before implementation. Keep the same read-only session, or start another read-only session.

```text
Review this plan with read-only agents.
Check architecture, concurrency, tests, persistence boundaries, naming, and unnecessary complexity.
Return conflicts and required plan changes only.
Do not edit files.
```

Expected result: independent findings, no file edits, human decision before implementation.

If the plan changes materially, update the Planner Issue before implementation.

### 3. Implementer

Start a write-capable Codex session only after the Planner Issue is approved by the human.

```bash
codex
```

Then in Codex:

```text
/new
Planner Issue:
<paste issue number and body>

Approved plan-review findings:
<paste selected findings>

Implement the approved issue.

Constraints:
- single writer only
- stay inside the approved scope
- avoid unrelated changes
- add or update relevant tests
- run relevant verification
- report any unverified command with reason and risk
```

Expected result: implementation, tests, and verification report.

Create a Pull Request:

```md
## Summary

## Linked Issue
Closes #<planner-issue-number>

## Implementation Notes

## Tests

## Verification

## AI Trace
- Implementer conversation: <local log/session reference>
```

The PR must link the Planner Issue using `Closes #<issue-number>` unless the PR is intentionally partial.

### 4. Diff Inspection

After implementation:

```text
/diff
```

Expected result: confirm all changes are related to the approved issue.

### 5. Reviewer

Start a read-only Codex session:

```bash
codex --sandbox read-only
```

Then in Codex:

```text
/new
/status
/review
```

For risky changes, add:

```text
Use read-only review agents for architecture drift, missing tests, concurrency risks, persistence responsibility confusion, naming drift, and scope creep.
Do not edit files.
```

Expected result: review findings only, no file edits.

After review, verify locally:

```bash
git status --short
```

No files should be changed by the Reviewer.

Reviewer must choose one decision:

| Decision | Use when | Action |
|---|---|---|
| Approve | PR satisfies the issue and has no blocking finding | Leave review summary comment, approve, merge when ready |
| Request changes | PR has blocking issues that should be fixed in the same PR | Leave required changes in PR review, no new Issue unless tracking outside PR is needed |
| Comment | Review has non-blocking questions or notes | Leave PR comment, no merge block |
| Reject / close PR | PR violates architecture or solves the wrong problem | Request changes or close PR, keep Planner Issue open |
| Approve with follow-up | PR satisfies current scope but reveals separate work | Approve or comment, create Review Finding Issue |

### 6. Review Result Comment

Every reviewed PR should have a summary comment.

```md
## Review Result

Decision:
- Approved / Request changes / Comment / Rejected / Approved with follow-up

Linked issue:
- #<planner-issue-number>

Checks:
- Architecture:
- Tests:
- Verification:
- Risk:

Findings:
1.
2.
3.

Follow-up issues:
- #<review-finding-issue-number> or none

Next action:
- Merge / Fix in this PR / Create follow-up PR / Close PR
```

PR comments are for decisions about the current PR. Review Finding Issues are for follow-up work that must be tracked separately.

### 7. Review Finding Issue

Create a Review Finding Issue only when the finding should survive beyond the current PR.

Issue labels:

- Always add `review-finding`.
- Add `bug`, `documentation`, `question`, or `invalid` when applicable.

Issue body:

```md
## Source

Found during review of PR #<pr-number>.

## Problem

## Why this matters

## Required fix

## Suggested tests

## Related
- PR #<pr-number>
- Original issue #<planner-issue-number>

## AI Trace
- Reviewer conversation: <local log/session reference>
```

### 8. Fixer

Start a write-capable Codex session only after the Review Finding Issue is selected for fixing.

```bash
codex
```

Then in Codex:

```text
/new
Review Finding Issue:
<paste review-finding issue number and body>

Fix only this issue.
Keep the scope narrow.
Run relevant verification again.
```

Expected result: targeted fix and verification report.

Create a PR linked to the Review Finding Issue:

```md
## Summary

## Linked Issue
Fixes #<review-finding-issue-number>

## Tests

## Verification
```

## Re-entering Existing Role Conversations

Use `/resume` or `/agent` only when continuing the same role conversation in a session with the same write policy.

- Planner and Reviewer re-entry must be done from a read-only sandbox session.
- Implementer and Fixer re-entry may use a write-capable session only for approved work.
- Switching roles requires `/new`.

Do not turn a planner conversation into an implementer conversation. Do not turn an implementer conversation into a reviewer conversation.

## Optional Project-Scoped Custom Agents

Suggested read-only custom agents:

- `architecture_guard`: architecture drift from README.md / AGENTS.md.
- `concurrency_guard`: oversell, duplicate hold, stale release, and ownership risks.
- `test_guard`: missing tests and weak verification.
- `persistence_guard`: PostgreSQL and Redis responsibility boundaries.
- `simplicity_guard`: scope creep and unnecessary complexity.

Do not use custom review agents as writers.

## Non-CLI Fallback

Prompt labels are fallback only for environments without slash commands.

Planning fallback:

```text
Plan only. Do not edit files yet.
```

Review fallback:

```text
Review only. Do not edit files.
```

Fallback still requires external enforcement such as read-only sandboxing, a disposable worktree, or manual `git status` verification.

## Acceptance Criteria

A workflow run is acceptable when:

- Planner and Reviewer were run with `codex --sandbox read-only`
- planning used `/new`, `/status`, and `/plan`
- Planner Issue was created with `enhancement`
- risky plans were reviewed by read-only agents
- implementation used a separate single-writer conversation after human approval
- PR linked the Planner Issue with `Closes #...`
- review used `/new`, `/status`, and `/review`
- PR received a review result comment
- follow-up findings were tracked with `review-finding` Issues only when needed
- fixes used a separate scoped single-writer conversation
- fix PRs linked Review Finding Issues with `Fixes #...`
- relevant tests were added or updated
- verification was run or explicitly reported as not run
- `/diff` showed no unrelated changes
- hook logs captured the role conversations when hooks are enabled
