# AGENTS.md

This file defines the operating rules for Codex agents working in this repository.

## Scope

These instructions apply to the entire repository.

## Non-negotiable rules

1. **Never push directly to `main`.**
   - Create a working branch from the current `main`.
   - Open a pull request for every change.
   - Do not merge your own pull request unless a human explicitly asks you to.

2. **Implement only what is requested by a GitHub issue.**
   - Read the target issue title, body, labels, and comments before changing code.
   - Do not invent additional features, refactors, cleanup, optimizations, or UX changes.
   - Do not expand scope because something “looks useful.”
   - If the issue is ambiguous, implement only the unambiguous part and state the ambiguity in the PR. If safe implementation is impossible, stop and report the blocker.

3. **Do not mutate issues unless explicitly asked.**
   - Do not edit issue title/body, labels, assignees, milestones, or state.
   - Do not close issues from the agent side unless the issue or human instruction explicitly requires it.

4. **Respect the existing codebase.**
   - Preserve the current architecture, package boundaries, naming style, and test style.
   - Prefer small local changes over broad rewrites.
   - Do not introduce new frameworks, infrastructure, dependencies, or build tools unless the issue explicitly requires them.
   - Do not perform large package renames or domain renames unless the issue explicitly asks for that migration.

## Repository context

Rushmore Seat is a large-scale seat reservation system, not a generic CRUD app. The main design goals are:

- queue-based traffic control before seat selection,
- seat state synchronization scoped by sector/tile,
- reduced contention during seat hold,
- no overselling,
- performance verification through load tests.

Current stack is defined by the repository files and should be treated as authoritative:

- Backend: Kotlin, Java 21, Spring Boot, Gradle.
- Backend modules: Spring MVC, WebSocket, JPA, Redis, Flyway, Actuator/Micrometer.
- Database/runtime: PostgreSQL, Redis, Docker Compose.
- Frontend: Vite, Vanilla TypeScript, HTML, CSS, Canvas.
- Tests: JUnit 5, Testcontainers where applicable.

When documentation and build files disagree, follow executable build/config files first and mention the mismatch in the PR if it matters.

## Domain invariants

Keep these invariants unless the target issue explicitly changes them.

### Seat and performance model

- `Seat` represents fixed venue seat layout.
- `PerformanceSeat` represents the seat state for a specific performance.
- `PerformanceStatus` and `PerformanceSalesStatus` are separate concepts.
- A performance is reservable only when both the performance state and sales state allow it.

### Queue and admission

- Queue admission means access to the seat selection screen.
- Queue admission does **not** mean a seat is held or reserved.
- Redis queue/admission logic should control write-path pressure rather than letting every client reach the reservation path.

### Seat state

Persistent seat state should represent final meaningful states only:

```text
AVAILABLE -> HELD -> RESERVED
```

- Do not persist `CLAIMING` as an RDB state.
- If a transient claiming state is required, keep it in Redis or WebSocket event flow only.

### Hold and confirm

- A hold is valid only when the PostgreSQL conditional update succeeds.
- `affected rows = 1` means hold success.
- `affected rows = 0` means the seat was already held/reserved or otherwise unavailable.
- Do not keep a DB transaction or connection open while the user waits for payment/confirmation.
- Temporary occupancy is represented by `HELD` plus `hold_expires_at`.
- Confirm logic must reject expired holds.
- Expired holds must be recoverable by server-side reaper logic, not by client behavior alone.

### WebSocket and read model

- Do not broadcast all 100,000 seat changes to all clients.
- Clients should receive updates only for subscribed sector/tile scopes.
- Slow clients should be allowed to resync from a snapshot instead of accumulating unbounded delta events.

## Implementation workflow

Before coding:

1. Identify the target GitHub issue.
2. Read `README.md` and nearby implementation files relevant to the issue.
3. Check whether the issue is backend, frontend, infrastructure, load-test, or documentation work.
4. Define the smallest change that satisfies the issue.

While coding:

1. Keep commits focused.
2. Prefer existing patterns over new abstractions.
3. Add or update tests when behavior changes.
4. Keep public API changes explicit and issue-scoped.
5. Keep database schema changes in Flyway migrations when schema changes are required.
6. For hot reservation paths, avoid accidental extra queries, long transactions, or broad locks.

Before opening the PR:

1. Run the narrowest relevant validation command available.
2. If validation cannot be run, explain why in the PR.
3. Review the diff for accidental scope expansion.
4. Confirm no unrelated formatting-only churn is included.

## Useful commands

Backend:

```bash
./gradlew :backend:test
./gradlew :backend:bootRun
```

Frontend:

```bash
cd frontend
npm install
npm run build
npm run dev
```

Runtime dependencies:

```bash
docker compose up -d postgres redis
```

Full local observability stack, when needed:

```bash
docker compose up -d
```

Use these commands as guidance. If the issue or current repository scripts provide more specific commands, prefer the more specific commands.

## Pull request requirements

Every PR should include:

- linked issue number,
- concise summary of the change,
- explicit note of what was intentionally not changed,
- validation commands run and their result,
- any unresolved ambiguity or follow-up work.

PRs should be small enough for a human to review without reconstructing the entire project context.

## Forbidden changes without explicit issue scope

Do not do these unless the target issue explicitly asks for them:

- direct push to `main`,
- broad architecture rewrite,
- dependency or framework replacement,
- database engine change,
- Redis/PostgreSQL topology change,
- large package rename,
- mass formatting,
- changing seat state semantics,
- making queue admission equivalent to reservation,
- broadcasting whole-seatmap events to every WebSocket client,
- adding speculative “nice-to-have” features.
