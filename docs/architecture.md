# CineBook Architecture

## 1. Overview

CineBook is a **monolithic** online movie ticket booking system (graduation project).

| Component        | Choice                          |
|------------------|---------------------------------|
| Backend          | Single Spring Boot application  |
| Frontend         | Vue 3 SPA                       |
| Database         | MySQL (source of truth)         |
| Payment          | VNPay Sandbox                   |
| Movie data source| TMDB API (import/seed only)     |
| Redis            | Optional                        |

**Primary goals**: correct business functionality, maintainable code, fast delivery within ~1 month.  
Do **not** optimize for hypothetical enterprise-scale requirements.

---

## 2. Architecture Style (Locked)

### 2.1 Monolith + Layered Architecture

CineBook uses **Layered Architecture** inside a single deployable unit (monolith).

```text
Controller  →  Service  →  Repository  →  Database
```

This decision is **locked** in `AGENTS.md`. Do not migrate to microservices, hexagonal ports/adapters as a full rewrite, or any distributed architecture.

### 2.2 Alignment with Clean Architecture Principles

Although the project is explicitly **Layered**, it follows the core **Dependency Rule** of Clean Architecture:

> Source code dependencies must point only inward, toward higher-level policies.

| Clean Architecture Concept     | CineBook Mapping                          |
|--------------------------------|-------------------------------------------|
| Entities (Enterprise Business Rules) | JPA Entities + domain invariants enforced in Services |
| Use Cases (Application Business Rules) | Service layer (business logic & transaction boundaries) |
| Interface Adapters             | Controllers + DTOs + Mappers              |
| Frameworks & Drivers           | Spring Boot, Spring Data JPA, Spring Security, MySQL, VNPay, TMDB |

**Dependency direction (must never be reversed):**

```text
Frameworks / Drivers
        ↑
Interface Adapters (Controller, DTO, Mapper)
        ↑
Application Business Rules (Service / Use Case)
        ↑
Enterprise Business Rules (Entity + core invariants)
```

In practice this means:

- Controllers depend on Services (never the reverse).
- Services depend on Repository interfaces / Spring Data repositories.
- Entities and core domain rules do not depend on Spring, HTTP, or external APIs.
- External concerns (VNPay, TMDB, Redis) are isolated behind service methods; the rest of the system does not know their concrete details.

### 2.3 Why not full Clean / Hexagonal?

Full ports-and-adapters or pure Clean Architecture would require significant additional abstraction layers. Given the short delivery timeline and existing working code, the project deliberately stays with **classic Layered Architecture** while still respecting the Dependency Rule and SOLID.

---

## 3. SOLID Principles Applied

Every new or modified code must respect SOLID:

| Principle | How it is applied in CineBook |
|-----------|-------------------------------|
| **S** – Single Responsibility | Controller only handles HTTP. Service only contains business logic. Repository only handles persistence. One reason to change per class. |
| **O** – Open/Closed | Prefer extension (new service methods, new strategies) over modification of existing stable logic. Pricing rules, payment gateways are candidates for strategy-style extension when needed. |
| **L** – Liskov Substitution | Any implementation of a repository or service interface must be substitutable without breaking callers. |
| **I** – Interface Segregation | Keep interfaces focused. Do not force clients to depend on methods they do not use. Prefer small, role-specific service methods over god interfaces. |
| **D** – Dependency Inversion | High-level modules (Services) depend on abstractions (Repository interfaces, Spring Data). Concrete frameworks (JPA, VNPay SDK) are details injected from the outside. |

**Practical rules for the AI:**

- Never put business rules inside Controllers or Repositories.
- Never let a Service depend on a concrete Controller or HTTP concern.
- Prefer constructor injection.
- When introducing a new external system (e.g. another payment gateway later), isolate it behind an interface owned by the application layer.

---

## 4. Layer Responsibilities

