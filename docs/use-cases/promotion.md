# Promotion / Voucher Domain Specification

## 0. Purpose

This document is the canonical specification for the **CineBook Promotion & Voucher Management module (V1)**.

It defines the domain models, business invariants, discount calculation algorithms, usage limit and concurrency controls, admin management workflows, customer validation and booking snapshotting rules, financial integrity guarantees, API contracts, security controls, and testing strategy for promotional discounts.

---

## 1. Canonical References

The implementation agent MUST adhere to:

- `AGENTS.md` (Monolith rules, Layered Architecture, database safety, testing requirements)
- `docs/documentation-map.md`
- `docs/architecture.md` (Controller → Service → Repository → DB)
- `docs/database.md` (§3.6 Booking, §3.7 Payment, §3.8 Promotion, §5 Constraints, §6 Statuses)
- `docs/business-rules.md` (§7 Pricing, §8 Booking & Seat Hold, §9 Payment, §10 Promotion / Voucher)
- `docs/api.md` (§8 Booking, §9 Payment, §10 Promotions, §11 Admin Endpoints)
- `docs/use-cases/booking.md` (Booking lifecycle, pricing snapshots, ticket creation)
- `docs/use-cases/payment.md` (Payment snapshots, VNPay integration, financial amount consistency)
- `.agents/rules/backend.md`
- `.agents/rules/security.md`
- `.agents/rules/database.md`
- `.agents/skills/implement-backend-feature/SKILL.md`

### Source Priority Order
1. Explicit developer instructions (Locked V1 Decisions in this document)
2. `AGENTS.md`
3. Existing working source code (`Promotion.java`, `BookingPromotion.java`, `Booking.java`, `Payment.java`, `PromotionDiscountType.java`, `PromotionStatus.java`, `BookingServiceImpl.java`, `PaymentServiceImpl.java`)
4. Current database schema (`docs/database.md`)
5. `docs/business-rules.md` & `docs/api.md`
6. Sibling use-case specifications (`docs/use-cases/booking.md`, `docs/use-cases/payment.md`)

---

## 2. Domain Scope & Architectural Boundaries

### 2.1 In Scope (V1 Promotion Backend)

1. **Admin Promotion Management (`/api/v1/admin/promotions/**`)**:
   - Create new promotion with unique uppercase code, discount configuration (`PERCENTAGE` or `FIXED_AMOUNT`), validity window (`start_at` to `end_at`), optional minimum order threshold (`min_order_amount`), optional discount ceiling (`max_discount_amount`), optional global usage quota (`usage_limit`), and initial status `ACTIVE`.
   - List promotions with pagination, sorting, and filtering by status (`ACTIVE`, `INACTIVE`, `EXPIRED`) and keyword search (code, name).
   - Retrieve promotion details with real-time usage statistics (`used_count`, `usage_limit`, remaining quota).
   - Update mutable metadata of unexpired promotions (name, description, validity dates, usage limit, thresholds).
   - Explicit lifecycle activation / deactivation toggle (`PATCH /api/v1/admin/promotions/{id}/status`).
2. **Customer Promotion Validation & Preview (`GET /api/v1/promotions/validate`)**:
   - Lightweight preview endpoint for customer checkout UI to test a code against an estimated/gross amount before starting a booking.
   - Validates existence, status, validity window, minimum order threshold, and remaining quota.
   - Calculates and returns estimated discount amount and net payable amount.
3. **Booking Integration & Discount Snapshotting (`POST /api/v1/bookings`)**:
   - Optional `promotionCode` field in `CreateBookingRequest`.
   - Authoritative server-side validation during the atomic booking transaction:
     - Check code existence, `status == ACTIVE`, `start_at <= now < end_at`.
     - Check gross ticket subtotal $\ge$ `min_order_amount` (if configured).
     - Check `used_count < usage_limit` (if configured) with pessimistic concurrency locking (`SELECT ... FOR UPDATE`).
   - Calculate exact discount:
     - `PERCENTAGE`: $\text{discount} = \min(\text{grossAmount} \times \text{discountValue} / 100, \text{maxDiscountAmount})$.
     - `FIXED_AMOUNT`: $\text{discount} = \min(\text{discountValue}, \text{grossAmount})$.
   - Deduct discount from gross subtotal: $\text{booking.total\_amount} = \max(0, \text{grossAmount} - \text{discount})$.
   - Atomically create `booking_promotions` snapshot record (`promotion_id`, `booking_id`, `discount_amount`, `created_at`).
   - Increment `promotions.used_count` by 1 within the same booking creation transaction.
