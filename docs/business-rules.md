# CineBook Business Rules

## 1. Purpose

This file defines **how CineBook must behave**.

It is the primary reference for domain invariants and business validation that Services must enforce.

Rules here are derived from:
- Locked decisions in `AGENTS.md`
- Existing database constraints (`docs/database.md`)
- Known flows in `docs/architecture.md`

**Do not invent missing rules.**  
If a rule is not finalized, it is marked as `TODO / DECISION REQUIRED`.

---

## 2. Cross-Cutting Rules

### 2.1 Authorization

- Every protected operation must respect the authenticated user’s roles.
- Users may only access/modify their own bookings unless they have an administrative role.
- Administrative operations (manage movies, cinemas, showtimes, promotions, users, …) require the appropriate role.
- Never trust client-supplied user identity; always take identity from the security context (JWT).

### 2.2 Validation

- Request DTOs are validated with Jakarta Bean Validation at the Controller boundary.
- Business validation (availability, status transitions, totals, ownership, …) belongs in the Service layer.
- Validation failures must not be silently swallowed.

### 2.3 Data Integrity

- Database constraints are the last line of defense; application code must still enforce the same invariants.
- Soft-deleted records (`deleted_at IS NOT NULL`) are excluded from normal business queries.
- Monetary amounts must never be negative.
- Optimistic locking (`version`) must be used on concurrent update paths of aggregate roots that carry a version column.

### 2.4 Secrets & Security

- Passwords are stored only as hashes; never log or return password hashes.
- JWT, refresh tokens, password-reset tokens must not be logged unnecessarily.
- VNPay and TMDB credentials stay server-side and outside source control.
- Frontend must never receive secrets or connect directly to MySQL / payment gateway credentials.

---

## 3. Identity & Authentication

### 3.1 Users

- Email is unique.
- Phone is unique when present.
- Password must be hashed before persistence.
- A user can have multiple roles (via `user_roles`).
- Soft-deleted users cannot log in or perform normal actions.

### 3.2 Tokens

- Refresh tokens are stored as hashes in `refresh_tokens`.
- Password-reset tokens are stored as hashes in `password_reset_tokens`.
- A token that is expired, revoked, or already used is invalid.
- **Token lifetimes (Finalized)**:
  - Access Token: 15 minutes (`900000` ms)
  - Refresh Token: 7 days (`604800000` ms, SHA-256 hashed, rotated upon each use)
  - Password Reset Token: 15 minutes (`900000` ms, SHA-256 hashed, single-use)

### 3.3 Roles

- Role names are unique.
- Authorization decisions are based on the roles attached to the authenticated user.

---

## 4. Movie

- A movie may belong to multiple genres (`movies_genres`).
- `tmdb_id` is unique when present (one CineBook movie per TMDB movie).
- Soft-deleted movies are hidden from public listings.
- Movie status controls visibility (exact allowed values are application enums — inspect code, do not invent).

```text
TODO / DECISION REQUIRED:
- Exact allowed movie status values and their transition rules
- Whether manually curated fields may be overwritten by TMDB import
```

---

## 5. Cinema, Auditorium, Seat

### 5.1 Cinema & Auditorium

- An auditorium belongs to exactly one cinema.
- Auditorium name is unique within a cinema.
- Soft-deleted cinemas/auditoriums are excluded from booking flows.
- `rows_count` and `columns_count` must be > 0.

### 5.2 Seat & Seat Type

- A seat belongs to exactly one auditorium and one seat type.
- Seat position (`row_label` + `seat_number`) is unique within an auditorium.
- Seat type has a `price_modifier` ≥ 0.
- Seats with non-bookable status cannot be held or sold.

```text
TODO / DECISION REQUIRED:
- Exact seat status values (AVAILABLE, BLOCKED, …) and which statuses allow holding/selling
```

---

## 6. Showtime

- A showtime belongs to exactly one movie and one auditorium.
- `end_time` must be greater than `start_time`.
- `base_price` must be ≥ 0.
- Only showtimes in a bookable status can accept new holds/bookings.

```text
TODO / DECISION REQUIRED:
- Exact showtime status values and transition rules
- Whether overlapping showtimes in the same auditorium are forbidden (and how overlap is defined)
```

---

## 7. Pricing

Pricing is composed of:

- Showtime `base_price`
- Day-of-week modifier (`day_pricing_rules`)
- Time-slot modifier (`time_slot_pricing_rules`)
- Seat-type modifier (`seat_types.price_modifier`)

```text
TODO / DECISION REQUIRED:
- Exact formula / precedence:
  e.g. final = base × day_modifier × time_modifier + seat_modifier
  or other combination
- Whether modifiers are multiplicative, additive, or mixed
- Behavior when no matching day or time-slot rule exists
```

Until the formula is finalized, any pricing calculation code must be clearly marked and must not be treated as final business truth.

---

## 8. Booking & Seat Hold (Critical)

### 8.1 Core Invariants

1. **A seat must not be sold more than once for the same showtime.**
2. A booking always belongs to one user and one showtime.
3. Booking total must be calculated consistently with the pricing rules in force at confirmation time.
4. Expired seat holds must not remain valid.
5. Payment status and booking status must stay consistent.

### 8.2 Seat Hold

- A hold is recorded in `seat_holds` with a unique constraint on `(showtime_id, seat_id)`.
- Hold creation must run inside a transaction that also checks current availability (existing holds + paid tickets).
- Frontend availability checks are **never** sufficient; backend must re-validate.
- When a hold expires, the seat becomes available again for other users.

