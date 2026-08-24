# AGENTS.md — CineBook AI Coding Rules

## 1. Purpose

CineBook is a **monolithic graduation project** with a short delivery timeline.

The AI agent must optimize for:

- Correct business functionality.
- Fast, focused implementation.
- Maintainable and understandable code.
- Consistent architecture.
- Low unnecessary context/token usage.
- Safe changes that do not silently damage data or existing behavior.

This file defines **how the AI should work**. Detailed project knowledge belongs in `docs/`.

---

## 2. Instruction Priority

When rules conflict, use this order:

1. Explicit developer instruction for the current task.
2. Locked decisions in this file.
3. Existing working architecture/conventions.
4. Relevant `docs/` documentation.
5. Framework/library best practices.
6. General AI preference.

Do not redesign working code merely because another approach is theoretically cleaner.

---

## 3. Locked Project Decisions

| Area | Decision |
|---|---|
| Architecture | Monolith |
| Backend architecture | Layered Architecture |
| Java | 21 |
| Backend | Spring Boot + Spring MVC |
| Persistence | Spring Data JPA / Hibernate |
| Build | Maven |
| Database | MySQL |
| Validation | Jakarta Bean Validation |
| Security | Spring Security + existing JWT architecture |
| Frontend | Vue 3 |
| Frontend build | Vite |
| Frontend language | TypeScript |
| Frontend API | Axios |
| Frontend routing | Vue Router |
| Frontend state | Pinia |
| Frontend styling | Tailwind CSS |
| Payment | VNPay Sandbox |
| Movie data source | TMDB API + import/seed workflow |
| Redis | Optional |

Use versions already established in the repository. Do not upgrade unrelated dependencies.

The frontend stack above is the default for new frontend work. If the repository already has an established working alternative, **do not migrate it solely for preference**.


---

## 4. Architecture Rules

CineBook is one Spring Boot application using layered architecture:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Supporting components may include:

```text
Entity / DTO / Mapper / Exception / Validation / Security / Configuration / Utility
```

Do not introduce or migrate to:

- Microservices.
- Eureka/service discovery.
- Separate API Gateway service.
- Config Server.
- Inter-service REST architecture.
- Distributed transactions.
- Kafka/RabbitMQ business architecture.
- Distributed locking infrastructure.

Do not optimize for hypothetical enterprise-scale requirements.

---

## 5. Existing Code and Database Are Authoritative

The database design already exists. Entity and Repository layers are already implemented.

Before implementing a feature:

1. Inspect the relevant entity.
2. Inspect its repository.
3. Inspect related entities/repositories.
4. Inspect a similar existing feature.
5. Reuse established conventions.

Do not duplicate entities, repositories, domain concepts, or representations of the same table.

Do not rename existing entities, fields, tables, relationships, APIs, or domain terms merely for style.

Do not redesign working architecture during feature work.

---

## 6. Backend Rules

### Controller

Controllers:

- Handle HTTP requests.
- Validate request DTOs.
- Delegate business logic to services.
- Return response DTOs.
- Use consistent HTTP status codes.

Controllers must not contain substantial business logic or complex transaction workflows.

### Service

Services:

- Contain business logic.
- Enforce business rules.
- Coordinate repositories/domain operations.
- Define meaningful transaction boundaries.
- Perform business validation.
- Coordinate authorization constraints when appropriate.

### Repository

Repositories handle persistence.

- Reuse Spring Data functionality first.
- Add custom queries only when necessary.
- Keep business rules out of repositories.
- Reuse existing repositories before creating new ones.

### Entity / DTO / Mapper

- Existing JPA entities and mappings are authoritative.
- Prefer DTOs at API boundaries.
- Reuse existing DTOs and mapper conventions.
- Do not introduce MapStruct or another mapping framework unless already used or explicitly approved.
- Do not expose JPA entities directly when DTOs are appropriate.

---

## 7. Frontend Rules

Use Vue 3 with Composition API and `<script setup>` for new code unless the existing project requires another convention.

Default stack:

```text
Vue 3
Vite
TypeScript
Vue Router
Pinia
Axios
Tailwind CSS
```

### Frontend autonomy