| Layer          | Responsibility                                                                 | Forbidden                                      |
|----------------|--------------------------------------------------------------------------------|------------------------------------------------|
| **Controller** | Receive HTTP request, validate request DTO (Jakarta Validation), call Service, map to response DTO, return proper status code | Business logic, `@Transactional`, complex rules, direct Repository access |
| **Service**    | Enforce business rules, coordinate multiple repositories, define transaction boundaries, perform business validation, orchestrate external calls (VNPay, TMDB) | HTTP concerns, raw SQL, framework-specific request objects |
| **Repository** | Persistence only (Spring Data JPA). Custom queries when necessary              | Business rules, transaction orchestration beyond simple save/find |
| **Entity**     | JPA mapping + basic domain representation. Soft-delete / version fields where present | Workflow logic, external API calls             |
| **DTO**        | API contract (request / response). Validation annotations                      | JPA annotations, business logic                |
| **Mapper**     | Convert Entity ↔ DTO (manual or existing convention)                           | Business decisions                             |

Supporting packages (existing or allowed):
- Security (JWT filters, SecurityConfig)
- Exception handling (global handler)
- Configuration
- Utility / helper (pure functions only)

---

## 5. High-Level System Diagram

```text
┌──────────────────┐
│   Vue 3 + Vite   │  Frontend (SPA)
│  TypeScript      │
│  Pinia / Axios   │
└────────┬─────────┘
         │ REST / HTTPS
         ▼
┌────────────────────────────────────────────────────┐
│                 Spring Boot Monolith               │
│                                                    │
│  ┌─────────────┐   ┌─────────────┐   ┌──────────┐  │
│  │ Controllers │ → │  Services   │ → │Repository│  │
│  │ + DTOs      │   │ (Use Cases) │   │ + Entity │  │
│  └─────────────┘   └──────┬──────┘   └────┬─────┘  │
│                           │               │        │
│  ┌─────────────┐   ┌──────▼──────┐   ┌────▼─────┐  │
│  │  Security   │   │  Domain     │   │   JPA    │  │
│  │  (JWT)      │   │  Rules      │   │ Hibernate│  │
│  └─────────────┘   └─────────────┘   └────┬─────┘  │
└───────────────────────────────────────────┼────────┘
                                            │
                                            ▼
                                      ┌──────────┐
                                      │  MySQL   │
                                      └──────────┘

External systems (isolated behind Services):
  • VNPay Sandbox  – payment
  • TMDB API       – movie import / seed
  • Redis          – optional cache only
```

---

## 6. Domain Organization

Logical groups based on the existing database schema:

```text
Identity
├── users, roles, user_roles
├── refresh_tokens
└── password_reset_tokens

Movie
├── movies
├── genres
└── movies_genres

Cinema
├── cinemas
├── auditoriums
├── seat_types
└── seats

Showtime
└── showtimes

Pricing
├── day_pricing_rules
└── time_slot_pricing_rules

Booking
├── seat_holds
├── bookings
├── tickets
└── booking_promotions

Payment
├── payments
└── refunds

Promotion
└── promotions
```

Package structure should follow these domains when practical, while remaining consistent with whatever package layout already exists in the repository.

---

## 7. Key Flows (Overview)

### 7.1 Request Flow

```text
HTTP Request
    → Spring Security (JWT authentication / authorization)
    → Controller (validate DTO)
    → Service (business rules + @Transactional boundary)
    → Repository
    → JPA / Hibernate
    → MySQL
```

### 7.2 Authentication Flow

- Register / Login → issue JWT access token + store refresh token.
- Refresh token stored in `refresh_tokens` table (SHA-256 hashed).
- Password reset via `password_reset_tokens` (SHA-256 hashed).
- Roles via `roles` + `user_roles`.
- Subsequent requests carry JWT; Spring Security enforces role-based access.
- Token lifetimes (finalized): Access Token = 15 minutes, Refresh Token = 7 days, Password Reset Token = 15 minutes.

### 7.3 Booking Flow

```text
1. Select movie → showtime → seats
2. Create / extend SeatHold (temporary)
3. Confirm → create Booking (HOLD / PENDING)
4. Initiate payment → VNPay
5. VNPay callback / IPN → backend verifies signature
6. On valid payment → Booking status = PAID, tickets confirmed
```

Critical invariants (must be enforced in Service + DB constraints):

- One seat can be sold only once per showtime.
- Seat holds must expire; they must not remain valid forever.
- Booking total must be calculated consistently with pricing rules.
- Payment status and Booking status must stay consistent.

### 7.4 Payment Flow

