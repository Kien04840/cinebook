# CineBook Documentation Map

This document serves as the master navigation guide and canonical source index for the CineBook project.
AI coding agents and developers must use this map to identify the authoritative documentation required for any specific task.

---

## 1. Documentation Inventory & Canonical Sources

| Document | Primary Concern | Canonical Source For |
|---|---|---|
| `AGENTS.md` | AI agent behavior, global engineering rules, locked decisions | Project rules, autonomy matrix, locked technology stack |
| `docs/architecture.md` | System design, layering, SOLID principles, key flows | Layer responsibilities, dependency direction, domain grouping |
| `docs/database.md` | MySQL schema, tables, columns, indexes, constraints | Authoritative schema, data types, unique keys, relationships |
| `docs/business-rules.md` | System behavior, domain invariants, validation rules | Business rules, hard invariants, token lifetimes, pricing rules |
| `docs/api.md` | Public & admin REST contracts, request/response DTOs | API paths, HTTP methods, status codes, query parameters |
| `docs/payment.md` | VNPay Sandbox integration mechanics | VNPay signature hash, IPN/return handling, amount conversion |
| `docs/tmdb-import.md` | TMDB API movie & genre import workflow | TMDB mapping, dedup strategy, re-import overwrite policy |
| `docs/use-cases/authentication.md` | End-to-end authentication & user management | Auth flows, RBAC authorization matrix, password reset |
| `docs/use-cases/movie.md` | End-to-end movie discovery & administration | Public movie filters, admin movie CRUD, genre management |
| `docs/use-cases/{domain}.md` | Use cases for Booking, Cinema, Showtime, Payment, Admin | Domain-specific user journeys and operational flows |

---

## 2. Documentation Dependency Maps

### 2.1 Global Entry Point Flow
```text
AGENTS.md (Entry Point & Global Rules)
    ↓
docs/documentation-map.md (Task Routing)
    ↓
docs/architecture.md (Architectural Context)
```

### 2.2 Backend Feature Development Flow
```text
AGENTS.md
    ↓
.agents/rules/backend.md
    ↓
docs/architecture.md
    ↓
docs/database.md (Entities & Tables)
    ↓
docs/business-rules.md (Domain Invariants)
    ↓
docs/api.md (Endpoint Shape)
    ↓
docs/use-cases/{domain}.md (Business Flow)
    ↓
.agents/skills/implement-backend-feature/SKILL.md
```

### 2.3 TMDB Import Integration Flow
```text
AGENTS.md
    ↓
.agents/rules/backend.md + .agents/rules/security.md
    ↓
docs/tmdb-import.md (Field Mapping & Policy)
    ↓
docs/database.md §3.2 (movies, genres, movies_genres)
    ↓
docs/api.md §18 (Admin TMDB Endpoints)
```

### 2.4 Payment & Gateway Flow
```text
AGENTS.md
    ↓
.agents/rules/security.md
    ↓
docs/payment.md (VNPay Signature & IPN Mechanics)
    ↓
docs/business-rules.md §9 (Payment Invariants)
    ↓
docs/database.md §3.7 (payments, refunds)
    ↓
docs/api.md §9 (Payment Endpoints)
```

### 2.5 Database & Schema Evolution Flow
```text
AGENTS.md §9
    ↓
.agents/rules/database.md
    ↓
docs/database.md (Current Schema)
    ↓
docs/business-rules.md (Data Invariants)
    ↓
.agents/skills/database-change/SKILL.md
```

### 2.6 Frontend Development Flow
```text
AGENTS.md
    ↓
.agents/rules/frontend.md
    ↓
docs/api.md (API Contract)
    ↓
docs/use-cases/{domain}.md (UI Flow & Requirements)
```

---

## 3. Task → Documentation Matrix

Use this matrix to determine which documentation files MUST be loaded and which can be safely omitted to conserve context.

