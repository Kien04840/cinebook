# Booking Domain Specification

## 0. Purpose

This document is the canonical specification for the **CineBook Booking & Seat Hold backend module**.

It defines the domain rules, data models, state lifecycle, concurrency mechanisms, authorization policies, and API contracts for managing customer seat reservations and bookings.

### Scope Boundaries

- **Backend Only**: Vue/frontend is out of scope.
- **Payment Gateway Isolation**: Payment integration (VNPay request, IPN callback, signature verification, payment record creation, refund execution) belongs strictly to the **Payment** module (`docs/use-cases/payment.md`, `docs/payment.md`).
- **Cinema & Seat Management**: Cinema, auditorium, and seat configuration/CRUD belong to the **Cinema** module (`docs/use-cases/cinema.md`).
- **Showtime Management**: Showtime scheduling and validation belong to the **Showtime** module (`docs/use-cases/showtime.md`).
- **Booking Domain Ownership**: The Booking module owns booking creation, temporary seat holding (5-minute window), booking lifecycle transitions, calculation and snapshot of booking totals, ticket creation upon verified payment, ownership authorization checks, and booking cancellation for unpaid reservations.

### Canonical References

The implementation agent MUST adhere to:

- `AGENTS.md`
- `docs/documentation-map.md`
- `docs/architecture.md`
- `docs/database.md` (§3.6 Booking, §3.7 Payment, §5 Critical Constraints)
- `docs/business-rules.md` (§2 Authorization, §5 Cinema/Seat, §6 Showtime, §7 Pricing, §8 Booking & Seat Hold, §9 Payment)
- `docs/api.md` (§6 Showtime/Seat Map, §8 Booking, §9 Payment)
- `docs/use-cases/authentication.md` (Customer identity, ownership validation)
- `docs/use-cases/cinema.md`
- `docs/use-cases/movie.md`
- `docs/use-cases/showtime.md`
- `.agents/rules/backend.md`
- `.agents/rules/security.md`
- `.agents/rules/database.md`
- `.agents/skills/implement-backend-feature/SKILL.md`
- `.agents/skills/database-change/SKILL.md` (only if a schema change is approved)

**Source Priority**:
1. Explicit developer instructions (Locked V1 Decisions in this document)
2. `AGENTS.md`
3. Existing working source code (`Booking.java`, `SeatHold.java`, `Ticket.java`, `Payment.java`, `BookingStatus.java`, `TicketStatus.java`, `PaymentStatus.java`)
4. Current database schema (`docs/database.md`)
5. `docs/business-rules.md`
6. `docs/api.md`
7. `docs/use-cases/*.md`

---

# 1. Implementation Prompt for Antigravity

Copy this prompt to Antigravity when implementing the Booking backend module.

```text
Implement the CineBook Booking & Seat Hold backend module.

BACKEND ONLY. Do not implement Vue/frontend/UI.
Do not implement Payment/VNPay gateway in this module.

============================================================
MANDATORY DOCUMENTATION ROUTING
============================================================

Before coding, read:

1. AGENTS.md
2. docs/documentation-map.md
3. docs/architecture.md
4. docs/database.md (especially §3.6 Booking, §3.7 Payment, §5 Constraints, §6 Statuses)
5. docs/business-rules.md (especially §8 Booking & Seat Hold, §9 Payment, §7 Pricing, §2 Auth)
6. docs/api.md (especially §8 Booking, §6 Seat Map, §9 Payment)
7. docs/use-cases/booking.md
8. docs/use-cases/showtime.md
9. docs/use-cases/cinema.md
10. .agents/rules/backend.md
11. .agents/rules/security.md
12. .agents/rules/database.md
13. .agents/skills/implement-backend-feature/SKILL.md

============================================================
PHASE 1 — INSPECT FIRST
============================================================

Do not implement immediately. Inspect existing code:
- Booking, SeatHold, Ticket, Payment, Seat, Showtime, User entities
- BookingStatus, TicketStatus, PaymentStatus enums
- BookingRepository, SeatHoldRepository, TicketRepository, PaymentRepository, SeatRepository
- BookingService, BookingServiceImpl, BookingController if existing stubs exist
- Error handling (AppException, ResourceNotFoundException, ConflictException, BadRequestException, ForbiddenException)
- Security context (SecurityUtils, CustomUserDetails, JWT authentication)
- DTO and Mapper conventions in sibling modules (Cinema, Movie, Showtime)

Determine what already exists versus what needs to be created or updated.

============================================================
PHASE 2 — SCOPE & INVARIANTS (LOCKED V1)
============================================================

Scope:
- Seat hold creation: exactly 5-minute duration (hold_expires_at = expires_at = now() + 5 min).
- Maximum seats: maximum 8 seats per booking transaction.
- Initial persisted booking status: PENDING_PAYMENT.
- Hold extension: NOT SUPPORTED in V1.
- Expired hold cleanup: query time filter (expires_at > now()) + Spring @Scheduled housekeeping every 60s.
- Pricing formula: ticket_price = showtime.base_price + seat_type.price_modifier (DECIMAL(12,2), no dynamic rules in V1).
- Customer cancellation: allowed for PENDING_PAYMENT (releases holds atomically); NOT allowed for PAID bookings.
- Booking 1-N Payment relationship: Database preserves 1-N cardinality between Booking and Payment. V1 standard flow processes one payment attempt per booking without retry.
- Ticket creation: created upon backend-verified payment success; QR code payload is ticket UUID (ticket.id).
- Customer booking detail and booking history (with ownership enforcement).
- Concurrency protection: uk_seat_holds_showtime_seat (showtime_id, seat_id) translated to 409 Conflict.

Forbidden:
- Do NOT implement VNPay gateway or payment callbacks here (Payment module).
- Do NOT change Booking-Payment relationship to 1-1 or add UNIQUE(payments.booking_id).
- Do NOT implement Promotion CRUD or discount calculation in Booking V1.
- Do NOT introduce Redis locks, distributed locks, or message brokers.
- Do NOT alter primary key strategies or table structures without approval.
- Do NOT trust client-supplied userId or seat prices.

============================================================
PHASE 3 — CONCURRENCY & TRANSACTION STRATEGY
============================================================

Booking is highly concurrency-sensitive:
- Never rely on "check availability then insert" without database transaction safety.
- The unique constraint `uk_seat_holds_showtime_seat` (showtime_id, seat_id) is the critical safety net against double-booking.
- Catch DataIntegrityViolationException / constraint violations and map to 409 Conflict.
- Encapsulate booking creation + seat holds within an atomic @Transactional boundary (all-or-nothing rollback).

============================================================
PHASE 4 — IMPLEMENTATION ORDER
============================================================

1. Repositories: custom queries for active holds, sold tickets, and availability.
2. DTOs: CreateBookingRequest, BookingDetailResponse, BookingSummaryResponse, SeatHoldResponse, TicketResponse, PaymentSummaryResponse.
3. Pricing calculation logic: base price + seat type modifier snapshot.
4. Scheduled cleanup task: BookingCleanupTask running every 60s.
5. BookingService & BookingServiceImpl:
   - createBooking (validation, 5-minute hold creation, pricing snapshot, PENDING_PAYMENT status)
   - getBookingDetail (ownership check)
   - getMyBookings (paginated customer history)
   - cancelBooking (ownership check, status check, hold release)
   - confirmPaidBooking (internal hook for Payment to transition to PAID and issue tickets with UUID QR)
6. Controller:
   - POST /api/v1/bookings
   - GET /api/v1/bookings/me
   - GET /api/v1/bookings/{id}
   - POST /api/v1/bookings/{id}/cancel
7. Unit, Concurrency, and Integration Tests.

============================================================
PHASE 5 — VERIFICATION
============================================================

Verify that:
- Single seat hold succeeds with 5-minute expiration and PENDING_PAYMENT status.
- Concurrent hold for the same seat results in 1 success and 1 conflict (409).
- Expired holds do not block new holds.
- Scheduled cleanup cleans up expired holds every 60s.
- Request with > 8 seats is rejected with 400 Bad Request.
- Customer cannot view or cancel another customer's booking (403/404).
- Customer cannot cancel PAID booking (400 Bad Request).
- Booking-Payment 1-N relationship is preserved in entities/schema.
- Prices are snapshotted and total is accurate.
- All existing tests and new tests pass: .\mvnw.cmd clean test.
```

