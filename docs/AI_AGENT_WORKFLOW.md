# AI Agent Workflow

This repository uses Codex CLI command modes as the primary AI workflow interface.

## Command Model

- `/new`: start a fresh conversation in the same Codex CLI session.
- `/plan`: switch the active conversation into planning mode and create an implementation plan.
- `/review`: review the current working tree.
- `/diff`: inspect local changes.
- `/resume`: reload a saved conversation from the session picker.
- `/agent`: switch to an existing agent thread from the picker.

Roles are assigned by conversation boundary:

- Planner conversation: created with `/new`, then entered into planning mode with `/plan`.
- Implementer conversation: created with `/new`, then given the approved plan and explicit approval.
- Reviewer conversation: created with `/new`, then entered into review with `/review`.
- Fixer conversation: created with `/new`, then given selected review findings.

Do not combine Codex CLI command modes with duplicate prompt labels. In CLI, `/plan` is the planning interface and `/review` is the review interface.

## Standard Flow

### 1. Planner

Start a fresh conversation:

```text
/new
```

Then plan:

```text
/plan expired hold reaper implementation plan.

Read README.md and AGENTS.md before planning.
Return objective, architecture rules, likely files, expected behavior, risks, tests, and verification commands.
```

Expected result: plan only, no implementation.

### 2. Implementer

Start a fresh conversation:

```text
/new
```

Then provide the approved plan:

```text
Approved plan:
<paste planner output>

Implement the approved plan.

Constraints:
- stay inside the approved scope
- avoid unrelated changes
- add or update relevant tests
- run relevant verification
- report any unverified command with reason and risk
```

Expected result: implementation, tests, and verification report.

### 3. Diff Inspection

After implementation:

```text
/diff
```

Expected result: inspect all staged, unstaged, and untracked changes.

### 4. Reviewer

Start a fresh conversation:

```text
/new
```

Then review:

```text
/review
```

Optional follow-up:

```text
Focus on architecture drift, missing tests, concurrency risks, oversell risks, Redis/PostgreSQL responsibility confusion, naming drift, and unnecessary complexity.
```

Expected result: review findings only.

### 5. Fixer

Start a fresh conversation:

```text
/new
```

Then provide only selected findings:

```text
Selected reviewer findings:
<paste selected findings>

Fix only the selected findings.
Keep the scope narrow.
Run relevant verification again.
```

Expected result: targeted fix and verification report.

## Re-entering Existing Conversations

Use these commands when returning to an existing role conversation:

- `/resume`: reload a saved conversation from the session picker. Use this after restarting Codex or after leaving the CLI.
- `/agent`: switch to an existing agent thread from the picker while inside the CLI.

Operational rule:

- If continuing the same role, re-enter that role conversation with `/resume` or `/agent`.
- If changing role, start a new conversation with `/new`.

Examples:

- Continue planner refinement: `/resume` or `/agent` to the planner conversation.
- Continue implementation after interruption: `/resume` or `/agent` to the implementer conversation.
- Start independent review after implementation: `/new`, then `/review`.

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

Do not use these fallback labels inside Codex CLI when slash commands are available.

## Acceptance Criteria

A workflow run is acceptable when:

- planning used `/new` followed by `/plan`
- implementation used a separate conversation
- review used `/new` followed by `/review`
- fixes used a separate scoped conversation
- relevant tests were added or updated
- verification was run or explicitly reported as not run
- `/diff` showed no unrelated changes
- hook logs captured planner, implementer, reviewer, and fixer turns
