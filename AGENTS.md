# AGENTS.md

## Scope

This file applies to the entire `rushmore-seat` repository.

`rushmore-seat` is a large-scale seat reservation system simulation. The goal is not basic reservation CRUD. The system must preserve these architectural goals:

- Queue-based traffic admission before seat selection.
- Admission token validation before seat snapshot, WebSocket subscription, hold, or confirm paths.
- 100,000-seat performance seat state management.
- Tile-scoped seat snapshot loading and WebSocket updates.
- Short-lived seat hold using PostgreSQL conditional update.
- Expired hold recovery.
- Oversell prevention.
- Load-testable behavior with measurable performance criteria.

Agents must treat `README.md` and this file as the architecture contract unless the task explicitly asks to change the architecture.

---

## Repository Shape

```text
rushmore-seat
├── backend/          # Kotlin + Spring Boot backend
├── frontend/         # Vite + Vanilla TypeScript + Canvas frontend
├── docker-compose.yml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

Backend stack:

- Kotlin
- Java 21
- Spring Boot
- Spring MVC
- Spring WebSocket
- Spring Data JPA
- Redis
- PostgreSQL
- Flyway
- Actuator / Micrometer / Prometheus
- JUnit 5
- Testcontainers

Frontend stack:

- Vite
- Vanilla TypeScript
- HTML / CSS
- Canvas-based seat map rendering

Runtime dependencies:

- PostgreSQL
- Redis
- Prometheus
- Grafana

---

## Non-Negotiable Architecture Rules

### 1. Preserve Domain Separation

Do not collapse fixed venue layout and per-performance seat state.

Use this conceptual model:

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
- Do not add time-overlap constraints casually. If needed, implement explicit service validation or a PostgreSQL exclusion constraint after design review.

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

The queue absorbs large traffic. Only admitted users may reach seat selection, WebSocket subscription, hold, or confirm paths.

### 4. Do Not Broadcast All Seat Changes to All Clients

The system must remain tile-scoped.

Required flow:

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

Expected behavior:

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

Interpretation:

```text
affected rows = 1 -> hold success
affected rows = 0 -> already held, reserved, expired, invalid, or unavailable
```

Rules:

- Do not replace the hot hold path with read-before-write logic.
- Do not hold a database transaction while waiting for payment or user confirmation.
- Hold ownership must be represented as a lease using `HELD + hold_expires_at`.
- Confirm must verify the hold token and `hold_expires_at > now()`.

### 7. Keep Redis Roles Explicit

Redis may be used for:

- Waiting queue.
- Admission token / queue token state.
- Seat read model.
- WebSocket Pub/Sub backplane.
- Optional claim gate.

Rules:

- Redis queue admission must not become the source of truth for final reservation.
- PostgreSQL remains the source of truth for final seat state.
- Redis claim gate, if added, is an optimization before the DB hold path, not a replacement for DB correctness.
- Redis replica is not a write scale-out mechanism for reservation correctness.

### 8. Expired Hold Recovery Is Mandatory

Expired holds must be recoverable even if WebSocket events are lost.

Expected recovery:

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

The README states that the current skeleton still has some `event/asset` naming. New code should move toward the target domain language.

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

Avoid adding new code using these legacy names unless modifying existing skeleton code:

```text
event
asset
```

Do not perform broad package renames unless explicitly requested.

---

## AI Workflow Rules

This repository is used to demonstrate controlled AI-assisted development. Agents must follow the workflow below.

### 1. Plan First

Before editing files, produce a plan and stop.

The plan must include:

- Objective.
- Relevant README / AGENTS.md architecture rule.
- Files likely to change.
- Expected behavior after the change.
- Risks and failure modes.
- Tests to add or update.
- Verification commands.

Do not edit files during the planning step.

### 2. Human Approval Gate

Do not implement until the user explicitly approves the plan.

Acceptable approval examples:

```text
계획 승인. 구현해.
Approved. Implement it.
Proceed with the approved plan.
```

If the user asks for planning, review, analysis, risk check, or design only, do not edit files.

### 3. Stay Inside the Approved Scope

During implementation:

- Modify only files required by the approved plan.
- Do not rewrite unrelated modules.
- Do not introduce broad renames, new frameworks, or new production dependencies without explicit approval.
- If implementation reveals that the plan is wrong, stop and report the revised plan before continuing.

### 4. Separate Implementer and Reviewer Behavior

When asked to review, act as a reviewer only.

Reviewer mode rules:

- Do not edit files.
- Inspect the diff or described change.
- Report architecture violations, missing tests, concurrency risks, naming drift, and unnecessary complexity.
- Prefer concrete findings over broad advice.

Implementer mode rules:

- Follow the approved plan.
- Add or update tests in the same change.
- Run relevant verification commands when possible.
- Report unverified commands honestly.

### 5. Traceability

Every meaningful AI-assisted change should leave enough information to reconstruct:

- Initial request.
- Proposed plan.
- Human approval or plan modification.
- Files changed.
- Tests added or updated.
- Verification command and result.
- Remaining risks.

When finishing a task, include a concise summary in this shape:

```text
Summary:
- Changed:
- Tests:
- Verification:
- Risks / follow-up:
```

Do not claim a command passed unless it was actually run.

---

## Backend Coding Rules

### Kotlin / Spring

- Use Kotlin idioms, but keep code explicit and readable.
- Use constructor injection.
- Keep transaction boundaries explicit.
- Keep request validation at API boundaries.
- Keep domain state transitions centralized in service/domain logic.
- Do not introduce new global mutable state.
- Do not add broad dependencies without a clear need.

### Persistence

Use JPA where aggregate lifecycle and ordinary CRUD are acceptable.

Use `JdbcClient` / `JdbcTemplate` style SQL for hot-path operations where exact SQL semantics matter, especially:

- Seat hold conditional update.
- Expired hold bulk release.
- High-contention reservation paths.
- Load-test-sensitive queries.

Rules:

- Entity/schema changes require Flyway migrations.
- Do not rely on Hibernate DDL auto-generation.
- Keep `ddl-auto=validate` compatible with migrations.
- Do not make destructive migrations unless the task explicitly requires it.
- Add indexes when introducing new query patterns.

### API

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
- Do not add API behavior that bypasses queue/admission unless it is explicitly marked as admin/test-only.
- Update README or API documentation when changing endpoint shape.

### WebSocket

Rules:

- WebSocket subscriptions must be scoped by performance and tile.
- Do not send full seat map updates for small changes.
- Prefer snapshot + delta model.
- Support resync when a client misses too many updates.
- When multiple app replicas are involved, use Redis Pub/Sub or another explicit backplane.

---

## Frontend Rules

The frontend is a minimal Vite + TypeScript + Canvas client.

Rules:

- Keep frontend simple.
- Do not introduce a heavy framework unless explicitly requested.
- Keep seat rendering tile-oriented.
- Do not assume the client has all 100,000 seats loaded.
- Keep API URLs and WebSocket URLs easy to configure.
- When changing frontend behavior, run the frontend build.

---

## Testing Requirements

Every functional change must include relevant tests.

Do not claim a feature is complete unless the relevant tests have been added or updated and the verification command has been run or explicitly reported as not run.

### Required Coverage by Change Type

#### Domain State Changes

Add or update tests for:

- Valid state transitions.
- Invalid state transitions.
- Performance status vs sales status rules.
- Hold expiration behavior.
- Reservation confirm behavior.

#### Queue / Admission Changes

Add or update tests for:

- Queue enter.
- Queue status.
- Admission token creation.
- Admission token TTL.
- Rejection when admission is missing or expired.
- Duplicate queue entry behavior.

#### Seat Hold Changes

Add or update tests for:

- `AVAILABLE -> HELD` success.
- Hold failure when already held.
- Hold failure when already reserved.
- Duplicate hold prevention.
- Concurrent hold attempts on the same seat.
- Hold token ownership.
- Hold expiration.

For DB-sensitive hold behavior, prefer integration tests with PostgreSQL via Testcontainers. Do not rely only on mocks for concurrency-sensitive correctness.

#### Reservation Confirm Changes

Add or update tests for:

- Confirm success with valid hold token.
- Confirm failure with wrong hold token.
- Confirm failure after hold expiration.
- Confirm failure for already reserved seat.
- Oversell prevention.

#### Expired Hold Reaper Changes

Add or update tests for:

- Expired `HELD` seats are released.
- Non-expired `HELD` seats are not released.
- `RESERVED` seats are never released.
- Reaper can run repeatedly without corrupting state.
- Release event is published after successful release.

#### WebSocket / Tile Changes

Add or update tests for:

- Tile-scoped subscription.
- Seat snapshot loading by tile.
- Delta event routing by tile.
- No cross-tile event leakage.
- Resync-required behavior when event delivery cannot be trusted.

#### Schema / Migration Changes

Add or update tests or verification for:

- Flyway migration validity.
- JPA schema validation.
- Required indexes for new query paths.
- Backward-compatible data assumptions.

#### Performance-Sensitive Changes

When changing queue, hold, WebSocket fanout, Redis, or DB hot-path code, include one of:

- Targeted benchmark.
- k6 scenario.
- Measured before/after result.
- Explicit explanation of why performance is not affected.

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

Use targeted checks first, then broader checks.

### Backend

```bash
./gradlew :backend:test
```

```bash
./gradlew :backend:build
```

### Backend with Infrastructure

Start local dependencies when tests or manual checks require PostgreSQL or Redis:

```bash
docker compose up -d postgres redis
```

For metrics/dashboard work:

```bash
docker compose up -d
```

### Frontend

```bash
cd frontend
npm install
npm run build
```

### Full Local Verification

For changes touching backend, frontend, and infrastructure:

```bash
docker compose up -d postgres redis
./gradlew :backend:test
./gradlew :backend:build
cd frontend && npm install && npm run build
```

If a command cannot be run, explicitly report:

```text
Not verified: <command>
Reason: <why it could not be run>
Risk: <what might be broken>
```

---

## Documentation Rules

Update documentation when changing:

- API endpoint shape.
- Domain terminology.
- Seat state transition.
- Queue/admission behavior.
- Hold/confirm semantics.
- WebSocket event format.
- Redis key shape.
- Migration or local setup.
- Load-test scenario or success criteria.
- AI workflow, hook logging, or verification process.

The README is part of the architecture contract. Keep it aligned with implementation.

---

## Security / Safety Rules

- Do not commit secrets.
- Do not add real credentials.
- Keep local defaults local-only.
- Prefer environment variables for deployable configuration.
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

For reservation correctness, the minimum acceptance rule is:

```text
oversell count = 0
duplicate hold count = 0
```