---

# 2. Domain Scope

### In Scope (V1 Booking Backend)

1. **Start Booking / Seat Hold**:
   - Validate showtime eligibility (status `SCHEDULED`, not soft-deleted, `startTime > now()`).
   - Validate cinema and auditorium eligibility (status `ACTIVE`, not soft-deleted).
   - Validate requested seats (exist, belong to auditorium, status `ACTIVE`, `seatIds.size() <= 8`).
   - Validate real-time seat availability (no active unexpired holds, no valid sold tickets).
   - Atomically create `bookings` record (status `PENDING_PAYMENT`, `hold_expires_at = now() + 5 min`) and `seat_holds` records (`expires_at = now() + 5 min`).
   - Calculate booking total and snapshot ticket prices (`base_price + price_modifier`).
2. **Booking Retrieval & Ownership**:
   - Customer booking history (`GET /api/v1/bookings/me`) with pagination (`Pageable`, sort `createdAt,DESC`).
   - Customer booking detail (`GET /api/v1/bookings/{id}`) enforcing strict owner authorization (`booking.user_id == currentUserId` or `ROLE_ADMIN`).
3. **Booking Cancellation**:
   - Customer self-cancellation of `PENDING_PAYMENT` bookings (`POST /api/v1/bookings/{id}/cancel`), updating status to `CANCELLED` and releasing held seats immediately.
   - Strict rejection of customer self-cancellation for `PAID` bookings (400 Bad Request).
4. **Hold Expiration Management**:
   - 5-minute temporary hold window.
   - Dual cleanup strategy:
     - Query time filter: `expires_at > CURRENT_TIMESTAMP()` (expired holds never block new holds).
     - Spring `@Scheduled` task running every 60 seconds as housekeeping to transition expired bookings to `EXPIRED` and delete expired `seat_holds`.
5. **Paid Conversion / Ticket Issuance (Hook for Payment Module)**:
   - Coordinate with Payment module: transition `PENDING_PAYMENT → PAID`.
   - Issue `tickets` with snapshot `ticket_price`, status `VALID`, and `qr_code = ticket.id`.
   - Clean up / delete `seat_holds` upon successful ticket creation.
6. **Concurrency Safety**:
   - Enforce database constraint `uk_seat_holds_showtime_seat` as authoritative safety net.
   - Atomic transaction management (rollback all if any seat fails).

### Explicitly Out of Scope

- **Payment & Gateway**: VNPay request building, IPN handling, HMAC-SHA512 verification, refunds (`docs/use-cases/payment.md`).
- **Cinema & Seat Management**: CRUD for cinemas, auditoriums, seat types, and seats (`docs/use-cases/cinema.md`).
- **Showtime Management**: Scheduling, conflict detection, auto-generation (`docs/use-cases/showtime.md`).
- **Dynamic Pricing Administration**: `day_pricing_rules` and `time_slot_pricing_rules` are out of scope for V1 booking calculation.
- **Promotion Administration**: Discount codes and `booking_promotions` calculation are deferred for a dedicated Promotion module.
- **Frontend / UI**: Vue 3 components, seat map visualization, Pinia stores.
- **Distributed Infrastructure**: Redis distributed locks, message queues (RabbitMQ/Kafka).

---

# 3. Domain Relationships

```text
User (Customer)
  │ 1
  │
  ▼ *
Booking ────────────► Showtime ────────────► Auditorium ────────► Cinema
  │ 1                     │ 1                    │ 1
  ├──────────────┬────────┤                      ▼ *
  │ 1            │ 1      │ 1                   Seat
  ▼ *            ▼ *      │                      ▲ ▲
SeatHold       Ticket     │                      │ │
  │              │        │                      │ │
  │              ▼ *      │                      │ │
  │            Payment    │                      │ │
  │              ▲        │                      │ │
  │              │ 1      │                      │ │
  │              │        │                      │ │
  └──────────────┴────────┴──────────────────────┴─┘
```

### Cardinality & Invariants

- **User → Booking** (`1:N`): A user can make multiple bookings over time. A booking belongs to exactly one user.
- **Showtime → Booking** (`1:N`): A showtime hosts multiple customer bookings. A booking is strictly tied to exactly one showtime.
- **Booking → SeatHold** (`1:N`): A booking holds 1 to 8 seats during the 5-minute reservation window.
- **Booking → Ticket** (`1:N`): A confirmed (paid) booking produces 1 or more tickets (1 per seat).
- **Booking → Payment** (`1:N`): A booking can have multiple payment records over time (preserving full audit history of payment attempts: `FAILED`, `CANCELLED`, `SUCCESS`). In the standard V1 flow, one payment attempt is initiated and processed; V1 does not implement payment retry.
- **Showtime + Seat → SeatHold** (`1:1 active`): A seat in a specific showtime can have at most ONE active hold at any instant (`uk_seat_holds_showtime_seat`).
- **Booking + Seat → Ticket** (`1:1`): A booking can have at most one ticket per seat (`uk_tickets_booking_seat`).
- **Showtime + Seat → Ticket** (`1:1 valid`): A seat in a specific showtime can be sold at most once.

---

# 4. Existing Database Model

The database schema (`docs/database.md` §3.6, §3.7) defines the authoritative tables:

### 4.1 `bookings`

| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `varchar(36)` | Primary Key (UUID) |
| `booking_code` | `varchar(30)` | Unique, Not Null (e.g. `CB-20260901-ABC12`) |
| `user_id` | `varchar(36)` | FK → `users.id`, Not Null |
| `showtime_id` | `varchar(36)` | FK → `showtimes.id`, Not Null |
| `total_amount` | `decimal(12,2)` | Not Null, Check `total_amount >= 0` |
| `booking_status` | `varchar(30)` | Not Null (`HOLDING`, `PENDING_PAYMENT`, `PAID`, `CANCELLED`, `EXPIRED`, `REFUNDED`) |
| `hold_expires_at` | `datetime` | Nullable (timestamp when seat hold expires: `createdAt + 5m`) |
| `cancelled_at` | `datetime` | Nullable (timestamp when cancelled) |
| `cancelled_by_user_id`| `varchar(36)` | FK → `users.id`, Nullable |
| `cancelled_reason` | `varchar(500)` | Nullable |
| `created_at` | `datetime` | Not Null, creation timestamp |
| `updated_at` | `datetime` | Not Null, update timestamp |
| `version` | `bigint` | Not Null, Optimistic Locking (`@Version`) |

**Indexes**:
- `idx_bookings_user_created` (`user_id`, `created_at`)
- `idx_bookings_showtime_status` (`showtime_id`, `booking_status`)
- Unique: `uk_bookings_code` (`booking_code`)

### 4.2 `seat_holds`

| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `bigint unsigned` | Primary Key, `AUTO_INCREMENT` |
| `showtime_id` | `varchar(36)` | FK → `showtimes.id`, Not Null |
| `seat_id` | `varchar(36)` | FK → `seats.id`, Not Null |
| `booking_id` | `varchar(36)` | FK → `bookings.id`, Not Null |
| `expires_at` | `datetime` | Not Null (`createdAt + 5m`) |
| `created_at` | `datetime` | Not Null |