```text
TODO / DECISION REQUIRED:
- Exact seat-hold duration (minutes)
- Whether hold can be extended and under what conditions
- Cleanup strategy for expired holds (scheduled job vs lazy check)
```

### 8.3 Booking Lifecycle (High Level)

```text
HOLD / PENDING_PAYMENT  →  PAID  →  (optional) CANCELLED
                ↘ FAILED / EXPIRED
```

- A booking moves to **PAID** only when a payment is verified as successful by the backend.
- Cancellation rules (who can cancel, until when, refund eligibility) are not fully finalized.

```text
TODO / DECISION REQUIRED:
- Exact booking_status values and allowed transitions
- Cancellation window and refund policy
- Whether a booking can have multiple payment attempts
```

### 8.4 Concurrency

- Availability validation and hold/ticket creation must occur inside an appropriate transaction boundary.
- Respect the unique constraint `uk_seat_holds_showtime_seat` and optimistic locking where present.
- Do not introduce distributed locking (Redis locks, etc.) unless explicitly required and approved.
- Do not put `@Transactional` on every method; use meaningful business boundaries.

### 8.5 Tickets

- A ticket belongs to one booking and one seat.
- Unique constraint `(booking_id, seat_id)` prevents duplicate tickets inside the same booking.
- Ticket price is stored at creation time (snapshot).
- QR code is unique when present.

---

## 9. Payment

### 9.1 Core Rules

- Payment verification **must** happen on the backend.
- Frontend-reported “payment success” is never trusted alone.
- Booking becomes PAID only after a valid, verified payment.
- Payment amount must match the booking total (or the amount that was authorized).
- Credentials and signature verification stay server-side.
- VNPay is used in **sandbox** mode during development; never accidentally point to production.

### 9.2 Consistency

- Payment status and Booking status must be updated consistently (preferably in the same transaction when the callback is processed).
- At most one refund per payment (`uk_refunds_payment`).

```text
TODO / DECISION REQUIRED:
- Exact payment_status values and transitions
- Timeout / abandoned payment handling
- Partial refund rules
- Whether multiple payment records per booking are allowed
```

Detailed VNPay request/callback/signature handling belongs in `docs/payment.md`.

---

## 10. Promotion / Voucher

- Promotion code is unique.
- `end_at` must be greater than `start_at`.
- `used_count` must not exceed `usage_limit` when a limit is set.
- Discount value and related amounts must be ≥ 0.
- When a promotion is applied, the discount amount is snapshotted in `booking_promotions`.

```text
TODO / DECISION REQUIRED:
- Stacking rules (can multiple promotions apply to one booking?)
- Which discount types exist (PERCENT, FIXED, …) and calculation details
- Eligibility rules (first booking only, specific movies, min amount, …)
```

---

## 11. TMDB Import

- TMDB is an external data source, not the runtime source of truth.
- Import/seed runs on the backend only.
- Imported data must map into the existing schema.
- Avoid creating unnecessary duplicates (`tmdb_id` uniqueness enforced).
- **Re-import / Update Policy (Finalized)**:
  - Re-importing a movie by `tmdbId` synchronizes and overwrites all TMDB-sourced metadata (`title`, `originalTitle`, `overview`, `durationMinutes`, `director`, `actors`, `country`, `language`, `releaseDate`, `posterUrl`, `backdropUrl`, `trailerUrl`, `genres`).
  - Re-importing strictly **preserves** CineBook lifecycle and business fields: `id` (internal UUID), `tmdbId`, `status`, `deletedAt`, `createdAt`, `version`.
  - Movies marked `HIDDEN` or soft-deleted are never automatically reverted or un-deleted by TMDB re-import.

Detailed import workflow belongs in `docs/tmdb-import.md`.

---

## 12. Soft Delete Behavior

Entities that support soft delete (`users`, `movies`, `cinemas`, `auditoriums`, …):

- Soft-deleted records are invisible to normal public and booking flows.
- Hard delete is forbidden without explicit instruction.
- Related active bookings/showtimes that reference a soft-deleted entity must be handled carefully (usually the parent should not be soft-deleted while active children exist, or the use case must define the behavior).

```text
TODO / DECISION REQUIRED:
- Cascading soft-delete rules (if any)
- Whether an admin can soft-delete a cinema that still has future showtimes
```

---

## 13. Summary of Hard Invariants (Must Never Be Violated)

| # | Invariant |
|---|-----------|
| 1 | A seat cannot be sold more than once for the same showtime |
| 2 | Booking always references a valid user and showtime |
| 3 | Booking total is calculated consistently with pricing rules |
| 4 | Payment success is verified on the backend before booking becomes PAID |
| 5 | Payment status and booking status remain consistent |
| 6 | Expired seat holds are no longer valid |
| 7 | Authorization respects the authenticated user’s roles |
| 8 | Secrets (passwords, tokens, gateway keys) are never exposed or logged carelessly |
| 9 | Monetary amounts are never negative |
| 10 | Database unique constraints and foreign keys are respected |

---

## 14. How the AI Should Use This File

1. Before implementing a feature, read the relevant section of this file.
2. If a rule is marked `TODO / DECISION REQUIRED`, do **not** invent a value.
3. Prefer asking the developer or inspecting existing code over guessing.
4. When a new rule is decided, update this file so it remains the single source of behavior truth.
5. Do not duplicate the same rule with slightly different wording in other docs.

---

*This document describes the current known business rules of CineBook.  
Any new rule that affects behavior, data integrity, or security should be recorded here after the developer confirms it.*
