---
name: cinebook-backend
description: Mandatory rules for implementing, modifying, and testing Spring Boot backend code in CineBook.
---

# CineBook Backend Rules

Canonical Architecture Reference: `docs/architecture.md`
Canonical Business Rules: `docs/business-rules.md`
Canonical API Reference: `docs/api.md`

## 1. Layered Architecture & Responsibilities

- **Controller**: Handle HTTP requests, validate request DTOs (`@Valid`), delegate directly to services, return response DTOs with consistent HTTP status codes. No business logic or `@Transactional`.
- **Service**: Enforce business rules, orchestrate repositories/domain operations, define transaction boundaries (`@Transactional`), perform business validation, and handle external integrations (VNPay, TMDB).
- **Repository**: Persistence only via Spring Data JPA. Custom queries only when necessary. Keep business rules out of repositories.
- **Entity / DTO / Mapper**:
  - Existing JPA entities and mappings are authoritative (`docs/database.md`).
  - Always use DTOs at API boundaries. Do not expose JPA entities directly.
  - Manual mapping via `@Component` Spring bean mappers (no MapStruct unless already in project).

## 2. API Conventions

- Base path: `/api/v1/...`.
- Follow resource naming conventions defined in `docs/api.md`.
- Use consistent HTTP methods (`GET`, `POST`, `PUT`, `DELETE`).
- Return standard error envelope on failures (`AppException` hierarchy -> `GlobalExceptionHandler`).
- Protect `/api/v1/admin/**` endpoints with `ROLE_ADMIN`.

## 3. Concurrency & Transactions

- Availabilities and state-sensitive operations (seat holds, booking payments) must run inside explicit `@Transactional` service boundaries.
- Respect unique constraints (`uk_seat_holds_showtime_seat`, `uk_tickets_booking_seat`, `uk_movies_tmdb_id`).
- Respect optimistic locking via `@Version Long version` on aggregate entities.
- Never rely on client-side validation for availability or business constraints.
- Do NOT introduce distributed locking (Redis locks) or message queues.

## 4. Refactoring & Scope Constraints

- Stay strictly within the assigned task.
- Refactoring is conservative: do not rewrite working services, security mechanisms, or database schema during feature tasks.