**Indexes & Unique Constraints**:
- **Critical Unique**: `uk_seat_holds_showtime_seat` (`showtime_id`, `seat_id`) — prevents double holding.
- `idx_seat_holds_booking` (`booking_id`)
- `idx_seat_holds_expires_at` (`expires_at`)

### 4.3 `tickets`

| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `varchar(36)` | Primary Key (UUID) |
| `booking_id` | `varchar(36)` | FK → `bookings.id`, Not Null |
| `seat_id` | `varchar(36)` | FK → `seats.id`, Not Null |
| `ticket_price` | `decimal(12,2)` | Not Null, Check `ticket_price >= 0` (Price Snapshot) |
| `ticket_status` | `varchar(20)` | Not Null (`VALID`, `USED`, `CANCELLED`) |
| `qr_code` | `varchar(255)` | Unique, Nullable (stores `ticket.id` UUID) |
| `created_at` | `datetime` | Not Null |

**Indexes & Unique Constraints**:
- Unique: `uk_tickets_booking_seat` (`booking_id`, `seat_id`)
- `idx_tickets_booking` (`booking_id`)
- `idx_tickets_seat` (`seat_id`)

### 4.4 `payments` (Managed by Payment Module)

| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `varchar(36)` | Primary Key (UUID) |
| `booking_id` | `varchar(36)` | FK → `bookings.id`, Not Null (**1-N relationship, no unique constraint**) |
| `payment_method`| `varchar(20)` | Not Null (e.g. `VNPAY`) |
| `payment_code` | `varchar(50)` | Unique, Not Null |
| `gateway_transaction_id` | `varchar(100)` | Nullable |
| `amount` | `decimal(12,2)` | Not Null, Check `amount >= 0` |
| `payment_status`| `varchar(20)` | Not Null (`PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`) |
| `paid_at` | `datetime` | Nullable |
| `gateway_response` | `json` | Nullable |
| `created_at` | `datetime` | Not Null |
| `updated_at` | `datetime` | Not Null |

### 4.5 `booking_promotions` (Deferred for V2)

| Column | Type | Constraints / Notes |
|---|---|---|
| `promotion_id` | `varchar(36)` | PK, FK → `promotions.id` |
| `booking_id` | `varchar(36)` | PK, FK → `bookings.id` |
| `discount_amount`| `decimal(12,2)`| Not Null, Check `discount_amount >= 0` |
| `created_at` | `datetime` | Not Null |

*Note: Table is preserved in schema but unused in V1 calculation.*

---

# 5. Authorization & Security

### Role-Based Access Control (RBAC)

1. **`ROLE_CUSTOMER`**:
   - Can create a booking (`POST /api/v1/bookings`).
   - Can view own booking list (`GET /api/v1/bookings/me`).
   - Can view own booking detail (`GET /api/v1/bookings/{id}`).
   - Can cancel own `PENDING_PAYMENT` booking (`POST /api/v1/bookings/{id}/cancel`).
   - Can initiate payment for own booking (`POST /api/v1/bookings/{id}/payments`).
2. **`ROLE_ADMIN`**:
   - Can view any booking detail (`GET /api/v1/bookings/{id}`).
   - Can view administrative booking queries (if admin endpoints are exposed).
3. **Anonymous Users**:
   - Denied access (`401 Unauthorized`) to all `/api/v1/bookings/**` endpoints.

### Ownership Enforcement Invariant

For every request targeting a specific booking `GET /api/v1/bookings/{id}` or `POST /api/v1/bookings/{id}/cancel`:

```text
authenticatedUser = SecurityContext.getCurrentUser()

if (authenticatedUser.hasRole("ROLE_ADMIN")) {
    allowAccess()
} else if (booking.user_id.equals(authenticatedUser.getId())) {
    allowAccess()
} else {
    throw ForbiddenException("You do not have permission to access this booking")
}
```

**Security Rule**: User ID MUST be resolved directly from the authenticated JWT security context (`CustomUserDetails.getId()`), never from request parameters, body, or URL path.

---

# 6. Booking Lifecycle

### 6.1 State Machine (Locked V1)

```text
                  POST /api/v1/bookings
                           │
                           ▼
            ┌──────────────────────────────┐
            │       PENDING_PAYMENT        │◄── (Initial persisted state)
            │ (5-minute seat hold active)  │
            └──────────────┬───────────────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
   Payment verified   User cancels   5-minute timeout
      successful       (unpaid)       (housekeeping)
           │               │               │
           ▼               ▼               ▼
┌────────────────────┐ ┌───────────┐ ┌───────────┐
│        PAID        │ │ CANCELLED │ │  EXPIRED  │
│(Tickets issued &   │ │   (Hold   │ │   (Hold   │
│  VALID, QR ready)  │ │ released) │ │ released) │
└──────────┬─────────┘ └───────────┘ └───────────┘
           │
  (No customer self-
  cancellation in V1)
```

### 6.2 Transition Matrix

| From Status | Event / Trigger | Target Status | Triggered By | Conditions & Invariants |
|---|---|---|---|---|
| *(None)* | Create Booking | `PENDING_PAYMENT` | Customer | Showtime bookable, seats available, max 8 seats, 5-min holds created atomically. |
| `PENDING_PAYMENT` | Payment Success | `PAID` | Payment IPN (System) | Payment verified SUCCESS, amount matches total, signature valid, tickets issued. |
| `PENDING_PAYMENT` | Customer Cancels | `CANCELLED` | Customer | Ownership verified. Holds released immediately. No refund required. |
| `PENDING_PAYMENT` | 5-Min Timeout | `EXPIRED` | Scheduled Housekeeping | `now() >= hold_expires_at`. Holds deleted, booking marked EXPIRED. |
| `PAID` | Customer Cancels | *(Rejected)* | Customer | **FORBIDDEN IN V1** (returns 400 Bad Request). |
| `PAID` | Admin Refund | `REFUNDED` / `CANCELLED` | Admin / Payment | Handled in Payment/Admin domain. |
| `EXPIRED` | Any | *(Terminal)* | - | No transitions allowed. |
| `CANCELLED` | Any | *(Terminal)* | - | No transitions allowed. |

*Note on `HOLDING`: `HOLDING` exists in the `BookingStatus` enum for domain completeness but is not exposed as the post-create state in V1. `POST /api/v1/bookings` immediately returns `PENDING_PAYMENT`.*

---

# 7. Start Booking / Seat Hold Flow

### Endpoint: `POST /api/v1/bookings`

**Request Body (`CreateBookingRequest`)**:
```json
{
  "showtimeId": "c4b1e840-7988-4c6e-a2f1-9d2123456789",
  "seatIds": [
    "550e8400-e29b-41d4-a716-446655440001",
    "550e8400-e29b-41d4-a716-446655440002"
  ]
}
```

### Step-by-Step Execution Flow

```text
Customer Client                Booking Service                 Database
      │                               │                            │
      │ 1. POST /api/v1/bookings      │                            │
      ├──────────────────────────────►│                            │
      │                               │ 2. Authenticate User       │
      │                               │    (Extract userId)        │
      │                               │                            │
      │                               │ 3. Validate Constraints:   │
      │                               │    • seatIds.size <= 8     │
      │                               │    • Showtime bookable     │
      │                               │    • Cinema/Aud ACTIVE     │
      │                               ├───────────────────────────►│
      │                               │◄───────────────────────────┤
      │                               │                            │
      │                               │ 4. Atomic Availability     │
      │                               │    Check (Holds + Tickets) │
      │                               ├───────────────────────────►│
      │                               │◄───────────────────────────┤
      │                               │                            │
      │                               │ 5. Calculate Pricing:      │
      │                               │    base_price + modifier   │
      │                               │                            │
      │                               │ 6. Insert Booking          │
      │                               │    (PENDING_PAYMENT, +5m)  │
      │                               │    + Insert SeatHolds (+5m)│
      │                               ├───────────────────────────►│ (Enforces uk_seat_holds)
      │                               │◄───────────────────────────┤
      │                               │                            │
      │ 7. 201 Created (Booking DTO)  │                            │
      │◄──────────────────────────────┤                            │
```

