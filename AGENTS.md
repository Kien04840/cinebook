# AGENTS.md — CineBook AI Coding Rules

## 1. Purpose

CineBook is a **monolithic graduation project** with a short delivery timeline (~1 month).

The AI agent must optimize for:
- Correct business functionality.
- Fast, focused implementation without unnecessary token/context overhead.
- Maintainable, understandable, and consistent code.
- Safe changes that preserve existing data and behavior.

This file defines **how the AI works and global project rules**. Detailed domain and architectural knowledge lives in `docs/`.

---

## 2. Instruction Priority

When rules conflict, use this order:
1. Explicit developer instruction for the current task.
2. Locked decisions in this file.
3. Existing working code and conventions in the repository.
4. Relevant `docs/` documentation (see `docs/documentation-map.md`).
5. Framework / library best practices.
6. General AI preference.

Do not redesign working code merely because another approach is theoretically cleaner.

---

## 3. Locked Project Decisions

| Area | Decision |
|---|---|
| Architecture | Monolith (Single deployable Spring Boot unit) |
| Backend architecture | Layered Architecture (Controller → Service → Repository → DB) |
| Java | 21 |
| Backend framework | Spring Boot + Spring MVC |
| Persistence | Spring Data JPA / Hibernate |
| Build tool | Maven |
| Database | MySQL 8 (Single source of truth) |
| Validation | Jakarta Bean Validation |
| Security | Spring Security + stateless JWT (15m access / 7d refresh) |
| Frontend | Vue 3 (Composition API, `<script setup>`) |
| Frontend build & lang | Vite + TypeScript |
| Frontend routing & state | Vue Router + Pinia |
| Frontend API & styling | Axios + Tailwind CSS |
| Payment gateway | VNPay Sandbox |
| Movie data source | TMDB API (Server-side import/seed workflow only) |
| Redis | Optional (Cache only) |

Do not upgrade unrelated dependencies or introduce unapproved frameworks.

---

## 4. Core Architecture Rules

CineBook uses classic **Layered Architecture** with strict dependency direction (Clean Architecture Dependency Rule):

```text
Controller  →  Service  →  Repository  →  Database
```

- High-level business policies (Services) must never depend on low-level presentation/HTTP concerns.
- Controllers stay thin: HTTP handling, DTO validation, service delegation, status mapping.
- Services own business logic, transaction boundaries (`@Transactional`), and invariant enforcement.
- Repositories handle persistence only via Spring Data JPA.

**Forbidden Architectural Patterns**:
- Microservices, Eureka / Consul service discovery.
- Separate API Gateway service or Spring Cloud Config Server.
- Distributed transactions (2PC, Saga) or message brokers (Kafka, RabbitMQ).
- Distributed locking infrastructure (Redis locks).

---

## 5. Existing Code & Database Are Authoritative

The database schema and existing JPA entities are the primary truth.
Before implementing any feature:
1. Inspect the relevant entities and repository interfaces.
2. Inspect analogous existing services, DTOs, mappers, and controllers.
3. Reuse established conventions and exception classes (`AppException` hierarchy).
4. Do not duplicate domain representations or rename working concepts for stylistic preference.

---

## 6. Database Safety

MySQL is a critical source of truth. Without explicit developer instruction, **NEVER**:
- `DROP TABLE`, `TRUNCATE TABLE`, or perform bulk deletions.
- Reset the database or execute destructive migrations.
- Remove foreign keys, unique constraints (`uk_*`), or indexes.
- Change primary key strategies (UUID for entities, `bigint` for `seat_holds`).

If schema expansion is required, follow `.agents/rules/database.md` and `.agents/skills/database-change/SKILL.md`.

---

## 7. Security & Secrets

- **Zero hard-coded credentials**: `TMDB_API_KEY`, `JWT_SECRET`, database passwords, and VNPay merchant keys must remain in environment variables (`${VAR_NAME:default}`).
- **Sensitive data protection**: Never log passwords, tokens, or payment hash secrets; never return password hashes.
- **Authorization**: Enforce role-based access control (RBAC). Public endpoints allow anonymous access; `/api/v1/admin/**` strictly requires `ADMIN`.
- See `.agents/rules/security.md` for full security constraints.

---

## 8. External Integrations (Summary)

- **TMDB**: External data source, not runtime source of truth. Runs server-side. Re-import updates movie metadata but strictly preserves internal UUID, status, soft-delete state, and timestamps. (Canonical guide: `docs/tmdb-import.md`).
- **VNPay**: Sandbox mode only. Cryptographic signature and amount verification must happen on the backend via IPN callback. (Canonical guide: `docs/payment.md`).
- **Redis**: Optional. MySQL remains source of truth; do not add Redis locking.