4. **Quota Release upon Booking Expiration / Cancellation**:
   - If a `PENDING_PAYMENT` booking expires (via `BookingCleanupTask`) or is cancelled by the customer (`POST /api/v1/bookings/{id}/cancel`), atomically decrement `promotions.used_count` by 1 (clamped to $\ge 0$) so the quota is returned to the pool.
5. **Financial Integrity & Payment Coordination**:
   - Payment module (`POST /api/v1/bookings/{bookingId}/payments`) automatically snapshots `booking.total_amount` (which already reflects the promotion discount).
   - Once a booking is created, the discount in `booking_promotions` and `booking.total_amount` is 100% immutable.
   - VNPay IPN and Return handlers never recalculate promotions or alter discounted amounts.

### 2.2 Explicitly Out of Scope (V1 Promotion Backend)

- **Promotion Stacking**: Combining multiple promotion codes in a single booking is strictly forbidden in V1 (at most 1 promotion per booking).
- **User-Specific Targeting**: Targeted vouchers for specific user IDs or customer segments (VIP, new users) are deferred to V2 (`SCHEMA GAP / FUTURE V2`).
- **Per-Customer Usage Limits**: Restricting a user to $N$ uses of a promotion (e.g. "1 use per customer") is deferred to V2 (`SCHEMA GAP / FUTURE V2` - in V1, `usage_limit` is global).
- **Entity-Specific Scope Rules**: Restricting promotions to specific movies, genres, cinemas, days of week, or showtimes is deferred to V2 (`SCHEMA GAP / FUTURE V2`).
- **Auto-Applying Best Promotion**: System automatically picking the highest discount voucher is out of scope. Customers must explicitly supply a code.
- **Refund Policy for Discounted Bookings**: Full/partial refund calculation is deferred to the future Refund module V2. Promotion records store historical truth.

---

## 3. Domain Relationships & Cardinality

```text
       Admin
         │ creates & manages
         ▼
     Promotion (1)
         │
         │ 1
         ▼ *
  BookingPromotion (M:N Snapshot Table)
         ▲ *
         │ 1
     Booking (1) ──────────► Showtime ──────────► Auditorium ──────────► Cinema
         │ 1                     │ 1
         ├──────────────┬────────┤
         │ 1            │ 1      │
         ▼ *            ▼ *      │
      SeatHold        Ticket     │
         │              │        │
         │              ▼ *      │
         │            Payment    │
         │              ▲        │
         │              │ 1      │
         └──────────────┴────────┘
```

### Cardinality & Invariants
- **Promotion ─── BookingPromotion (`1:N`)**: A single promotion can be applied to many bookings over time across different customers (up to `usage_limit`).
- **Booking ─── BookingPromotion (`1:1` in V1)**: While the relational schema `booking_promotions` supports M:N via composite PK `(promotion_id, booking_id)`, **V1 enforces a strict business rule of at most 1 promotion per booking**.
- **Booking ─── Payment (`1:N`)**: A booking may have multiple payment attempts, but all payment attempts snapshot the net `booking.total_amount` established at booking creation time.
- **Promotion Immutability**: Modifying a promotion's future dates, discount values, or status in Admin has ZERO retroactive effect on existing `booking_promotions` or `bookings`.

---

## 4. Existing Database Model

The database schema (`docs/database.md` §3.8 and existing JPA entities) defines the authoritative structure:

