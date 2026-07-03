# AGENTS.md

## Scope

This file applies to the entire `rushmore-seat` repository.

`rushmore-seat` is a large-scale seat reservation system simulation, not a basic reservation CRUD project. Preserve these goals:

- Queue-based traffic admission before seat selection.
- Admission token validation before seat snapshot, WebSocket subscription, hold, or confirm paths.
- 100,000-seat performance seat state management.
- Tile-scoped seat snapshot loading and WebSocket updates.
- Short-lived seat hold using PostgreSQL conditional update.
- Expired hold recovery.
- Oversell prevention.
- Load-testable behavior with measurable performance criteria.

Treat `README.md` and this file as the architecture contract unless the task explicitly asks to change the architecture.

---

## Codex CLI Workflow Rules

Use Codex CLI command modes as the primary AI workflow interface.

Command semantics:

- `/new`: start a fresh conversation in the same CLI session.
- `/plan`: enter planning mode in the active conversation.
- `/review`: review the current working tree.
- `/diff`: inspect local changes.
- `/resume`: re-enter a saved conversation.
- `/agent`: switch to an existing agent thread.
- `/permissions`: set what Codex may do without asking.
- `/status`: inspect active model, approval policy, writable roots, and context state.

Important: `/plan` is not a write lock. Planner and Reviewer conversations must use Read Only permissions.

Role assignment is conversation-based:

- Planner: `/new`, `/permissions` -> Read Only, `/status`, then `/plan`.
- Implementer: `/new`, write-capable permissions after human approval, then approved Planner Issue plus explicit implementation approval.
- Reviewer: `/new`, `/permissions` -> Read Only, `/status`, then `/review` or read-only review agents.
- Fixer: `/new`, write-capable permissions after selected Review Finding Issue is approved for fixing.

Do not combine CLI commands with duplicate prompt labels. In Codex CLI, `/plan` is the planning interface and `/review` is the review interface. `Plan only` and `Review only` are fallback phrases only for non-CLI environments.

Detailed workflow is in `docs/AI_AGENT_WORKFLOW.md`.

### GitHub Tracking Rules

GitHub is the traceability layer.

- Planner creates a GitHub Issue with label `enhancement`.
- Implementer creates a PR linked to the Planner Issue with `Closes #<issue-number>`.
- Reviewer leaves a PR review decision and summary comment.
- Reviewer creates a new Issue only for findings that must be tracked outside the current PR.
- Review Finding Issues must have label `review-finding`.
- Add `bug`, `documentation`, `question`, or `invalid` as a secondary label when applicable.
- Fixer creates a PR linked to the Review Finding Issue with `Fixes #<issue-number>`.

Do not create Review Finding Issues for every PR comment. PR comments are for the current PR. Review Finding Issues are for independent follow-up work.

### Multi-Agent Policy

Use multiple agents only for read-only research and review. Useful lanes are architecture, concurrency, tests, persistence boundaries, naming, and scope control.

Implementation and fixing must use one writer conversation. Review agents must return findings only. A human selects which findings move to the fixer conversation.

### Standard Flow

Planner:

```text
/new
/permissions
/status
/plan <task and planning requirements>
```

Planner must use Read Only permissions. After planning, `git status --short` must show no changes.

Plan review for risky work:

```text
Review this plan with read-only agents.
Check architecture, concurrency, tests, persistence boundaries, naming, and unnecessary complexity.
Return conflicts and required plan changes only.
Do not edit files.
```

Implementer:

```text
/new
Planner Issue:
<paste issue number and body>

Approved plan-review findings:
<paste selected findings>

Implement the approved issue.
```

Reviewer:

```text
/new
/permissions
/status
/review
```

Reviewer must use Read Only permissions. After review, `git status --short` must show no review-created file changes.

Fixer:

```text
/new
Review Finding Issue:
<paste review-finding issue number and body>

Fix only this issue.
```

### Re-entering Existing Role Conversations

Use `/resume` or `/agent` only when continuing the same role conversation.

- Continue planner refinement: re-enter the planner conversation and keep Read Only.
- Continue implementation after interruption: re-enter the implementer conversation.
- Continue review discussion: re-enter the reviewer conversation and keep Read Only.
- Continue selected fix: re-enter the fixer conversation.
- Switch roles: start a fresh conversation with `/new`.

Do not turn a planner conversation into an implementer conversation. Do not turn an implementer conversation into a reviewer conversation.

### AI Workflow Acceptance Criteria

A workflow run is acceptable when:

- Planning used `/new`, `/permissions` Read Only, `/status`, and `/plan`.
- Planner Issue was created with label `enhancement`.
- Risky plans were reviewed by read-only agents.
- Implementation used a separate single-writer conversation and explicit approval.
- PR linked the Planner Issue with `Closes #...`.
- Review used `/new`, `/permissions` Read Only, `/status`, and `/review` or read-only review agents.
- PR received a review result comment.
- Follow-up findings were tracked with `review-finding` Issues only when needed.
- Fix PRs linked Review Finding Issues with `Fixes #...`.
- Relevant tests were added or updated.
- Verification was run or explicitly reported as not run.
- `/diff` showed no unrelated file changes.
- Hook logs captured role conversations when hooks are enabled.

---

## Non-Negotiable Architecture Rules

### 1. Preserve Domain Separation

Do not collapse fixed venue layout and per-performance seat state.

```text
Venue
  └─ Hall
      └─ SeatMap
          ├─ Sector
          ├─ Tile
          └─ Seat

Performer
  └─ Show
      └─ Performance
          └─ PerformanceSeat
              └─ Reservation
```

Rules:

- `Seat` represents fixed physical layout.
- `PerformanceSeat` represents the state of a seat for one performance.
- Reservation state must be derived from `PerformanceSeat` / `Reservation`, not from the static `Seat`.
- `Performance` identity must not depend on `starts_at`, `ends_at`, or `hall_id`.
- Do not add time-overlap constraints casually. If needed, implement explicit service validation or PostgreSQL exclusion constraint after design review.

### 2. Keep Performance Status and Sales Status Separate

Do not merge performance lifecycle and sales lifecycle.

```text
PerformanceStatus
- SCHEDULED
- COMPLETED
- CANCELLED

PerformanceSalesStatus
- BEFORE_SALE
- ON_SALE
- CLOSED
```

Reservation is allowed only when:

```text
PerformanceStatus == SCHEDULED
and PerformanceSalesStatus == ON_SALE
```

### 3. Queue Admission Is Not Reservation

Never treat queue passage as seat ownership.

```text
Queue passed = seat selection screen admission
Queue passed != seat reserved
```

Only admitted users may reach seat selection, WebSocket subscription, hold, or confirm paths.

### 4. Do Not Broadcast All Seat Changes to All Clients

The system must remain tile-scoped.

```text
Sector selected
  -> Load tile summaries
  -> Load seat snapshot only for visible tile
  -> Subscribe only to required tile events
```

Rules:

- Do not broadcast 100,000-seat updates to every client.
- WebSocket updates must be scoped by performance and tile.
- A client should normally subscribe to only 1 to 3 tiles.
- If an outbound queue becomes stale or overloaded, prefer a resync signal over endlessly buffering deltas.

### 5. Persist Only Meaningful Seat States

RDB seat states should remain simple:

```text
AVAILABLE
  -> HELD
  -> RESERVED
```

Rules:

- Do not persist `CLAIMING` as a database state.
- If click-in-progress state is needed, model it as a Redis claim gate or transient WebSocket event.
- Do not introduce extra persistent states unless there is a clear recovery rule and test coverage.

### 6. Use PostgreSQL Conditional Update for Baseline Hold

The baseline hold path must use PostgreSQL conditional update semantics.

```sql
UPDATE performance_seat
SET
    status = 'HELD',
    hold_member_id = :memberId,
    hold_token = :holdToken,
    hold_expires_at = now() + interval '3 minutes',
    version = version + 1,
    updated_at = now()
WHERE id = :performanceSeatId
  AND performance_id = :performanceId
  AND status = 'AVAILABLE';
```

Rules:

- Do not replace the hot hold path with read-before-write logic.
- Do not hold a database transaction while waiting for payment or user confirmation.
- Hold ownership must be represented as a lease using `HELD + hold_expires_at`.
- Confirm must verify the hold token and `hold_expires_at > now()`.

### 7. Keep Redis Roles Explicit

Redis may be used for waiting queue, admission token state, seat read model, WebSocket backplane, and optional claim gate.

Rules:

- Redis queue admission must not become the source of truth for final reservation.
- PostgreSQL remains the source of truth for final seat state.
- Redis claim gate, if added, is an optimization before the DB hold path, not a replacement for DB correctness.

### 8. Expired Hold Recovery Is Mandatory

Expired holds must be recoverable even if WebSocket events are lost.

```text
HELD and hold_expires_at < now()
  -> AVAILABLE
  -> reservation EXPIRED
  -> WebSocket SEAT_RELEASED
```

Rules:

- Implement expired hold reaping as an idempotent operation.
- Reaper logic must not release already reserved seats.
- Confirm logic must independently reject expired holds.
- WebSocket events are notification, not the correctness boundary.