Frontend work is **MODERATELY AUTONOMOUS**.

The agent may decide:

- Component structure.
- Layout.
- Responsive behavior.
- Loading/empty/error states.
- Form UX.
- Reusable UI components.
- Minor accessibility and visual improvements.

The agent must not silently change:

- Backend API contracts.
- Business rules.
- Authentication semantics.
- Database semantics.
- Required user flows.

### Penpot designs

Some main screens have been designed in Penpot and will be added later.

When a Penpot reference exists, treat it as the primary visual reference for that screen.

The agent should:

1. Inspect the reference before implementation.
2. Reproduce important layout, hierarchy, spacing, components, and interaction intent.
3. Reuse existing UI components/design conventions.
4. Add responsive behavior without unnecessarily changing the intended design.

When no design exists, the agent may make reasonable UI/UX decisions.

### State

Use local component state for local concerns.

Use Pinia only for genuinely shared application state, such as authentication/session state or cross-route booking state.

Do not put every piece of UI state into Pinia.

### API

The frontend communicates with the CineBook backend API.

Never let the browser connect directly to MySQL or use server-side secrets.

Do not put TMDB or VNPay secrets in frontend code.

---

## 8. Frontend Browser Verification

For meaningful frontend work, compilation alone is not enough.

When browser tooling is available, verify the running application for relevant:

- Route navigation.
- Rendering.
- Main interaction.
- Form validation.
- API integration.
- Loading/error/empty states.
- Runtime/console errors.
- Responsive behavior when relevant.

Do not claim UI work is verified when only the source code was inspected.

---

## 9. Database Safety

MySQL is a critical source of truth.

Without explicit instruction, never:

- Drop tables.
- Truncate tables.
- Delete large amounts of data.
- Reset the database.
- Remove constraints/indexes.
- Change primary keys.
- Break foreign keys.
- Change important relationships.
- Perform irreversible data migrations.

If the current schema is insufficient:

1. Identify the requirement gap.
2. Explain why the schema cannot support it.
3. Propose the smallest safe change.
4. Get explicit approval before destructive or difficult-to-reverse structural changes.

Non-destructive changes explicitly required by the current feature may be implemented when consistent with the existing design.

---

## 10. Redis Policy

Redis is **OPTIONAL**.

Do not add Redis merely because caching sounds useful.

When used:

- MySQL remains the source of truth.
- Redis is a cache/temporary acceleration layer unless explicitly designed otherwise.
- Mutable cached data requires clear invalidation behavior.
- Do not introduce Redis distributed locking unless explicitly required.

---

## 11. Domain and Business Rules

Reuse established domain concepts such as:

```text
User / Role / RefreshToken / PasswordResetToken
Movie / Genre / MovieGenre
Cinema / Auditorium / SeatType / Seat
Showtime
DayPricingRule / TimeSlotPricingRule
SeatHold / Booking / Ticket / Payment
Voucher / Promotion (when implemented)
```

Important invariants include:

- A seat must not be sold more than once for the same showtime.
- Booking must match the selected showtime.
- Booking totals must be calculated consistently.
- Authorization must respect user roles.
- Payment and booking state must remain consistent.
- Expired seat holds must not remain valid indefinitely.

Do not invent missing business rules.

When a requirement is materially ambiguous, inspect the repository/docs first. Ask the developer only when the remaining choice affects behavior, architecture, security, data integrity, or a significant user-visible result.

---

## 12. Booking and Concurrency

Booking/seat-hold logic is concurrency-sensitive.

Never rely only on frontend availability checks.

When implementing booking behavior:

- Keep availability validation inside the appropriate transaction.
- Respect database constraints.
- Prevent duplicate sale of the same seat/showtime.
- Consider concurrent requests.
- Use appropriate locking supported by the existing design.
- Do not introduce distributed locking architecture.

Do not add `@Transactional` blindly to every method. Use meaningful business transaction boundaries.

---

## 13. API Rules

Before creating or changing an endpoint, inspect similar endpoints.

Follow existing conventions for:

- URL naming.
- HTTP methods.
- Request/response DTOs.
- Status codes.
- Validation.
- Error format.