1. **Authentication**: Resolve current `User` from Security Context.
2. **Seat Limit Validation**:
   - `seatIds` must not be empty.
   - `seatIds.size() <= 8` (enforce maximum 8 seats limit). If `> 8`, throw `BadRequestException`.
3. **Showtime Eligibility Check**:
   - Showtime must exist and not be soft-deleted.
   - Showtime status must be `SCHEDULED` (not `CANCELLED`).
   - Showtime `startTime` must be in the future (`startTime > now()`).
   - Cinema must be `ACTIVE` and not soft-deleted.
   - Auditorium must be `ACTIVE` and not soft-deleted (reject `MAINTENANCE` or `DECOMMISSIONED`).
4. **Seat Validation**:
   - All `seatIds` must exist in database.
   - Every seat must belong to the showtime's `auditorium_id`.
   - Every seat must have status `ACTIVE` (reject `BROKEN`).
5. **Availability Verification (Transaction Boundary)**:
   - Check `seat_holds`: No active hold exists where `showtime_id = :showtimeId AND seat_id IN (:seatIds) AND expires_at > now()`.
   - Check `tickets`: No ticket exists where `seat_id IN (:seatIds) AND ticket_status = 'VALID'` for showtime's bookings.
6. **Pricing Calculation (V1 Locked)**:
   - For each seat: `seatPrice = showtime.base_price + seat.seatType.price_modifier`.
   - `totalAmount = sum(seatPrice for all seats)`.
7. **Booking & Hold Persistence**:
   - Generate unique `bookingCode` (e.g. `CB-YYYYMMDD-XXXXXX`).
   - `now = LocalDateTime.now()`.
   - `holdExpiresAt = now.plusMinutes(5)`.
   - Set `bookingStatus = PENDING_PAYMENT`.
   - Save `Booking` entity.
   - For each seat, create `SeatHold` entity with `expiresAt = holdExpiresAt`.
   - Save all `SeatHold` records (relying on `uk_seat_holds_showtime_seat` for concurrency safety).
8. **Response**: Return `BookingDetailResponse` with status `201 Created`.

---

# 8. Seat Availability Rules

A seat is evaluated with the following strict precedence: **`BLOCKED` > `SOLD` > `HELD` > `AVAILABLE`**:

1. **`BLOCKED`**:
   - The seat has `status = 'BROKEN'` or is soft-deleted.
   - The auditorium is `MAINTENANCE` or `DECOMMISSIONED` (or soft-deleted).
   - The cinema is `INACTIVE` or `CLOSED` (or soft-deleted).
   - Showtime status is `CANCELLED` or `startTime <= CURRENT_TIMESTAMP()`.
2. **`SOLD`**:
   - There exists a record in `tickets` joined with `bookings` where `bookings.showtime_id = :showtimeId`, `tickets.seat_id = :seatId`, and `tickets.ticket_status IN ('VALID', 'USED')`. (Both valid and used/scanned tickets occupy the seat for the showtime; only `CANCELLED` tickets release the seat).
3. **`HELD`**:
   - There exists a record in `seat_holds` for `(showtime_id, seat_id)` where `expires_at > CURRENT_TIMESTAMP()`.
4. **`AVAILABLE`**:
   - The seat is active, auditorium and cinema are active, showtime is scheduled in the future, and the seat is neither `SOLD` nor actively `HELD`.

### Expired Holds Policy (Locked V1)

- Holds where `expires_at <= CURRENT_TIMESTAMP()` are **EXPIRED** and **MUST NOT** block seat selection or new holds.
- **Query-Time Availability**: Availability queries strictly filter `expires_at > CURRENT_TIMESTAMP()`.
- **Active Hold Resolution on Booking Creation**: Inside the `createBooking` transaction, any expired `seat_holds` for the requested seats are actively deleted (`deleteExpiredHoldsForSeats`) before active hold verification and insertion. This guarantees that expired rows still present in MySQL do NOT cause false `409 Conflict` errors via the `uk_seat_holds_showtime_seat` database unique constraint prior to scheduled housekeeping.

---

# 9. Seat Hold Rules

### Hold Mechanics (Locked V1)

1. **Duration**: Exactly **5 minutes** from creation.
2. **Expiration Consistency**:
   - `bookings.hold_expires_at == seat_holds.expires_at`.
3. **Hold Extension**: **NOT SUPPORTED in V1**.
4. **Hold Ownership**: A seat hold is strictly linked to a single `booking_id` and single `user_id`.
5. **Atomicity**: If a user requests 3 seats and 1 is unavailable, the entire transaction rolls back (no partial holds, winner-takes-all).
6. **Uniqueness**: Enforced at the database level via `uk_seat_holds_showtime_seat (showtime_id, seat_id)`.
7. **Triple Expiration Safety Strategy**:
   - **Query-Time Filter**: `expires_at > now()` ensures expired holds never appear as `HELD` in seat availability.
   - **Transaction-Time Active Purge**: `createBooking` actively purges expired holds for the requested seats before inserting new holds, ensuring immediate reuse without waiting for the background job.
   - **Scheduled Housekeeping**: Spring `@Scheduled` task running **every 60 seconds** as background housekeeping to transition unpaid bookings past `hold_expires_at` to `EXPIRED` and delete all expired `seat_holds`.

---

# 10. Pricing & Booking Total

### Price Formula (Locked V1)

```text
ticket_price = showtime.base_price + seat_type.price_modifier
total_amount = sum(ticket_price for all selected seats)
```

- `day_pricing_rules` and `time_slot_pricing_rules` are **NOT** used in Booking V1.
- **Rounding**: Standard monetary precision `DECIMAL(12,2)`. No rounding to 1,000 / 5,000 VND.
- **Currency**: VND.
- **Price Snapshot**: `ticket.ticket_price` and `booking.total_amount` are immutable snapshots at creation time.

---

# 11. Payment Boundary & Coordination

```text
┌──────────────────────────────────────────┐           ┌──────────────────────────────────────────┐
│              BOOKING MODULE              │           │              PAYMENT MODULE              │
├──────────────────────────────────────────┤           ├──────────────────────────────────────────┤
│ • Validates seats & showtime             │           │ • Creates Payment record (booking_id FK) │
│ • Creates 5-min holds                    │           │ • Initiates VNPay Sandbox payment URL    │
│ • Computes booking total                 │           │ • Handles VNPay IPN & Return callbacks   │
│ • Manages BookingStatus                  │           │ • Verifies HMAC-SHA512 signatures        │
│ • Coordinates ticket creation upon PAID  │◄──────────┤ • Verifies transaction amounts           │
│ • Releases temporary holds on cancel/paid│ (Success) │ • Manages PaymentStatus state machine    │
│ • No direct gateway / VNPay SDK calls    │           │ • Handles refund transactions            │
└──────────────────────────────────────────┘           └──────────────────────────────────────────┘
```

### 11.1 Domain Ownership Separation

The boundary between Booking and Payment is strictly maintained:

- **Booking Module Owns**:
  - Booking aggregate lifecycle (`PENDING_PAYMENT`, `PAID`, `CANCELLED`, `EXPIRED`).
  - Temporary seat hold acquisition and release.
  - Calculation and snapshotting of booking total amount.
  - Ownership authorization verification.
  - Pre-payment cancellation by customer.
  - Creation of final valid tickets upon receiving verified payment success notification.
  - Booking module does **NOT** call external VNPay APIs, construct gateway redirect URLs, or verify cryptographic hashes.

- **Payment Module Owns**:
  - Payment aggregate lifecycle (`PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`).
  - Creation and management of `payments` records linked to `bookings.id`.
  - Payment method configuration (`VNPAY`).
  - Unique payment code generation (`payment_code`).
  - Gateway communication, VNPay Sandbox URL generation, and transaction ID mapping (`gateway_transaction_id`).
  - Server-side cryptographic signature verification (`vnp_SecureHash`) and amount validation.
  - Storing raw gateway responses (`gateway_response`) and timestamp (`paid_at`).
  - Refund operations.

---

### 11.2 Database Cardinality: Booking 1-N Payment

The database schema and JPA entities maintain a **1-to-N relationship between `Booking` and `Payment`**:

```java
// In Booking.java
@OneToMany(mappedBy = "booking")
private List<Payment> payments = new ArrayList<>();

// In Payment.java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "booking_id", nullable = false)
private Booking booking;
```

- **No Unique Constraint on `payments.booking_id`**: The database intentionally allows multiple `Payment` rows referencing the same `booking_id`.
- **Purpose**: Preserves full audit history of payment attempts across the lifecycle of a booking.
- **Example Domain Scenario**:
  ```text
  Booking CB-20260901-8F32A1
    ├── Payment PAY-001 ── status: FAILED     (User cancelled on gateway)
    └── Payment PAY-002 ── status: SUCCESS    (User re-attempted and paid)
  ```
- **V1 Standard Flow**: In Booking V1, the standard workflow processes a single payment attempt per booking without retry logic; payment retry is not implemented in V1. The 1-N schema cardinality is preserved to ensure compatibility with future Payment retry/extension features.

---

### 11.3 VNPay Sandbox Integration — Future Payment Module

CineBook integrates with the **VNPay Sandbox** gateway environment for development and testing:

- **External Gateway Nature**: VNPay is strictly an external third-party payment service. No sensitive card/banking credentials are ever transmitted to or stored in CineBook database.
- **Sandbox Simulation**: VNPay Sandbox allows simulating payment success (`vnp_ResponseCode = "00"`), user cancellation (`vnp_ResponseCode = "24"`), and various failure conditions.
- **No Schema Pollution**: Gateway-specific data is stored cleanly in existing `payments` columns (`gateway_transaction_id`, `gateway_response`, `payment_code`). No artificial tables such as `vnpay_transactions` or `sandbox_payments` are introduced.

#### End-to-End Domain Flow with VNPay Sandbox:

```text
Customer               Booking Module            Payment Module             VNPay Sandbox
   │                          │                         │                         │
   │ 1. POST /api/v1/bookings │                         │                         │
   ├─────────────────────────►│                         │                         │
   │    (PENDING_PAYMENT)     │                         │                         │
   │                          │                         │                         │
   │ 2. POST /api/v1/bookings/{id}/payments             │                         │
   ├───────────────────────────────────────────────────►│                         │
   │                          │                         │ 3. Create Payment       │
   │                          │                         │    (status = PENDING)   │
   │                          │                         │ 4. Build VNPay URL      │
   │ 5. Return Payment URL    │                         │    (with HMAC-SHA512)   │
   │◄───────────────────────────────────────────────────┤                         │
   │                                                    │                         │
   │ 6. Redirect to VNPay Sandbox                       │                         │
   ├─────────────────────────────────────────────────────────────────────────────►│
   │                                                    │                         │
   │ 7. Customer performs sandbox transaction           │                         │
   │                                                    │                         │
   │ 8. VNPay IPN Server Callback                       │                         │
   │    (GET /api/v1/payments/vnpay/ipn)                │                         │
   │                                                    │◄────────────────────────┤
   │                          │                         │                         │
   │                          │                         │ 9. Verify Signature     │
   │                          │                         │    & Match Amount       │
   │                          │                         │ 10. Update Payment      │
   │                          │                         │     (SUCCESS / FAILED)  │
   │                          │ 11. Confirm Paid Booking│                         │
   │                          │◄────────────────────────┤                         │
   │                          │     (if SUCCESS)        │                         │
   │                          │ 12. Update Booking PAID │                         │
   │                          │ 13. Create Tickets      │                         │
   │                          │ 14. Delete SeatHolds    │                         │
   │                          │                         │                         │
   │ 15. VNPay redirects user back (Return URL)         │                         │
   ├─────────────────────────────────────────────────────────────────────────────►│
```

---

### 11.4 Payment Status vs Booking Status State Machines

`PaymentStatus` and `BookingStatus` are **distinct, independent state machines** managed by their respective domain modules:

| PaymentStatus (Payment Module) | BookingStatus (Booking Module) | Relationship & Coordination |
|---|---|---|
| `PENDING` | `PENDING_PAYMENT` | Payment attempt created and awaiting customer completion on gateway. |
| `SUCCESS` | `PAID` | Payment verified successful. Triggers Booking transition to `PAID` and ticket generation. |
| `FAILED` | `PENDING_PAYMENT` / `EXPIRED` | Gateway transaction failed. Booking remains `PENDING_PAYMENT` until 5-minute hold expires (becoming `EXPIRED`). |
| `CANCELLED` | `CANCELLED` / `EXPIRED` | Customer cancelled on gateway page. |

**Important Rules**:
- There is **NO** `BookingStatus.FAILED`. Payment failure does not invent a new booking status.
- A booking becomes `PAID` **ONLY** upon verified `PaymentStatus.SUCCESS`.

---

### 11.5 VNPay Security, Signature Verification & Idempotency

When the Payment module processes gateway notifications:

1. **Cryptographic Verification**: The backend verifies `vnp_SecureHash` using the secret key (`HMAC-SHA512`). Responses with invalid signatures are immediately rejected (`97 Signature failed`).
2. **Zero Client Trust**: Payment status and amount are never trusted from frontend query parameters or redirect headers. Verification occurs strictly on server-side IPN.
3. **Amount Integrity**: Gateway response amount (`vnp_Amount / 100`) MUST strictly match `booking.total_amount`.
4. **Idempotent Callback Processing**: If VNPay sends duplicate IPN callbacks for the same transaction, the backend processes the transition once and returns HTTP 200 without creating duplicate tickets or re-triggering state changes.

---

# 12. Ticket Creation

### Invariants (Locked V1)

1. **Timing**: Tickets are created **ONLY** after backend-verified payment success (`PENDING_PAYMENT → PAID`).
2. **Quantity**: Exactly 1 ticket per selected seat.
3. **Snapshot**: `ticket.ticket_price = showtime.base_price + seat.seatType.price_modifier`.
4. **QR Code Payload**: V1 uses the ticket UUID (`ticket.id`) as the QR code identifier (`ticket.qr_code = ticket.id`).
5. **Initial Status**: `ticket_status = 'VALID'`.

---

# 13. Booking Cancellation

### Endpoint: `POST /api/v1/bookings/{id}/cancel`

**Authorization**: Owner customer (`ROLE_CUSTOMER`) or `ROLE_ADMIN`.

### Behavior by Status (Locked V1)

- **`PENDING_PAYMENT`**:
  - Customer cancels reservation before payment completes.
  - Action: Update `booking_status = 'CANCELLED'`, set `cancelled_at = now()`, set `cancelled_by_user_id`.
  - Action: Delete associated `seat_holds` records immediately so seats are released.
  - Return `200 OK` with updated `BookingDetailResponse`.