---

## 9. AI Autonomy Matrix

| Area | Autonomy Level | Guidelines |
|---|---|---|
| **Backend Architecture** | Conservative | Follow Layered Architecture, reuse existing classes, no unauthorized refactoring. |
| **Frontend UI/UX** | Autonomous within boundaries | Decide layout, component hierarchy, responsive styling, loading/error states. Follow Penpot if present. |
| **Database Schema** | Conservative | Minimal additive non-destructive changes only; requires approval for structural alterations. |
| **Business Invariants** | Conservative | Enforce all rules from `docs/business-rules.md`; never invent missing rules. |
| **Bug Fixing** | Autonomous for local bugs | Fix root cause directly without expanding scope into unrelated refactoring. |
| **Dependencies** | Semi-conservative | Install only justified, necessary libraries; never add dependencies for preference. |

---

## 10. AI Communication Rules

- **Response Language**: Default user-facing responses to **Vietnamese**. Code, identifiers, and established technical domain terms remain in English.
- **Output Style**: Concise, compact, structured:
  ```text
  Implemented:
  - ...
  Verified:
  - ...
  Files changed:
  - ...
  ```
- Do not explain obvious implementation details unless an architectural decision, risk, or ambiguity is involved.

---

## 11. Testing Strategy

CineBook uses **risk-based testing**:
- Unit test Service logic with Mockito (`@ExtendWith(MockitoExtension.class)`).
- Unit test Controllers with `MockMvc.standaloneSetup()`.
- Integration test security filter chains with `@SpringBootTest`.
- Prioritize: Auth/RBAC, seat hold/booking concurrency, payment transitions, and validation errors.
- Always verify that `.\mvnw.cmd clean test` passes before declaring completion.

---

## 12. Git & Version Control Rules

The developer controls Git. Unless explicitly requested, the AI must **NEVER**:
- Create, switch, merge, rebase, reset, or delete branches.
- Execute `git commit`, `git push`, or `git push --force`.

---

## 13. Documentation & Task Routing Guide

To maintain context efficiency, **do not load all documentation at once**. Load only what the current task requires:

| Task Type | Key Rules & Skills | Authoritative Docs |
|---|---|---|
| **Backend Feature** | `.agents/rules/backend.md`<br>`.agents/skills/implement-backend-feature/SKILL.md` | `docs/architecture.md`<br>`docs/database.md`<br>`docs/business-rules.md`<br>`docs/api.md` |
| **Authentication & RBAC** | `.agents/rules/security.md`<br>`.agents/rules/authentication.md` | `docs/use-cases/authentication.md`<br>`docs/api.md` §4 |
| **Movie & Genre / TMDB** | `.agents/rules/backend.md` | `docs/use-cases/movie.md`<br>`docs/tmdb-import.md`<br>`docs/api.md` §5, 18 |
| **Seat Hold & Booking** | `.agents/rules/backend.md` | `docs/business-rules.md` §8<br>`docs/database.md` §3.6<br>`docs/api.md` §8 |
| **Payment (VNPay)** | `.agents/rules/security.md` | `docs/payment.md`<br>`docs/business-rules.md` §9<br>`docs/api.md` §9 |
| **Database Schema** | `.agents/rules/database.md`<br>`.agents/skills/database-change/SKILL.md` | `docs/database.md`<br>`docs/business-rules.md` |
| **Frontend UI** | `.agents/rules/frontend.md` | `docs/api.md`<br>`docs/use-cases/{domain}.md` |
| **Master Navigation** | `.agents/workflows/feature-development.md` | `docs/documentation-map.md` |

---

## 14. Forbidden Actions

Unless explicitly requested by the developer, never:
1. Convert the system to microservices or introduce message brokers.
2. Drop, truncate, or reset database tables or delete existing business logic.
3. Disable security or bypass service validation.
4. Hard-code credentials or API tokens in source files.
5. Perform large-scale refactoring during a scoped feature task.
6. Commit or push changes to Git.

---

## 15. Definition of Done

A task is complete when:
- Requested functionality is correctly implemented following existing conventions.
- Domain invariants and security constraints are preserved.
- Relevant unit and integration tests are written and pass (`.\mvnw.cmd clean test`).
- No unnecessary dependencies or secrets are introduced.
- Associated documentation is updated if contracts or schemas changed.
- Final diff is reviewed and summarized in Vietnamese.

---

## 16. Final Principle

**Explicit developer instruction > locked decisions > existing working code > project docs > simplicity > convention > cleverness.**