| Task Category | Must Read | Usually Read | Usually Not Needed |
|---|---|---|---|
| **Authentication & RBAC** | `docs/use-cases/authentication.md`<br>`.agents/rules/security.md` | `docs/business-rules.md` §2–3<br>`docs/api.md` §4, 12<br>`docs/database.md` §3.1 | `docs/payment.md`<br>`docs/tmdb-import.md` |
| **Movie & Genre Management** | `docs/use-cases/movie.md`<br>`.agents/rules/backend.md` | `docs/database.md` §3.2<br>`docs/business-rules.md` §4<br>`docs/api.md` §5 | `docs/payment.md`<br>`docs/use-cases/authentication.md` |
| **TMDB Import / Sync** | `docs/tmdb-import.md`<br>`.agents/rules/backend.md` | `docs/database.md` §3.2<br>`docs/api.md` §18<br>`docs/architecture.md` §8 | `docs/payment.md`<br>`docs/use-cases/authentication.md` |
| **Cinema, Auditorium, Seat** | `docs/business-rules.md` §5<br>`docs/database.md` §3.3 | `docs/api.md` §6<br>`docs/architecture.md` §6 | `docs/payment.md`<br>`docs/tmdb-import.md` |
| **Showtime & Scheduling** | `docs/business-rules.md` §6<br>`docs/database.md` §3.4 | `docs/api.md` §7<br>`docs/architecture.md` §6 | `docs/payment.md`<br>`docs/tmdb-import.md` |
| **Pricing Rules** | `docs/business-rules.md` §7<br>`docs/database.md` §3.5 | `docs/architecture.md` §6 | `docs/tmdb-import.md`<br>`docs/payment.md` |
| **Seat Hold & Booking** | `docs/use-cases/booking.md`<br>`docs/business-rules.md` §8<br>`docs/database.md` §3.6<br>`.agents/rules/backend.md` | `docs/api.md` §8<br>`docs/architecture.md` §7.3 | `docs/tmdb-import.md`<br>`docs/use-cases/authentication.md` |
| **Payment (VNPay)** | `docs/use-cases/payment.md`<br>`docs/payment.md`<br>`docs/business-rules.md` §9<br>`.agents/rules/security.md` | `docs/database.md` §3.7<br>`docs/api.md` §9<br>`docs/architecture.md` §7.4 | `docs/tmdb-import.md`<br>`docs/use-cases/movie.md` |
| **Promotion & Voucher** | `docs/use-cases/promotion.md`<br>`docs/business-rules.md` §10<br>`docs/database.md` §3.8 | `docs/api.md` §10<br>`docs/architecture.md` §6 | `docs/tmdb-import.md`<br>`docs/payment.md` |
| **Reporting & Dashboard** | `docs/use-cases/reporting.md`<br>`.agents/rules/backend.md`<br>`.agents/rules/security.md` | `docs/api.md` §19<br>`docs/database.md`<br>`docs/business-rules.md` | `docs/tmdb-import.md`<br>`docs/payment.md` |

| **Database Schema Change** | `docs/database.md`<br>`.agents/rules/database.md`<br>`.agents/skills/database-change/SKILL.md` | `docs/business-rules.md`<br>`docs/architecture.md` | `docs/payment.md`<br>`docs/tmdb-import.md` |

| **Frontend UI Implementation** | `.agents/rules/frontend.md`<br>`docs/api.md`<br>`docs/use-cases/{domain}.md` | `docs/architecture.md` §5 | `docs/database.md`<br>`docs/tmdb-import.md` |

---

## 4. Skill → Documentation Mapping

| Skill | Procedural Focus | Bound Documentation Dependencies |
|---|---|---|
| `implement-backend-feature` | Standard bottom-up implementation | `docs/architecture.md`<br>`docs/database.md`<br>`docs/business-rules.md`<br>`docs/api.md`<br>`.agents/rules/backend.md` |
| `database-change` | Schema addition & entity alignment | `docs/database.md`<br>`docs/business-rules.md`<br>`.agents/rules/database.md` |
| `feature-development` (workflow) | End-to-end task execution lifecycle | `docs/documentation-map.md`<br>`AGENTS.md`<br>`.agents/rules/*` |