- **`PAID`**:
  - Customer self-cancellation is **STRICTLY FORBIDDEN IN V1**.
  - Request is rejected with `400 Bad Request` (`"Không thể tự hủy đơn đặt vé đã thanh toán thành công. Vui lòng liên hệ quản trị viên."`).
  - Refunds are handled solely through Admin / Payment workflows in V2+.
- **`EXPIRED` / `CANCELLED`**:
  - Reject with `400 Bad Request` (`"Đơn đặt vé đã bị hủy hoặc hết hạn trước đó!"`).

---

# 14. Booking History & Detail APIs

### 14.1 List My Bookings (`GET /api/v1/bookings/me`)

- **Auth**: `ROLE_CUSTOMER`
- **Query Params**: `page` (default 0), `size` (default 20), `status` (optional filter).
- **Sort**: `createdAt,DESC`.
- **Response**: `PageResponse<BookingSummaryResponse>` containing:
  - `id`, `bookingCode`, `bookingStatus`, `totalAmount`, `createdAt`, `holdExpiresAt`.
  - Showtime summary: movie title, poster URL, cinema name, auditorium name, format, `startTime`.
  - Total seat count.

### 14.2 Get Booking Detail (`GET /api/v1/bookings/{id}`)

- **Auth**: `ROLE_CUSTOMER` (owner only) or `ROLE_ADMIN`.
- **Response**: `BookingDetailResponse` containing:
  - `id`, `bookingCode`, `bookingStatus`, `totalAmount`, `holdExpiresAt`, `createdAt`, `cancelledAt`, `cancelledReason`.
  - `showtime`: ID, movie (title, poster, duration), cinema (name, address), auditorium (name), format, language, `startTime`, `endTime`.
  - `seats`: List of seats (`seatId`, `rowLabel`, `seatNumber`, `seatCode`, `seatTypeName`, `price`).
  - `tickets`: List of issued tickets (if `PAID`) with `id`, `ticketPrice`, `ticketStatus`, `qrCode`.
  - `payments`: List of associated `PaymentSummaryResponse` records (`id`, `paymentMethod`, `paymentCode`, `amount`, `paymentStatus`, `paidAt`).

---

# 15. API Contract Specifications

### 15.1 Summary Table

| Method | Endpoint | Auth | Request Body | Success Response | Error Codes |
|---|---|---|---|---|---|
| `POST` | `/api/v1/bookings` | `ROLE_CUSTOMER` | `CreateBookingRequest` | `201 Created` (`BookingDetailResponse`) | `400`, `401`, `404`, `409` |
| `GET` | `/api/v1/bookings/me` | `ROLE_CUSTOMER` | None (Pageable query) | `200 OK` (`PageResponse<BookingSummaryResponse>`) | `401` |
| `GET` | `/api/v1/bookings/{id}` | `ROLE_CUSTOMER` / `ROLE_ADMIN` | None | `200 OK` (`BookingDetailResponse`) | `401`, `403`, `404` |
| `POST` | `/api/v1/bookings/{id}/cancel` | `ROLE_CUSTOMER` / `ROLE_ADMIN` | `CancelBookingRequest` (optional) | `200 OK` (`BookingDetailResponse`) | `400`, `401`, `403`, `404` |

### 15.2 Request / Response DTO Shapes

#### `CreateBookingRequest`
```json
{
  "showtimeId": "c4b1e840-7988-4c6e-a2f1-9d2123456789",
  "seatIds": [
    "550e8400-e29b-41d4-a716-446655440001",
    "550e8400-e29b-41d4-a716-446655440002"
  ]
}
```

#### `BookingDetailResponse`
```json
{
  "id": "c4b1e840-7988-4c6e-a2f1-9d2123456789",
  "bookingCode": "CB-20260901-8F32A1",
  "bookingStatus": "PENDING_PAYMENT",
  "totalAmount": 240000.00,
  "holdExpiresAt": "2026-09-01T10:05:00",
  "createdAt": "2026-09-01T10:00:00",
  "cancelledAt": null,
  "cancelledReason": null,
  "showtime": {
    "id": "st-1",
    "movie": {
      "id": "mov-1",
      "title": "Inception",
      "posterUrl": "https://image.tmdb.org/...",
      "durationMinutes": 148
    },
    "cinema": {
      "id": "cin-1",
      "name": "CineBook Landmark 81",
      "address": "720A Dien Bien Phu, Binh Thanh, HCMC"
    },
    "auditorium": {
      "id": "aud-1",
      "name": "Hall 1 (IMAX)"
    },
    "format": "IMAX",
    "language": "English",
    "startTime": "2026-09-01T14:00:00",
    "endTime": "2026-09-01T16:28:00"
  },
  "seats": [
    {
      "seatId": "seat-1",
      "rowLabel": "F",
      "seatNumber": 5,
      "seatCode": "F5",
      "seatTypeName": "VIP",
      "price": 120000.00
    },
    {
      "seatId": "seat-2",
      "rowLabel": "F",
      "seatNumber": 6,
      "seatCode": "F6",
      "seatTypeName": "VIP",
      "price": 120000.00
    }
  ],
  "tickets": [],
  "payments": []
}
```

---

# 16. Concurrency Strategy

### The Double-Booking Race Condition

```text
User A (Thread 1)                          User B (Thread 2)
      │                                          │
      │ 1. Check availability for Seat F5        │ 1. Check availability for Seat F5
      │    (Result: AVAILABLE)                   │    (Result: AVAILABLE)
      │                                          │
      │ 2. Insert into bookings                  │ 2. Insert into bookings
      │                                          │
      │ 3. Insert into seat_holds                │ 3. Insert into seat_holds
      │    (showtime_id, seat_id=F5)             │    (showtime_id, seat_id=F5)
      │    ───► SUCCESS                          │    ───► UNIQUE CONSTRAINT VIOLATION!
      │                                          │         (uk_seat_holds_showtime_seat)
```

### Concurrency Protection Mechanisms

1. **Pre-Insert Active Hold Check**: Check current active holds (`expires_at > now()`) inside the transaction.
2. **Database Constraint as Ground Truth**: `uk_seat_holds_showtime_seat (showtime_id, seat_id)` prevents concurrent transactions from committing duplicate holds.
3. **Exception Translation**: Catch `DataIntegrityViolationException` in `BookingServiceImpl` and translate to `ConflictException` (`409 Conflict`: `"Một hoặc nhiều ghế đã được người khác giữ chỗ. Vui lòng chọn ghế khác."`).
4. **Optimistic Locking**: `@Version private Long version;` on `Booking` entity prevents conflicting state mutations.
5. **No Heavyweight Locks**: Zero Redis distributed locks, zero table-level locks. Relies on relational DB ACID guarantees and unique index.

---

# 17. Transaction Boundaries

1. **Create Booking & Hold Transaction** (`@Transactional`):
   - Scope: Showtime validation, seat availability check, `Booking` insert, `SeatHold` batch insert.
   - Isolation: Default (`READ_COMMITTED`).
   - Rollback: `Exception.class` (atomic all-or-nothing rollback if any seat fails).
2. **Payment Confirmation & Ticket Issuance Transaction** (`@Transactional`):
   - Scope: Coordinated with Payment module IPN processing. Update `booking_status = 'PAID'`, insert `tickets`, delete `seat_holds`.
3. **Cancellation Transaction** (`@Transactional`):
   - Scope: Validate status, update `booking_status = 'CANCELLED'`, delete `seat_holds`.

---

# 18. Validation & Error Scenarios