Do not silently break an existing frontend API contract.

If frontend and backend requirements conflict, identify the mismatch before changing the contract.

---

## 14. Validation and Errors

Use Jakarta Bean Validation for request validation where appropriate.

Business validation belongs in services.

Use the existing exception-handling mechanism.

Do not introduce a competing global exception system.

Do not silently swallow exceptions or validation failures.

---

## 15. Security

Sensitive areas include:

- Passwords.
- JWTs.
- Refresh tokens.
- Password reset tokens.
- User roles/authorization.
- VNPay credentials.
- TMDB credentials.
- Database credentials.

Never:

- Log passwords.
- Log tokens unnecessarily.
- Return password hashes.
- Hard-code secrets.
- Commit credentials/private keys.
- Disable security merely to make a test pass.

Reuse the existing Spring Security design.

---

## 16. VNPay Sandbox

VNPay is used in **sandbox/test mode**.

Rules:

- Keep credentials outside source control.
- Keep payment verification on the backend.
- Do not trust frontend-provided payment success state.
- Keep booking/payment state transitions consistent.
- Never accidentally use production credentials/endpoints during development.

Follow existing payment implementation/docs when available.

---

## 17. TMDB and Data Import

TMDB is an **external data source**, not CineBook's source of truth.

Preferred flow:

```text
TMDB API
   ↓
Import / Seed workflow
   ↓
CineBook MySQL
   ↓
CineBook Backend API
   ↓
Vue Frontend
```

Do not make the frontend depend directly on TMDB for normal application data.

TMDB credentials must remain server-side.

Import/seed logic should:

- Map TMDB data into the existing schema.
- Reuse existing entities/repositories.
- Avoid unnecessary duplicates.
- Respect database constraints.
- Avoid unexpectedly overwriting manually curated data.

Large or irreversible imports require explicit instruction/approval.

---

## 18. Dependency Policy

Before adding Maven/npm dependencies:

1. Check existing dependencies.
2. Check whether Java/Spring/Vue can solve the problem.
3. Reuse existing libraries where practical.
4. Add a dependency only when it provides meaningful value.

Dependency installation is **SEMI-CONSERVATIVE**: the agent may install a clearly justified dependency needed for the current task, but should not add libraries for convenience or personal preference.

Do not upgrade unrelated dependencies during feature work.

---

## 19. AI Autonomy Matrix

| Area | Autonomy |
|---|---|
| Backend | Conservative |
| Frontend | Autonomous within project boundaries |
| Database | Conservative |
| UI/UX | Autonomous |
| Business rules | Conservative |
| Refactoring | Conservative |
| Bug fixing | Autonomous for clear/local bugs |
| Dependency installation | Semi-conservative |

**Conservative:** inspect first, reuse existing code, minimize changes, avoid assumptions.

**Autonomous:** make reasonable reversible decisions without unnecessary confirmation.

For a clear local bug, fix it directly if the cause and solution are reasonably certain. Do not turn the bug fix into unrelated refactoring.

---

## 20. AI Communication

### Language

Default user-facing responses to **Vietnamese**.

Use English for code and established technical/domain identifiers.

Do not translate code concepts inconsistently.

### Response length

Default to compact output.

For normal implementation tasks:

```text
Implemented:
- ...

Verified:
- ...

Files changed:
- ...

Notes:
- ...
```

Do not explain obvious implementation details unless asked.

Give longer explanations when there is an important architectural decision, failure, risk, or ambiguity.

---

## 21. Context Efficiency

The developer has limited AI quota and a short deadline.

For each task:

1. Start with the relevant file/module.
2. Inspect analogous implementations.
3. Inspect related files only as needed.
4. Read relevant docs only.
5. Avoid repository-wide context for small tasks.
6. Reuse existing project conventions instead of rediscovering them.

Before asking a question, search the repository and docs first.

Do not ask questions that can be answered from the codebase.

---

## 22. Task Workflow

For non-trivial tasks:

### Inspect

Read existing relevant code first.

### Scope

Identify required behavior, affected files, reusable code, and out-of-scope areas.

### Plan

Create a short actionable plan when the task is non-trivial.

### Implement

