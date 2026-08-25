---
name: cinebook-code-quality
description: Code quality, naming conventions, commenting, and testing standards.
---

# CineBook Code Quality Rules

Canonical Architecture Reference: `docs/architecture.md`

## 1. Naming & Conventions

- Use clear English identifiers and maintain existing domain terminology (`Movie`, `Showtime`, `SeatHold`, `Booking`, `Ticket`, `Payment`).
- Do not rename existing working concepts, database columns, or API fields for stylistic preference.

## 2. Comments & Documentation

- Do not add comments for obvious code.
- Write clear comments for complex business invariants, concurrency mechanisms, security constraints, and external gateway workarounds.

## 3. Testing Standards

- CineBook uses **risk-based testing**, prioritizing:
  - Authentication and RBAC authorization paths.
  - Booking concurrency, seat-hold expiration, and ticket duplication prevention.
  - Pricing calculation and payment status transitions.
  - Input validation and exception handling envelopes.
- Unit tests use Mockito (`@ExtendWith(MockitoExtension.class)`).
- Web layer tests use `MockMvc.standaloneSetup()` for controller unit tests and `@SpringBootTest` for integration security verification.
- Always ensure `.\mvnw.cmd clean test` passes before declaring a task complete.