```text
Backend creates payment request
    → redirect user to VNPay Sandbox
    → user pays
    → VNPay callback / IPN hits Backend
    → Backend verifies signature & amount
    → update Payment + Booking in the same transaction
```

- Payment verification **always** happens on the backend.
- Frontend success flag is never trusted alone.
- Full VNPay details belong in `docs/payment.md`.

---

## 8. External Integrations

### TMDB

```text
TMDB API → Import/Seed workflow (backend only) → MySQL → REST API → Frontend
```

- TMDB is a data source, **not** the runtime source of truth.
- Credentials stay server-side.
- Import must map into existing schema and avoid unwanted overwrites.

### VNPay

- Sandbox mode only.
- Credentials outside source control.
- Detailed integration → `docs/payment.md`.

### Redis

- **Optional**.
- MySQL remains source of truth.
- Use only for cache or short-lived temporary data with clear invalidation.
- Do not introduce Redis distributed locking unless explicitly required.

---

## 9. Technology Stack (Locked)

| Area              | Decision                                      |
|-------------------|-----------------------------------------------|
| Java              | 21                                            |
| Backend           | Spring Boot + Spring MVC                      |
| Persistence       | Spring Data JPA / Hibernate                   |
| Build             | Maven                                         |
| Database          | MySQL                                         |
| Validation        | Jakarta Bean Validation                       |
| Security          | Spring Security + existing JWT architecture   |
| Frontend          | Vue 3 + Vite + TypeScript + Pinia + Axios + Tailwind + Vue Router |
| Payment           | VNPay Sandbox                                 |
| Movie data        | TMDB API + import/seed                        |
| Redis             | Optional                                      |

Do **not** introduce: microservices, Eureka, Config Server, separate API Gateway, Kafka/RabbitMQ for business flows, distributed transactions, or distributed locking infrastructure.

---

## 10. Design Principles Summary

1. **Existing code & schema are authoritative** — inspect before inventing.
2. **Layered Architecture + Dependency Rule** — dependencies point inward only.
3. **SOLID** — especially Single Responsibility and Dependency Inversion.
4. **Business rules live in Services** — Controllers stay thin.
5. **Database constraints protect invariants** — application code must still enforce them.
6. **Conservative changes** — minimize risk to data integrity and existing behavior.
7. **No premature abstraction** — do not add ports/adapters or extra layers “just in case”.

---

## 11. Documentation Boundaries

| File                    | Responsibility                                      |
|-------------------------|-----------------------------------------------------|
| `AGENTS.md`             | How the AI must work (rules, autonomy, forbidden actions) |
| `docs/documentation-map.md` | Master navigation, canonical source index & task matrix |
| `docs/architecture.md`  | How the system is structured (this file)            |
| `docs/database.md`      | How data is organized (tables, constraints, relations) |
| `docs/business-rules.md`| How the system must behave (invariants, rules)      |
| `docs/api.md`           | API contracts                                       |
| `docs/payment.md`       | VNPay integration details                           |
| `docs/tmdb-import.md`   | TMDB import/seed workflow                           |
| `docs/use-cases/*.md`   | End-to-end scenarios                                |
| `docs/frontend.md`      | Frontend conventions (created later)                |

Avoid duplicating the same rule in multiple places with slightly different wording.

---

## 12. Finalized Architectural Decisions (Backend V1)

All critical architectural decisions have been locked and implemented:

- **Seat-Hold Expiration Duration**: Strictly 5 minutes (`holdExpiresAt = now.plusMinutes(5)`).
- **Pricing Precedence**: Ticket gross price = showtime base price + seat type modifier + day/time rules.
- **Refund Policy**: Customer refund $\ge 2$ hours before showtime; Admin refund anytime for paid/orphaned bookings.
- **Promotion / Voucher Stacking**: Strictly at most 1 promotion per booking (no stacking in V1).
- **Concurrency Locking Strategy**: Database unique constraints (`uk_*`) + JPA pessimistic write locks (`PESSIMISTIC_WRITE`) for seat allocation, IPN settlement, and promotion quotas.
- **Redis**: Optional (MySQL 8 is the single authoritative source of truth).


---

*This document describes the locked architecture of CineBook.  
Any change to architecture style (e.g. moving to full Clean/Hexagonal or microservices) requires explicit developer approval.*