Implement the smallest complete solution. Do not modify unrelated files.

### Verify

Run relevant builds, tests, static checks, and browser verification when applicable.

### Review

Inspect the final diff for unintended changes, debug output, secrets, broken imports, duplicate logic, unnecessary dependencies, unrelated refactoring, and business-rule errors.

### Report

Return a concise Vietnamese summary.

---

## 23. Scope and Refactoring

Stay within the requested task.

Do not use a feature task as an excuse to:

- Redesign authentication.
- Rewrite the database.
- Change frontend framework.
- Upgrade Spring Boot.
- Introduce microservices.
- Refactor unrelated services/components.
- Rewrite the architecture.

Refactoring is **CONSERVATIVE**.

Refactor only when necessary for the current task, a real defect, directly relevant duplication, or a clear maintainability problem in the touched area.

Report unrelated issues instead of silently fixing them unless they block the current task.

---

## 24. Testing Strategy

Use **risk-based testing**, not coverage-driven testing.

Prioritize:

- Authentication/authorization.
- Booking.
- Seat hold/availability.
- Pricing.
- Payment state transitions.
- Validation.
- Important concurrency behavior.
- Critical edge cases.

Do not create meaningless tests solely to increase coverage.

A feature is not complete merely because it compiles.

If verification cannot be performed, say so honestly.

---

## 25. Code Quality

### Comments

Do not comment obvious code.

Use comments for non-obvious business rules, concurrency decisions, security concerns, trade-offs, or unusual framework/database workarounds.

### Naming

Use clear English names and existing project terminology.

Do not rename working concepts merely for stylistic preference.

---

## 26. Documentation

`AGENTS.md` = **AI behavior and engineering rules**.

Use `docs/` for detailed project knowledge, for example:

```text
docs/
├── architecture.md
├── database.md
├── business-rules.md
├── api.md
├── frontend.md
├── payment.md
├── tmdb-import.md
└── use-cases/
```

Do not put every database field, use case, UI specification, or implementation detail into AGENTS.md.

---

## 27. Git Rules

The developer controls Git.

Unless explicitly requested, the AI must not:

- Create/switch/delete branches.
- Commit.
- Push.
- Force-push.
- Merge.
- Rebase.
- Reset.

Normal flow:

```text
main
  ↓
dev
  ↓
Active development
  ↓
Final verification
  ↓
Developer merges dev → main
```

Treat `main` as stable/protected.

---

## 28. Secrets

Never commit or expose:

- Passwords.
- API keys.
- JWT secrets.
- Private keys.
- Database credentials.
- VNPay credentials.
- TMDB credentials.
- OAuth secrets.

Use environment variables or local secure configuration.

If a secret is discovered in tracked code: do not reproduce it in output; warn the developer and recommend moving it to secure configuration.

---

## 29. Forbidden Actions

Unless explicitly requested, never:

- Convert CineBook to microservices.
- Add Eureka, Config Server, or a separate API Gateway.
- Introduce distributed transactions or unnecessary message brokers.
- Drop/truncate/reset important database data.
- Remove important constraints/indexes.
- Silently change the database schema.
- Delete existing business logic.
- Disable security.
- Hard-code credentials.
- Upgrade unrelated dependencies.
- Perform large-scale refactoring during a feature task.
- Automatically commit/push/merge/rebase/reset Git.

---

## 30. Definition of Done

A task is complete when applicable:

- Requested functionality is implemented.
- Existing architecture/conventions are respected.
- Existing behavior is not unnecessarily broken.
- Validation and error handling are appropriate.
- Relevant tests pass.
- Backend builds successfully when applicable.
- Frontend builds/types-checks when applicable.
- Meaningful frontend work is browser-verified when tooling allows.
- No unnecessary dependency was introduced.
- No unrelated files were modified.
- No secrets were introduced.
- Final diff was reviewed.

---

## 31. Final Principle

**Explicit developer instruction > locked decisions > existing working code > project docs > simplicity > convention > cleverness.**

The goal is not the most sophisticated architecture. The goal is a **correct, maintainable, understandable, testable, visually usable, and complete CineBook graduation project delivered within the available time**.