| Scenario | HTTP Status | Error Code / Exception | Message / Details |
|---|---|---|---|
| Anonymous user accesses booking API | `401 Unauthorized` | `UNAUTHORIZED` | "Full authentication is required to access this resource" |
| Customer tries to view another user's booking | `403 Forbidden` | `FORBIDDEN` | "Bạn không có quyền truy cập đơn đặt vé này." |
| Showtime ID does not exist | `404 Not Found` | `RESOURCE_NOT_FOUND` | "Không tìm thấy lịch chiếu!" |
| Showtime is in past or cancelled | `400 Bad Request` | `BAD_REQUEST` | "Lịch chiếu đã bắt đầu hoặc đã bị hủy!" |
| Auditorium is in maintenance / decommissioned | `409 Conflict` | `CONFLICT` | "Phòng chiếu hiện đang bảo trì hoặc ngừng hoạt động!" |
| Seat ID does not exist | `404 Not Found` | `RESOURCE_NOT_FOUND` | "Không tìm thấy thông tin ghế ngồi!" |
| Seat belongs to different auditorium | `400 Bad Request` | `BAD_REQUEST` | "Ghế ngồi không thuộc phòng chiếu của lịch chiếu này!" |
| Seat is broken (`BROKEN`) | `400 Bad Request` | `BAD_REQUEST` | "Ghế ngồi đang gặp sự cố và không thể đặt!" |
| Exceeding maximum 8 seats limit | `400 Bad Request` | `BAD_REQUEST` | "Không thể đặt quá 8 ghế trong một lần đặt vé!" |
| Seat is currently held by someone else | `409 Conflict` | `SEAT_ALREADY_HELD` | "Một hoặc nhiều ghế đã được giữ chỗ bởi người khác!" |
| Seat is already sold (paid ticket exists) | `409 Conflict` | `SEAT_ALREADY_SOLD` | "Một hoặc nhiều ghế đã được bán!" |
| Customer attempts to cancel `PAID` booking | `400 Bad Request` | `BAD_REQUEST` | "Không thể tự hủy đơn đặt vé đã thanh toán thành công. Vui lòng liên hệ quản trị viên." |
| Cancel booking already `CANCELLED` / `EXPIRED` | `400 Bad Request` | `BAD_REQUEST` | "Đơn đặt vé đã bị hủy hoặc hết hạn trước đó!" |
| Concurrency race condition on hold | `409 Conflict` | `SEAT_HOLD_CONFLICT` | "Ghế đã được đặt bởi người khác. Vui lòng chọn ghế khác!" |

---

# 19. Consolidated Hard Invariants

1. **No Double Selling**: A seat in a given showtime can have at most one sold ticket (`ticket_status IN ('VALID', 'USED')`). Only `CANCELLED` tickets release the seat.
2. **No Double Active Holding**: A seat in a given showtime can have at most one active (`expires_at > now()`) `seat_holds` record (`uk_seat_holds_showtime_seat`).
3. **5-Minute Hold Duration**: `hold_expires_at = expires_at = createdAt + 5 minutes`.
4. **Maximum 8 Seats**: A booking cannot contain more than 8 seats.
5. **Single Ownership**: A booking belongs to exactly one authenticated `User`.
6. **Single Showtime**: A booking holds seats for exactly one `Showtime`.
7. **Auditorium Integrity**: All seats in a booking must belong to the auditorium of the target showtime.
8. **Price Snapshot Immutability**: `ticket.ticket_price` and `booking.total_amount` must be stored at creation time (`base_price + price_modifier`) and never altered later.
9. **Non-Negative Amounts**: `total_amount >= 0` and `ticket_price >= 0` (`DECIMAL(12,2)`).
10. **Expired Holds Do Not Block**: Holds with `expires_at <= now()` are considered released and must not prevent new holds. `createBooking` actively purges expired holds for requested seats, and background housekeeping runs every 60s.
11. **Backend-Verified Payment**: Booking status only becomes `PAID` after cryptographic backend verification of the payment gateway response.
12. **Booking-Payment 1-N Cardinality**: Database schema preserves 1-N relationship between Booking and Payment (`List<Payment>`). V1 standard flow processes one payment attempt per booking without retry.
13. **No Customer PAID Self-Cancellation**: Customers cannot self-cancel `PAID` bookings in V1.
14. **Idempotent Payment Confirmation**: Multiple calls to `confirmPaidBooking` for the same paid booking return current state without creating duplicate tickets.

---

# 20. Database Change Policy

- The current database schema is authoritative (`docs/database.md`).
- Existing tables (`bookings`, `seat_holds`, `tickets`, `payments`, `booking_promotions`) are fully prepared with required foreign keys, indexes, and unique constraints.
- **Never perform destructive changes**:
  - Do NOT drop tables or columns.
  - Do NOT alter primary key data types (UUID vs BigInt for `seat_holds`).
  - Do NOT remove `uk_seat_holds_showtime_seat` or `uk_tickets_booking_seat`.
  - Do NOT add a unique constraint on `payments.booking_id` (preserving 1-N cardinality).
- If an additive change is required, follow `.agents/skills/database-change/SKILL.md` and seek developer confirmation.

---

# 21. Phased Implementation Plan

### Phase 1: Repositories & Query Infrastructure
- `SeatHoldRepository`:
  - `findActiveHoldsByShowtimeId(showtimeId, now)`
  - `findActiveHoldsByShowtimeAndSeatIds(showtimeId, seatIds, now)`
  - `deleteByBookingId(bookingId)`
  - `deleteExpiredHolds(now)`
- `TicketRepository`:
  - `existsByShowtimeIdAndSeatIdsAndStatus(showtimeId, seatIds, status)`
- `BookingRepository`:
  - `findByUserIdOrderByCreatedAtDesc(userId, Pageable)`
  - `findByIdAndUserId(id, userId)`
  - `findExpiredPendingBookings(now)`

### Phase 2: DTOs & Mappers
- Request DTOs: `CreateBookingRequest`, `CancelBookingRequest`.
- Response DTOs: `BookingDetailResponse`, `BookingSummaryResponse`, `BookingSeatResponse`, `TicketResponse`, `PaymentSummaryResponse`.
- Mapper: `BookingMapper` (mapping Booking entity to Detail/Summary responses).

### Phase 3: Scheduled Housekeeping Task
- Implement `BookingCleanupTask` with `@Scheduled(fixedDelay = 60000)`:
  - Transition unpaid bookings past `hold_expires_at` to `EXPIRED`.
  - Delete expired `seat_holds` (`expires_at <= now()`).

### Phase 4: BookingService — Core Hold & Create
- Implement `createBooking(CreateBookingRequest)`:
  - Validate seat count (`<= 8`).
  - Showtime, cinema, auditorium, seat validation.
  - Price calculation (`base_price + price_modifier`) & total computation.
  - Atomic persistence of `Booking` (`PENDING_PAYMENT`, `holdExpiresAt = now() + 5m`) and `SeatHold` records.
  - Catch `DataIntegrityViolationException` and rethrow `ConflictException`.

### Phase 5: BookingService — Queries & Detail
- Implement `getMyBookings(Pageable)`: Paginated customer booking history.
- Implement `getBookingDetail(id)`: Detailed view with owner/admin authorization check (including payment history list).

### Phase 6: BookingService — Cancellation
- Implement `cancelBooking(id, CancelBookingRequest)`:
  - Ownership check.
  - Status validation (only `PENDING_PAYMENT` allowed; reject `PAID`, `EXPIRED`, `CANCELLED`).
  - Status transition to `CANCELLED`.
  - Immediate removal of `seat_holds`.

### Phase 7: Paid Conversion Hook (for Payment Module)
- Implement `confirmPaidBooking(bookingId, paymentId)`:
  - Update `booking_status = PAID`.
  - Generate `Ticket` records with snapshot price and `qrCode = ticket.id`.
  - Delete `seat_holds`.

