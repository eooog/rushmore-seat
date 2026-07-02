# AI Agent Workflow

This document defines how AI agent work should be controlled, logged, reviewed, and verified in this repository.

## Goal

The goal is not to claim that AI was used. The goal is to make AI-assisted development auditable:

- Plan before implementation.
- Human approval before file edits.
- Role separation between planning, implementation, and review.
- Hook-based logging of prompts and assistant responses.
- Test and verification records for functional changes.

## Workflow

1. Ask the agent for a plan only.
2. Review the proposed plan manually.
3. Approve implementation explicitly.
4. Require tests for functional changes.
5. Run verification commands.
6. Run reviewer mode without file edits.
7. Keep logs outside the repository.

## Prompt Template: Plan Only

```text
Plan only. Do not edit files yet.

Task: <task>

Goals:
- <goal 1>
- <goal 2>

Use README.md and AGENTS.md as the architecture contract.
Return:
1. Objective
2. Architecture rule involved
3. Files likely to change
4. Risks / failure modes
5. Tests to add or update
6. Verification commands
```

## Prompt Template: Approval

```text
계획 승인. 구현해.

Constraints:
- Stay inside the approved scope.
- Add or update relevant tests.
- Run the relevant verification command.
- Report unverified commands honestly.
```

## Prompt Template: Review Only

```text
Review only. Do not edit files.

Review the current diff for:
- AGENTS.md violations
- README architecture violations
- Missing tests
- Concurrency / oversell risks
- Redis/PostgreSQL responsibility confusion
- API naming drift
- Unnecessary complexity

Return concrete findings only.
```

## Log Policy

Codex transcript logs and hook logs must not be committed.

Recommended local path:

```text
~/.codex/rushmore-seat/logs/
```

Logs should capture:

- user prompt
- assistant final response
- session id
- turn id
- working directory
- model
- timestamp

Secrets and tokens must be redacted or hashed before persistent storage.