---

## Naming Rules

Prefer:

```text
performance
performanceSeat
reservation
sector
tile
seat
queue
admission
hold
confirm
```

Avoid adding new code using legacy names unless modifying existing skeleton code:

```text
event
asset
```

---

## Backend Rules

- Use Kotlin idioms, but keep code explicit and readable.
- Use constructor injection.
- Keep transaction boundaries explicit.
- Keep request validation at API boundaries.
- Keep domain state transitions centralized in service/domain logic.
- Do not introduce new global mutable state.
- Do not add broad dependencies without a clear need.

Use JPA where aggregate lifecycle and ordinary CRUD are acceptable.

Use `JdbcClient` / `JdbcTemplate` style SQL for hot-path operations where exact SQL semantics matter, especially:

- Seat hold conditional update.
- Expired hold bulk release.
- High-contention reservation paths.
- Load-test-sensitive queries.

Schema rules:

- Entity/schema changes require Flyway migrations.
- Do not rely on Hibernate DDL auto-generation.
- Keep `ddl-auto=validate` compatible with migrations.
- Add indexes when introducing new query patterns.

---

## API Rules

Target API shape:

```http
POST /performances/{performanceId}/queue
GET  /performances/{performanceId}/queue/me

GET /performances/{performanceId}/sectors/summary
GET /performances/{performanceId}/sectors/{sectorId}/tiles/summary
GET /performances/{performanceId}/tiles/{tileId}/seats

WS /ws/performances/{performanceId}?token={admissionToken}

POST /performances/{performanceId}/seats/{performanceSeatId}/hold
POST /performances/{performanceId}/reservations/confirm
```

Rules:

- Keep admission validation before seat selection and WebSocket subscription.
- Keep hold and confirm as separate operations.
- Do not add API behavior that bypasses queue/admission unless explicitly marked as admin/test-only.
- Update README or API documentation when changing endpoint shape.

---

## Testing Requirements

Every functional change must include relevant tests.

Required coverage by change type:

- Domain state changes: valid/invalid transitions, sales status rules, hold expiration, confirm behavior.
- Queue/admission changes: queue enter, queue status, token creation, TTL, expired admission rejection, duplicate queue entry.
- Seat hold changes: success, already held, already reserved, duplicate hold prevention, concurrent attempts, token ownership, expiration.
- Reservation confirm changes: valid token, wrong token, expired hold, already reserved seat, oversell prevention.
- Expired hold reaper changes: expired release, non-expired not released, reserved never released, repeat execution safety, release event.
- WebSocket/tile changes: tile-scoped subscription, tile snapshot, delta routing, no cross-tile leakage, resync behavior.
- Schema changes: Flyway validity, JPA validation, indexes, backward-compatible assumptions.

For DB-sensitive hold behavior, prefer PostgreSQL integration tests via Testcontainers.

Performance-sensitive changes should include targeted benchmark, k6 scenario, measured before/after result, or explicit explanation of why performance is not affected.

Important metrics:

- queue enter p50 / p95 / p99
- tile snapshot p50 / p95 / p99
- hold API p50 / p95 / p99
- WebSocket propagation p50 / p95 / p99
- oversell count
- duplicate hold count
- false-positive click failure rate
- Redis ops/sec
- PostgreSQL connection pool usage

---

## Verification Commands

Backend:

```bash
./gradlew :backend:test
./gradlew :backend:build
```

Backend with infrastructure:

```bash
docker compose up -d postgres redis
```

Frontend:

```bash
cd frontend
npm install
npm run build
```

Full local verification:

```bash
docker compose up -d postgres redis
./gradlew :backend:test
./gradlew :backend:build
cd frontend && npm install && npm run build
```

If a command cannot be run, report:

```text
Not verified: <command>
Reason: <why it could not be run>
Risk: <what might be broken>
```

---

## Security Rules

- Do not commit secrets.
- Do not add real credentials.
- Keep local defaults local-only.
- Do not expose internal actuator endpoints beyond intended local/monitoring use.
- Do not log raw tokens in committed files.
- Do not commit Codex transcript logs.
- Redact or hash admission tokens, hold tokens, API keys, cookies, and credentials before storing logs.

---

## Definition of Done

A change is complete only when:

- It follows the README architecture.
- It preserves queue/admission/hold/reservation separation.
- It includes relevant tests.
- It updates Flyway migration if schema changed.
- It updates documentation if behavior changed.
- It runs the relevant verification command or reports why it could not be run.
- It reports remaining risks honestly.

Reservation correctness minimum:

```text
oversell count = 0
duplicate hold count = 0
```