### 4.1 `promotions` Table
```sql
CREATE TABLE promotions (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500) NULL,
    discount_type VARCHAR(20) NOT NULL,       -- 'PERCENTAGE', 'FIXED_AMOUNT'
    discount_value DECIMAL(12,2) NOT NULL,    -- Percentage (e.g. 10.00) or Flat Amount (e.g. 50000.00)
    min_order_amount DECIMAL(12,2) NULL,      -- Minimum gross booking amount required
    max_discount_amount DECIMAL(12,2) NULL,   -- Cap for PERCENTAGE discounts
    start_at DATETIME NOT NULL,               -- Start of validity window
    end_at DATETIME NOT NULL,                 -- End of validity window (end_at > start_at)
    usage_limit INT UNSIGNED NULL,            -- Maximum total successful usages (NULL = unlimited)
    used_count INT UNSIGNED NOT NULL DEFAULT 0, -- Current count of applied usages
    status VARCHAR(20) NOT NULL,              -- 'ACTIVE', 'INACTIVE', 'EXPIRED'
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,        -- Optimistic lock
    PRIMARY KEY (id),
    UNIQUE KEY uk_promotions_code (code),
    INDEX idx_promotions_status_period (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 4.2 `booking_promotions` Table (Immutable Snapshot)
```sql
CREATE TABLE booking_promotions (
    promotion_id VARCHAR(36) NOT NULL,
    booking_id VARCHAR(36) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,   -- Exact VND amount discounted for this booking
    created_at DATETIME NOT NULL,
    PRIMARY KEY (promotion_id, booking_id),
    INDEX idx_booking_promotions_booking (booking_id),
    CONSTRAINT fk_booking_promotions_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (id),
    CONSTRAINT fk_booking_promotions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 5. Promotion Lifecycle & State Machine

```text
                       ┌──────────────┐
                       │    ACTIVE    │ ◄────── Admin Re-activate (if not expired)
                       └──────┬───────┘
                              │
            ┌─────────────────┼─────────────────┐
            │                 │                 │
     Admin Deactivate   Natural Expiry    Usage Limit Reached
            │           (end_at <= now)   (used_count >= limit)
            ▼                 ▼                 ▼
     ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
     │   INACTIVE   │  │   EXPIRED    │  │ ACTIVE (Max) │
     └──────┬───────┘  └──────────────┘  └──────────────┘
            │
            └────────► Admin Reactivate ──► ACTIVE
```

### 5.1 Status Definitions (`PromotionStatus`)
- **`ACTIVE`**: Promotion is active and can be applied to new bookings (provided `start_at <= now < end_at` and `used_count < usage_limit`).
- **`INACTIVE`**: Manually disabled by Admin. Cannot be applied to any new bookings regardless of date or quota.
- **`EXPIRED`**: The promotion's `end_at` timestamp has passed (`now >= end_at`). Cannot be applied to new bookings.

### 5.2 State Transition Invariants
1. A promotion is created with status `ACTIVE` by default (or `INACTIVE` if Admin explicitly drafts it).
2. Admin can toggle status between `ACTIVE` and `INACTIVE` at any time via `PATCH /api/v1/admin/promotions/{id}/status`.
3. If `now >= end_at`, the promotion is considered expired regardless of the stored `status` column. Background housekeeping or read queries can lazily update `status` to `EXPIRED`.
4. Deactivating or expiring a promotion NEVER cancels, recalculates, or invalidates discounts on already created bookings.

---

## 6. Admin Promotion Management

### 6.1 Create Promotion (`POST /api/v1/admin/promotions`)
- **Authorization**: `ADMIN` strictly required.
- **Validation Rules**:
  - `code`: Not blank, max 50 chars, sanitized to uppercase alphanumeric (`^[A-Z0-9_-]+$`). Must be globally unique (`uk_promotions_code`).
  - `name`: Not blank, max 255 chars.
  - `description`: Optional, max 500 chars.
  - `discountType`: Must be `PERCENTAGE` or `FIXED_AMOUNT`.
  - `discountValue`: Must be $> 0$.
    - If `PERCENTAGE`: Must be $\le 100.00$ (e.g. 10% to 100%).
    - If `FIXED_AMOUNT`: Must be $> 0$ (e.g. 20,000 to 500,000 VND).
  - `minOrderAmount`: Optional, if present must be $\ge 0$.
  - `maxDiscountAmount`: Optional, if present must be $> 0$. Applicable primarily to `PERCENTAGE` discounts.
  - `startAt`: Must not be null.
  - `endAt`: Must not be null and strictly $> \text{startAt}$.
  - `usageLimit`: Optional integer, if present must be $\ge 1$. (Null = unlimited).
  - Initial `status`: Defaults to `ACTIVE` if omitted.
  - Initial `usedCount`: Always initialized to `0`.

### 6.2 Update Promotion (`PUT /api/v1/admin/promotions/{id}`)
- **Authorization**: `ADMIN` strictly required.
- **Immutable Fields**:
  - `id`: Immutable primary key.
  - `code`: Immutable business identifier (prevent breaking external advertising/links).
  - `discountType`: Immutable once created (to preserve accounting audit trail).
  - `usedCount`: Read-only, mutated strictly by booking transactions.
- **Mutable Fields**:
  - `name`, `description`, `minOrderAmount`, `maxDiscountAmount`, `startAt`, `endAt`, `usageLimit`.
  - If `usageLimit` is updated, the new value must be $\ge \text{usedCount}$ (or null).
  - `endAt` must still be $> \text{startAt}$.

### 6.3 List & Search Promotions (`GET /api/v1/admin/promotions`)
- **Query Parameters**:
  - `status`: Filter by `ACTIVE`, `INACTIVE`, `EXPIRED`.
  - `search`: Case-insensitive partial match on `code` or `name`.
  - `page`, `size`, `sort`: Standard Spring Data pagination (defaults: `page=0, size=20, sort=createdAt,DESC`).

---

## 7. Customer Promotion Validation & Application

### 7.1 Validation Eligibility Rules
A promotion code is **eligible** for a booking if and only if ALL of the following criteria pass:

1. **Existence**: A record exists in `promotions` matching `code` (case-insensitive lookup, normalized to uppercase).
2. **Status**: `promotion.status == PromotionStatus.ACTIVE`.
3. **Temporal Validity**:
   $$\text{promotion.start\_at} \le \text{now} < \text{promotion.end\_at}$$
4. **Global Usage Quota**:
   $$\text{promotion.usage\_limit} == \text{null} \quad \lor \quad \text{promotion.used\_count} < \text{promotion.usage\_limit}$$
5. **Order Minimum Threshold**:
   $$\text{promotion.min\_order\_amount} == \text{null} \quad \lor \quad \text{grossBookingAmount} \ge \text{promotion.min\_order\_amount}$$
6. **Positive Discount**: The calculated discount amount must be $> 0$.

### 7.2 Standalone Validation Endpoint (`GET /api/v1/promotions/validate`)
- **Auth**: Public or Authenticated Customer.
- **Purpose**: Allows frontend checkout to test a voucher code and display the discount preview before user confirms booking creation.
- **Request Parameters**:
  - `code` (String, required): Promotion code.
  - `grossAmount` (BigDecimal, required): Estimated gross total of selected seats.
- **Response**:
  - `valid`: `true`/`false`
  - `code`: Sanitized code
  - `discountType`: `PERCENTAGE` / `FIXED_AMOUNT`
  - `discountValue`: Stored value
  - `discountAmount`: Computed discount amount in VND
  - `finalAmount`: Estimated payable amount ($\text{grossAmount} - \text{discountAmount}$)
  - `message`: Explanatory message in Vietnamese (e.g. "Áp dụng mã giảm giá thành công", "Mã giảm giá đã hết hạn").

---

## 8. Discount Calculation Engine & Rounding Rules

### 8.1 Calculation Formulas

#### Gross Booking Amount Definition
$$\text{grossAmount} = \sum_{i=1}^{N} \left( \text{showtime.base\_price} + \text{seat}_i.\text{seatType.price\_modifier} \right)$$
*(Note: `grossAmount` is always $\ge 0$)*.

#### A. Percentage Discount (`PERCENTAGE`)
1. Calculate raw discount:
   $$\text{rawDiscount} = \text{grossAmount} \times \left( \frac{\text{discountValue}}{100} \right)$$
2. Apply maximum discount ceiling (`max_discount_amount`) if configured:
   $$\text{cappedDiscount} = \begin{cases} 
   \min(\text{rawDiscount}, \text{max\_discount\_amount}) & \text{if } \text{max\_discount\_amount} \neq \text{null} \\
   \text{rawDiscount} & \text{otherwise}
   \end{cases}$$
3. Rounding Strategy:
   $$\text{discountAmount} = \text{cappedDiscount.setScale(2, RoundingMode.HALF\_UP)}$$

#### B. Fixed Amount Discount (`FIXED_AMOUNT`)
1. Deduct flat amount, capped by gross booking total (cannot exceed order total):
   $$\text{discountAmount} = \min(\text{discountValue}, \text{grossAmount})$$

#### Net Booking Total Calculation
$$\text{netTotalAmount} = \max\left( \text{BigDecimal.ZERO}, \text{grossAmount} - \text{discountAmount} \right)$$

### 8.2 Calculation Invariants
1. **No Negative Totals**: $\text{netTotalAmount} \ge 0$.
2. **Discount Cannot Exceed Gross**: $0 \le \text{discountAmount} \le \text{grossAmount}$.
3. **High Precision Math**: All computations use `java.math.BigDecimal` with explicit scale and `RoundingMode.HALF_UP`. `float` and `double` are strictly forbidden.
4. **Zero-Amount Booking Support**: If $\text{discountAmount} == \text{grossAmount}$, $\text{netTotalAmount} = 0.00$. (In V1, payment initiation with total = 0 can be handled or validated; VNPay Sandbox requires $> 0$, but CineBook core business rule preserves total $\ge 0$).

---

## 9. Usage Limit & Concurrency Control Strategy

### 9.1 The Concurrency Race Condition Problem
Consider a flash sale voucher:
- `usage_limit = 100`
- `used_count = 99` (exactly 1 use remaining)
- Two customer requests (Thread A and Thread B) create bookings simultaneously with the same code.

If both read `used_count == 99`, both validate successfully, both create bookings, and both increment `used_count`, the final `used_count = 101` (violating `used_count <= usage_limit`).

### 9.2 Locking & Concurrency Architecture

CineBook enforces concurrency protection via **Database-Level Pessimistic Locking (`PESSIMISTIC_WRITE`)** within the Monolith Layered Architecture (no Redis distributed lock needed):

```java
// In PromotionRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Promotion p WHERE p.code = :code")
Optional<Promotion> findByCodeWithLock(@Param("code") String code);
```

### 9.3 Execution Flow under Lock:
1. Thread A enters `createBooking` `@Transactional` boundary.
2. Thread A executes `promotionRepository.findByCodeWithLock("FLASH100")`. MySQL executes `SELECT ... FOR UPDATE`, obtaining an exclusive row lock on the promotion record.
3. Thread B attempts `findByCodeWithLock("FLASH100")` and blocks waiting for Thread A's lock.
4. Thread A verifies `used_count (99) < usage_limit (100)`.
5. Thread A increments `used_count = 100`, creates `Booking`, creates `BookingPromotion`, creates `SeatHold` records, and commits transaction. Row lock is released.
6. Thread B acquires lock, reads freshly committed `used_count = 100`.
7. Thread B checks `used_count (100) < usage_limit (100)` $\rightarrow$ **Fails validation**.
8. Thread B throws `ConflictException("Mã giảm giá đã hết lượt sử dụng.")` (HTTP 409 Conflict) and rolls back cleanly.

---

## 10. Booking Integration & Snapshotting Workflow

### 10.1 Booking Creation Flow (`POST /api/v1/bookings`)

```text
Customer HTTP Request: { showtimeId, seatIds, promotionCode? }
    ↓
BookingController.createBooking()
    ↓
BookingServiceImpl.createBooking() [@Transactional(rollbackFor = Exception.class)]
    ├── 1. Validate Showtime, Cinema, Auditorium, Seat status & Seat availability
    ├── 2. Calculate Gross Ticket Total: grossAmount = sum(basePrice + priceModifier)
    ├── 3. IF promotionCode is present:
    │      ├── Promotion promo = promotionRepository.findByCodeWithLock(code)
    │      ├── Validate status, start_at <= now < end_at, min_order_amount, used_count < usage_limit
    │      ├── discountAmount = calculateDiscount(promo, grossAmount)
    │      ├── promo.setUsedCount(promo.getUsedCount() + 1)
    │      └── promotionRepository.save(promo)
    │   ELSE:
    │      └── discountAmount = 0.00
    ├── 4. netTotal = max(0, grossAmount - discountAmount)
    ├── 5. Create & Save Booking (status = PENDING_PAYMENT, total_amount = netTotal, hold_expires_at = now + 5m)
    ├── 6. IF promotion applied:
    │      └── Create & Save BookingPromotion(promotion, booking, discountAmount, now)
    ├── 7. Create & Save SeatHolds (hold_expires_at = now + 5m)
    └── 8. Construct BookingDetailResponse (including promotion discount info)
    ↓
COMMIT TRANSACTION & Return 201 Created
```

### 10.2 Booking Detail Response Contract Enrichment
`BookingDetailResponse` includes promotion summary details when a voucher is attached:
```json
{
  "id": "b001-uuid",
  "bookingCode": "CB-20260901-001",
  "bookingStatus": "PENDING_PAYMENT",
  "grossAmount": 200000.00,
  "discountAmount": 40000.00,
  "totalAmount": 160000.00,
  "holdExpiresAt": "2026-09-01T19:05:00",
  "promotion": {
    "code": "SUMMER20",
    "name": "Giảm 20% mùa hè",
    "discountAmount": 40000.00
  },
  "seats": [ ... ],
  "tickets": [ ... ]
}
```

---

## 11. Payment Boundary & Financial Amount Integrity

### 11.1 Financial Alignment Guarantees
1. **Source of Truth for Payment Amount**:
   - `PaymentServiceImpl.initiatePayment` reads `booking.getTotalAmount()`.
   - Because `booking.total_amount` was stored with the net discounted amount at booking creation, `payment.amount` is guaranteed to match the net payable total:
     $$\text{payment.amount} \equiv \text{booking.total\_amount}$$
2. **VNPay Gateway Amount**:
   - `vnp_Amount = payment.amount × 100` (e.g. $160,000 \times 100 = 16,000,000$).
   - Customer pays the exact discounted price on VNPay Sandbox.
3. **IPN Authoritative Verification**:
   - VNPay IPN verifies `incomingAmount == payment.amount × 100`.
   - On success, `bookingService.confirmPaidBooking` confirms the booking without re-evaluating promotions.
4. **Promotion Module Boundaries**:
   - Promotion service MUST NOT interact directly with VNPay.
   - Promotion service MUST NOT create or mutate `payments` records.
   - Payment module MUST NOT alter `booking_promotions` or recalculate discounts.

---

## 12. Booking Cancellation, Expiration & Quota Release Policy

### 12.1 Quota Release on Incomplete Bookings
When a customer applies a voucher, `used_count` is temporarily reserved during the 5-minute seat hold window. If the booking is not completed, the quota MUST be released back to the pool:

#### Case A: Customer Cancels Booking (`POST /api/v1/bookings/{id}/cancel`)
1. Booking status transitions `PENDING_PAYMENT → CANCELLED`.
2. Temporary `seat_holds` are deleted immediately.
3. System checks `booking_promotions` for the booking.
4. If a promotion was applied, acquire pessimistic lock on `Promotion` and decrement `used_count = max(0, used_count - 1)`.

#### Case B: 5-Minute Hold Expires (`BookingCleanupTask`)
1. `BookingCleanupTask` identifies expired `PENDING_PAYMENT` bookings (`hold_expires_at <= now()`).
2. Booking status transitions `PENDING_PAYMENT → EXPIRED`.
3. Temporary `seat_holds` are deleted.
4. For each expired booking with an associated `booking_promotions`, decrement `promotions.used_count = max(0, used_count - 1)`.

#### Case C: Booking is PAID (`confirmPaidBooking`)
1. Booking transitions `PENDING_PAYMENT → PAID`.
2. Promotion usage is **permanently consumed**. `used_count` is NOT decremented.

---

## 13. Security, Authorization & Sensitive Data Rules

### 13.1 RBAC Enforcement
| Endpoint | Method | Path | Required Role | Notes |
|---|---|---|---|---|
| Create Promotion | `POST` | `/api/v1/admin/promotions` | `ADMIN` | Configures discount & quota |
| List Promotions | `GET` | `/api/v1/admin/promotions` | `ADMIN` | Admin pagination & filters |
| Get Promotion Detail | `GET` | `/api/v1/admin/promotions/{id}` | `ADMIN` | Includes internal stats |
| Update Promotion | `PUT` | `/api/v1/admin/promotions/{id}` | `ADMIN` | Updates parameters |
| Toggle Status | `PATCH` | `/api/v1/admin/promotions/{id}/status` | `ADMIN` | Activate/Deactivate |
| Validate Code Preview | `GET` | `/api/v1/promotions/validate` | Public / Authenticated | Read-only calculation |
| Apply in Booking | `POST` | `/api/v1/bookings` | `CUSTOMER` / `ADMIN` | Client sends code only |

### 13.2 Security Invariants
1. **Zero Client Trust on Financials**: Clients NEVER submit `discountAmount`, `usedCount`, `totalAmount`, or `status`. All pricing and promotion rules are computed authoritatively on the backend.
2. **Code Sanitization**: Promotion codes are trimmed, uppercased, and validated against SQL injection / regex patterns.
3. **No Internal Data Leaks**: Customer error messages state clear business reasons (e.g. "Mã giảm giá không tồn tại hoặc đã hết hạn") without leaking database constraint names, stack traces, or other users' usage.

---

## 14. REST API Contract & DTO Specification

### 14.1 DTO Definitions

#### `CreatePromotionRequest` (Admin)
```java
public record CreatePromotionRequest(
    @NotBlank(message = "Promotion code is required")
    @Size(max = 50, message = "Code must not exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code must contain only letters, numbers, hyphens, and underscores")
    String code,

    @NotBlank(message = "Promotion name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    String name,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description,

    @NotNull(message = "Discount type is required")
    PromotionDiscountType discountType,

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be greater than 0")
    BigDecimal discountValue,

    @DecimalMin(value = "0.00", message = "Min order amount must be non-negative")
    BigDecimal minOrderAmount,

    @DecimalMin(value = "0.01", message = "Max discount amount must be greater than 0")
    BigDecimal maxDiscountAmount,

    @NotNull(message = "Start date is required")
    LocalDateTime startAt,

    @NotNull(message = "End date is required")
    LocalDateTime endAt,

    @Min(value = 1, message = "Usage limit must be at least 1")
    Integer usageLimit,

    PromotionStatus status
) {}
```

#### `UpdatePromotionRequest` (Admin)
```java
public record UpdatePromotionRequest(
    @NotBlank(message = "Promotion name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    String name,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description,

    @DecimalMin(value = "0.00", message = "Min order amount must be non-negative")
    BigDecimal minOrderAmount,

    @DecimalMin(value = "0.01", message = "Max discount amount must be greater than 0")
    BigDecimal maxDiscountAmount,

    @NotNull(message = "Start date is required")
    LocalDateTime startAt,

    @NotNull(message = "End date is required")
    LocalDateTime endAt,

    @Min(value = 1, message = "Usage limit must be at least 1")
    Integer usageLimit
) {}
```

#### `PromotionResponse` (Admin / Detail)
```java
public record PromotionResponse(
    String id,
    String code,
    String name,
    String description,
    PromotionDiscountType discountType,
    BigDecimal discountValue,
    BigDecimal minOrderAmount,
    BigDecimal maxDiscountAmount,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Integer usageLimit,
    Integer usedCount,
    Integer remainingUses,
    PromotionStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

#### `ValidatePromotionResponse` (Customer Preview)
```java
public record ValidatePromotionResponse(
    boolean valid,
    String code,
    String name,
    PromotionDiscountType discountType,
    BigDecimal discountValue,
    BigDecimal grossAmount,
    BigDecimal discountAmount,
    BigDecimal finalAmount,
    String message
) {}
```

---

## 15. Error Handling & Standardized HTTP Status Codes

| Scenario | HTTP Status | Error Code / Exception | User-Facing Message |
|---|---|---|---|
| Promotion code not found | `404 Not Found` / `400 Bad Request` | `ResourceNotFoundException` / `BadRequestException` | "Mã giảm giá không tồn tại." |
| Promotion status is `INACTIVE` | `400 Bad Request` | `BadRequestException` | "Mã giảm giá hiện đang tạm khóa." |
| Promotion has not started yet (`now < start_at`) | `400 Bad Request` | `BadRequestException` | "Mã giảm giá chưa đến thời gian áp dụng." |
| Promotion has expired (`now >= end_at`) | `400 Bad Request` | `BadRequestException` | "Mã giảm giá đã hết hạn sử dụng." |
| Order subtotal < `min_order_amount` | `400 Bad Request` | `BadRequestException` | "Đơn đặt vé chưa đạt giá trị tối thiểu để áp dụng mã này." |
| Quota exhausted (`used_count >= usage_limit`) | `409 Conflict` / `400 Bad Request` | `ConflictException` | "Mã giảm giá đã hết lượt sử dụng." |
| Duplicate promotion code on creation | `409 Conflict` | `ConflictException` | "Mã giảm giá đã tồn tại trong hệ thống." |
| Attempt to stack multiple promotions | `400 Bad Request` | `BadRequestException` | "Mỗi đơn đặt vé chỉ được áp dụng tối đa 1 mã giảm giá." |
| Customer tries to modify immutable code | `400 Bad Request` | `BadRequestException` | "Không thể thay đổi mã giảm giá của khuyến mãi đã tạo." |

---

## 16. Transaction Boundaries & Atomicity Analysis

```text
┌────────────────────────────────────────────────────────────────────────┐
│               POST /api/v1/bookings Transaction Boundary               │
│                                                                        │
│  1. Check seats availability                                           │
│  2. Calculate gross ticket price                                       │
│  3. SELECT promotion FOR UPDATE (Locking Promotion row)               │
│  4. Validate all promo rules                                           │
│  5. promo.used_count++                                                 │
│  6. Save Promotion                                                     │
│  7. Save Booking (total_amount = net)                                  │
│  8. Save BookingPromotion (snapshot)                                   │
│  9. Save SeatHolds                                                     │
│                                                                        │
│  COMMIT -> All changes persisted simultaneously                        │
│  ROLLBACK -> If ANY step fails (seat held by another, invalid promo),  │
│              promo.used_count is NOT incremented, no booking created!  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 17. Edge Cases & Invariants

| # | Edge Case | Handled Behavior |
|---|---|---|
| 1 | **100% Discount ($100\%$) or Fixed Discount $\ge$ Gross** | `netTotalAmount = 0.00`. `discount_amount` is capped at `grossAmount`. `booking.total_amount` is never negative. |
| 2 | **Concurrent creation with last remaining usage (`usage_limit = 100`, `used_count = 99`)** | Pessimistic lock serializes requests. First acquires and increments to 100; second acquires lock, detects `100 >= 100`, and receives 409 Conflict. |
| 3 | **Customer enters lowercase code (`summer2026`)** | System normalizes input using `.trim().toUpperCase()` before querying database. |
| 4 | **Admin deactivates promotion while customer is browsing** | Validation during `POST /api/v1/bookings` re-checks `status == ACTIVE` inside the transaction and rejects with 400 Bad Request. |
| 5 | **Customer creates booking with promotion, but abandons payment (Hold expires)** | `BookingCleanupTask` cleans up booking and decrements `promotions.used_count`, releasing quota back to public. |
| 6 | **Admin updates `usageLimit` to a value lower than existing `usedCount`** | Update validation rejects request (`400 Bad Request: "Hạn mức sử dụng mới không thể nhỏ hơn số lượt đã sử dụng"`). |
| 7 | **Promotion with both percentage and `max_discount_amount`** | Discount is calculated as percentage and capped at `max_discount_amount`. |

---

## 18. Testing Strategy

### 18.1 Unit Tests (`PromotionServiceTest`)
- Create promotion happy path (percentage & fixed).
- Duplicate code creation $\rightarrow$ 409 Conflict.
- Invalid date ranges (`end_at <= start_at`) $\rightarrow$ 400 Bad Request.
- Validation: Valid, inactive, not started, expired, below min order, quota exhausted.
- Discount calculations: Percentage, percentage with cap, fixed amount, fixed amount exceeding gross total, precision rounding.
- Update promotion: Metadata updates, invalid quota reductions.
- Toggle status: `ACTIVE` $\leftrightarrow$ `INACTIVE`.

### 18.2 Booking Integration Tests (`BookingPromotionIntegrationTest`)
- Create booking with valid promotion $\rightarrow$ `booking.total_amount` discounted, `booking_promotions` snapshot created, `promotions.used_count` incremented.
- Create booking with invalid promotion $\rightarrow$ Transaction rolled back, seats not held, `used_count` unchanged.
- Cancel booking $\rightarrow$ `promotions.used_count` decremented.
- Hold expiration via `BookingCleanupTask` $\rightarrow$ `promotions.used_count` decremented.
- Paid confirmation via `confirmPaidBooking` $\rightarrow$ `used_count` remains permanently consumed.

### 18.3 Concurrency Tests (`PromotionConcurrencyTest`)
- 10 simultaneous threads attempting to apply the last 1 remaining usage of a promotion.
- Verification: Exactly 1 thread succeeds (Booking created, `used_count` incremented to limit); exactly 9 threads fail with 409 Conflict; final `used_count == usage_limit`.

### 18.4 Security Tests (`PromotionSecurityTest`)
- Anonymous request to `/api/v1/admin/promotions/**` $\rightarrow$ 401 Unauthorized.
- Customer role request to `/api/v1/admin/promotions/**` $\rightarrow$ 403 Forbidden.
- Admin role request to `/api/v1/admin/promotions/**` $\rightarrow$ 200 OK.
- Customer request to `POST /api/v1/bookings` with `promotionCode` $\rightarrow$ 201 Created.

### 18.5 Regression Test Suite
- Run `.\mvnw.cmd clean test` ensuring all 301+ existing tests continue to pass.

---

## 19. Cross-Document Synchronization Plan

Upon approval of this specification:
1. **`docs/business-rules.md`**: Update §10 to remove TODO blocks and document locked V1 rules (1 promotion per booking, discount calculation formulas, quota release on expiration).
2. **`docs/api.md`**: Update §10 to specify exact Admin CRUD and Customer validation endpoints.
3. **`docs/use-cases/booking.md`**: Note promotion code parameter in `POST /api/v1/bookings`.
4. **`docs/database.md`**: Keep in sync (no schema changes needed, existing schema is 100% sufficient).

---

## 20. Open Decisions & Scope Clarifications

### 20.1 Locked V1 Decisions
1. **Cardinality**: Maximum 1 promotion per booking. No voucher stacking.
2. **Supported Discount Types**: `PERCENTAGE` and `FIXED_AMOUNT`.
3. **Pessimistic Locking**: `Promotion` row locked via `findByCodeWithLock` during booking creation to guarantee concurrency safety on `used_count`.
4. **Quota Release**: Expired / cancelled `PENDING_PAYMENT` bookings release their `used_count` reservation.
5. **Zero Schema Modifications**: V1 uses the existing `promotions` and `booking_promotions` tables in `docs/database.md`.

### 20.2 Deferred to V2 (Future Scope)
1. **Per-user usage limit**: "1 use per customer" (requires tracking customer booking history per promotion).
2. **Entity targeting**: Restricting vouchers to specific movies, cinemas, or seat types.
3. **Voucher refund policy**: Detailed refund policies for vouchers on cancelled paid bookings.

---

## 21. Implementation Notes & Guidelines

1. **Layered Architecture**:
   - `PromotionController` & `AdminPromotionController` (Thin web layer, DTO validation)
   - `PromotionService` / `PromotionServiceImpl` (Business logic, calculations, lifecycle)
   - `PromotionRepository` & `BookingPromotionRepository` (Spring Data JPA persistence)
2. **Exception Handling**: Reuse standard CineBook exception hierarchy (`BadRequestException`, `ConflictException`, `ResourceNotFoundException`, `ForbiddenException`).
3. **Code Quality**: Clean code, zero unused dependencies, English code symbols, Vietnamese user-facing messages.