### Phase 8: Controller & Security Configuration
- Implement `BookingController` (Customer endpoints: `/api/v1/bookings/**`).
- Verify Spring Security filter chain for `/api/v1/bookings/**`.

### Phase 9: Comprehensive Testing & Verification
- Unit tests for `BookingService`.
- Concurrency test for multi-threaded simultaneous seat holds.
- Controller unit tests with `MockMvc`.
- Security tests (`BookingSecurityTest`) verifying RBAC and 401/403 behavior.
- Regression verification: `.\mvnw.cmd clean test`.

---

# 22. Testing Strategy

### 22.1 Service Tests (`BookingServiceTest`)
- `createBooking_Success`: Valid showtime and available seats creates booking + 5-min holds (`PENDING_PAYMENT`).
- `createBooking_ExceedMaxSeats_ThrowsBadRequest`: Request with 9 seats throws 400 Bad Request.
- `createBooking_SeatAlreadyHeld_ThrowsConflict`: Active hold exists.
- `createBooking_SeatAlreadySold_ThrowsConflict`: Valid ticket exists.
- `createBooking_ExpiredHold_AllowsNewHold`: Expired hold ignored, new hold created.
- `createBooking_SeatFromDifferentAuditorium_ThrowsBadRequest`: Mismatched auditorium.
- `createBooking_BrokenSeat_ThrowsBadRequest`: Seat status `BROKEN`.
- `createBooking_MaintenanceAuditorium_ThrowsConflict`: Auditorium in `MAINTENANCE`.
- `getBookingDetail_Owner_Success`: Customer views own booking.
- `getBookingDetail_NotOwner_ThrowsForbidden`: Customer views another user's booking.
- `getBookingDetail_Admin_Success`: Admin views any customer's booking.
- `cancelBooking_PendingPayment_ReleasesHolds`: Holds deleted and status set to `CANCELLED`.
- `cancelBooking_PaidBooking_ThrowsBadRequest`: Customer cancelling `PAID` booking rejected with 400 Bad Request.
- `cancelBooking_AlreadyCancelled_ThrowsBadRequest`: Re-cancelling rejected.

### 22.2 Concurrency Tests (`BookingConcurrencyTest`)
- Multi-threaded execution: 2 threads attempting to book the exact same seat simultaneously.
- Verify: Exactly 1 thread succeeds (201 Created), exactly 1 thread fails (409 Conflict).
- Verify: Exactly 1 record exists in `seat_holds` for that `(showtime_id, seat_id)`.

### 22.3 Controller Tests (`BookingControllerTest`)
- `createBooking_Returns201`: Valid payload returns 201 Created with `PENDING_PAYMENT`.
- `getMyBookings_Returns200`: Paginated response.
- `getBookingDetail_Returns200`: Detail response.
- `cancelBooking_Returns200`: Updated detail response.

### 22.4 Security Tests (`BookingSecurityTest`)
- Anonymous request to `/api/v1/bookings/**` returns `401 Unauthorized`.
- Customer access allowed for own bookings.

---

# 23. Manual / Postman Verification Checklist

1. **Authentication**:
   - Register and login as `customer1` (`POST /api/v1/auth/login`).
   - Register and login as `customer2`.
2. **Explore Showtimes**:
   - `GET /api/v1/showtimes` -> pick `showtimeId`.
   - `GET /api/v1/showtimes/{showtimeId}/seats` -> observe available seats.
3. **Create Booking (Hold Seats)**:
   - `customer1` calls `POST /api/v1/bookings` with seat `[F5, F6]`.
   - Verify `201 Created`, `status = "PENDING_PAYMENT"`, `holdExpiresAt` is exactly 5 minutes ahead.
4. **Exceed Max Seats Test**:
   - `customer1` calls `POST /api/v1/bookings` with 9 seats -> verify `400 Bad Request`.
5. **Verify Seat Map Updated**:
   - `GET /api/v1/showtimes/{showtimeId}/seats` -> seats `F5, F6` now show as `HELD`.
6. **Conflict Test**:
   - `customer2` calls `POST /api/v1/bookings` with seat `[F5]`.
   - Verify `409 Conflict` returned.
7. **Customer Booking History & Detail**:
   - `customer1` calls `GET /api/v1/bookings/me` -> booking is listed.
   - `customer1` calls `GET /api/v1/bookings/{id}` -> returns full details.
8. **Security Ownership Test**:
   - `customer2` calls `GET /api/v1/bookings/{id}` with `customer1`'s booking ID.
   - Verify `403 Forbidden` returned.
9. **Cancellation Test (Unpaid)**:
   - `customer1` calls `POST /api/v1/bookings/{id}/cancel`.
   - Verify `bookingStatus = "CANCELLED"`.
   - Verify `GET /api/v1/showtimes/{showtimeId}/seats` -> seats `F5, F6` are `AVAILABLE` again.
10. **Hold Expiration Test**:
    - Create a booking with hold.
    - Wait 5 minutes or fast-forward `expires_at` in DB to past timestamp.
    - `customer2` calls `POST /api/v1/bookings` for the same seat -> succeeds (`201 Created`).
11. **Paid Cancellation Restriction Test**:
    - Simulate paid booking (`bookingStatus = "PAID"`).
    - `customer1` calls `POST /api/v1/bookings/{id}/cancel` -> verify `400 Bad Request` returned.

---

# 24. Definition of Done

The Booking module implementation is complete when:

- [ ] All request/response DTOs and mappers are created and tested.
- [ ] `BookingService` and `BookingController` are fully implemented following Layered Architecture.
- [ ] Database constraints `uk_seat_holds_showtime_seat` and `uk_tickets_booking_seat` are respected and tested.
- [ ] Booking-Payment 1-N relationship is preserved in JPA entities and schema without artificial unique constraints on `booking_id`.
- [ ] Hold duration is strictly 5 minutes, and maximum 8 seats limit is enforced.
- [ ] Initial persisted status is `PENDING_PAYMENT`.
- [ ] Real-time availability accurately factors in active holds and sold tickets, ignoring expired holds.
- [ ] Scheduled cleanup runs every 60s as housekeeping.
- [ ] Customer ownership authorization is strictly enforced on detail and cancellation endpoints.
- [ ] Customer self-cancellation of `PAID` bookings is blocked.
- [ ] Concurrency tests verify that double-booking is impossible.
- [ ] Price snapshots (`base_price + price_modifier`) and total amounts are calculated correctly.
- [ ] Coordination hook for ticket issuance on payment success is provided with UUID QR code.
- [ ] Unit and controller tests achieve high coverage.
- [ ] `.\mvnw.cmd clean test` passes with 0 failures, 0 errors.

---

# 25. Open Decisions

All V1 core decisions have been finalized and locked. The following items are recorded strictly for future module extensions (V2+):

1. **Payment Retry Workflow (V2)**:
   - Defining the UX and retry policy for creating a second payment attempt on an existing booking (`Booking 1-N Payment`) before hold expiration.
2. **Ticket Scanning & Gate Validation (V2)**:
   - Ticket status transition (`VALID -> USED`) upon physical entrance validation will be defined in a future Cinema Gate / Scanner Admin API specification.
3. **Promotion Engine Integration (V2)**:
   - Voucher validation, stacking rules, discount calculation, and `booking_promotions` snapshotting will be specified when the Promotion Management module is developed.
4. **Dynamic Pricing Rules Integration (V2)**:
   - Precedence and rule matching for `day_pricing_rules` and `time_slot_pricing_rules` will be specified in the Dynamic Pricing module.
5. **Admin Refund Workflow (V2)**:
   - Administrative cancellation and refund approval workflow for `PAID` bookings will be specified in the Payment / Administration module.
