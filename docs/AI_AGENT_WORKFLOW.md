# AI Agent Workflow

This repository uses a Codex CLI command-first workflow with controlled multi-agent review.

## Core Model

Roles are not permanent registry entries. A role is created by a conversation boundary and its first instruction.

- `/new`: create a fresh conversation.
- `/plan`: enter planning mode in the active conversation.
- `/review`: review the current working tree.
- `/diff`: inspect local changes.
- `/resume`: re-enter a saved conversation.
- `/agent`: switch to an existing agent thread.

Role mapping:

- Planner: `/new` then `/plan`.
- Implementer: `/new` then approved plan plus implementation approval.
- Reviewer: `/new` then `/review` or read-only review agents.
- Fixer: `/new` then selected reviewer findings only.

Do not combine CLI commands with duplicate prompt labels. In Codex CLI, use `/plan` for planning and `/review` for review. `Plan only` and `Review only` are fallback phrases only for non-CLI environments.

## Multi-Agent Policy

Use multiple agents only for read-only research and review.

Allowed:

- architecture review
- concurrency review
- test gap review
- persistence responsibility review
- simplicity / scope review

Not allowed:

- multiple writers changing files at the same time
- reviewer agents editing files
- unapproved scope expansion

Implementation and fixing must use one writer conversation.

## Standard Flow

### 1. Planner

```text
/new
/plan <task>

Read README.md and AGENTS.md before planning.
Return objective, architecture rules, likely files, expected behavior, risks, tests, and verification commands.
```

Expected result: plan only, no implementation, no file edits.

### 2. Plan Review

After the plan is produced, ask for read-only review agents when the change is risky.

```text
Review this plan with read-only agents.
Check architecture, concurrency, tests, persistence boundaries, and unnecessary complexity.
Return conflicts and required plan changes only.
Do not edit files.
```

Expected result: independent findings, no file edits, human decision before implementation.

### 3. Implementer

```text
/new

Approved plan:
<paste planner output>

Approved plan-review findings:
<paste selected findings>

Implement the approved plan.

Constraints:
- single writer only
- stay inside the approved scope
- avoid unrelated changes
- add or update relevant tests
- run relevant verification
- report any unverified command with reason and risk
```

Expected result: implementation, tests, and verification report.

### 4. Diff Inspection

```text
/diff
```

Expected result: confirm all changes are related to the approved task.

### 5. Reviewer

```text
/new
/review
```

For risky changes, add:

```text
Use read-only review agents for architecture drift, missing tests, concurrency risks, persistence responsibility confusion, naming drift, and scope creep.
Do not edit files.
```

Expected result: review findings only.

### 6. Fixer

```text
/new

Selected reviewer findings:
<paste selected findings>

Fix only the selected findings.
Keep the scope narrow.
Run relevant verification again.
```

Expected result: targeted fix and verification report.

## Re-entering Existing Role Conversations

Use `/resume` or `/agent` only when continuing the same role conversation.

- Continue planner refinement: re-enter the planner conversation.
- Continue implementation after interruption: re-enter the implementer conversation.
- Continue review discussion: re-enter the reviewer conversation.
- Continue a selected fix: re-enter the fixer conversation.

Switching roles requires `/new`.

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

## Acceptance Criteria

A workflow run is acceptable when:

- planning used `/new` followed by `/plan`
- risky plans were reviewed by read-only agents
- implementation used a separate single-writer conversation
- review used `/new` followed by `/review` or read-only review agents
- fixes used a separate scoped single-writer conversation
- relevant tests were added or updated
- verification was run or explicitly reported as not run
- `/diff` showed no unrelated changes
- hook logs captured the role conversations when hooks are enabled
