# Payment Domain Specification (VNPay Sandbox) — Payment V2 & Refund

## 0. Purpose

This document is the **canonical specification** for the **CineBook Payment Management, VNPay Sandbox Integration, and Refund Module (Payment V2)**.

It defines the architectural boundaries, database models, payment lifecycle, VNPay Sandbox communication protocol, cryptographic signature verification, IPN authoritative processing, return handling, financial amount integrity, refund lifecycle, ticket cancellation, seat release invariants, idempotency guarantees, security authorization, and testing strategy.

---

## 1. Canonical References

The implementation agent MUST adhere to:

- `AGENTS.md` (Monolith rules, Layered architecture, security constraints, testing requirements)
- `docs/documentation-map.md`
- `docs/architecture.md` (Layered Architecture, Monolith deployment unit)
- `docs/database.md` (§3.6 Booking, §3.7 Payment & Refunds, §3.8 Promotion, §5 Constraints, §6 Statuses)
- `docs/business-rules.md` (§9 Payment, §8 Booking & Seat Hold, §10 Promotion, §2 Authorization)
- `docs/api.md` (§9 Payment, §8 Booking)
- `docs/payment.md` (VNPay Sandbox parameters, HMAC-SHA512, IPN mechanics)
- `docs/use-cases/booking.md` (Booking ↔ Payment domain coordination, `confirmPaidBooking`)
- `docs/use-cases/promotion.md` (Promotion immutable snapshot & voucher quota rules)
- `.agents/rules/backend.md`
- `.agents/rules/security.md`
- `.agents/rules/database.md`
- `.agents/skills/implement-backend-feature/SKILL.md`

### Source Priority Order
1. Explicit developer instructions
2. `AGENTS.md`
3. Existing working source code (`Payment.java`, `Refund.java`, `Booking.java`, `Ticket.java`, `PaymentStatus.java`, `RefundStatus.java`, `BookingStatus.java`, `TicketStatus.java`, `BookingServiceImpl.java`)
4. Current database schema (`docs/database.md`)
5. `docs/business-rules.md` & `docs/payment.md`
6. `docs/api.md`
7. Sibling use-case specifications (`docs/use-cases/booking.md`, `docs/use-cases/promotion.md`)

---

## 2. Domain Scope & Architectural Boundaries

### 2.1 In Scope (Payment V2 & Refund)

1. **Payment Initiation (`POST /api/v1/bookings/{bookingId}/payments`)**:
   - Customer authentication & ownership verification (`booking.user_id == currentUserId` or `ROLE_ADMIN`).
   - Booking eligibility validation (status `PENDING_PAYMENT`, `hold_expires_at > now()`, active seat holds exist in `seat_holds`).
   - Snapshot payment amount strictly from `booking.total_amount` (net amount after promotion discount). Client cannot control amount.
   - Create `Payment` record with status `PENDING`, method `VNPAY`, unique `payment_code`.
   - Acquire pessimistic row lock on `Booking` during initiation to prevent concurrent payment sessions.
   - Enforce single active `PENDING` payment invariant (`existsByBookingIdAndPaymentStatus(bookingId, PENDING)`).
   - Construct standard VNPay parameters (version `2.1.0`, command `pay`, amount × 100 integer, transaction reference).
   - Compute `HMAC-SHA512` secure hash using `vnp_HashSecret`.
   - Return redirect payment URL to client.

2. **VNPay IPN Callback (`GET/POST /api/v1/payments/vnpay/ipn`) — Authoritative Confirmation Engine**:
   - Validate incoming `HMAC-SHA512` cryptographic checksum using constant-time comparison.
   - Verify `vnp_TmnCode` matches merchant configuration.
   - Lookup `Payment` record by `vnp_TxnRef` (`payments.payment_code`).
   - Verify transaction amount (`vnp_Amount / 100 == payments.amount`).
   - Enforce idempotency (duplicate IPN requests acknowledge `02 Order already confirmed` without re-executing transitions).
   - On verified `SUCCESS` (`vnp_ResponseCode == "00"` and `vnp_TransactionStatus == "00"`):
     - Atomically update `payments` (`payment_status = SUCCESS`, `paid_at = now()`, `gateway_transaction_id = vnp_TransactionNo`, `gateway_response = rawJson`).
     - Delegate to Booking module hook `bookingService.confirmPaidBooking(bookingId, paymentId)` to transition `Booking → PAID`, issue `VALID` tickets, and remove temporary seat holds.
     - **Financial Race Handling**: If payment succeeds on gateway (`00`) but IPN arrives after booking hold expired, payment remains `SUCCESS` (recorded for audit/refund), booking remains `EXPIRED`, zero tickets are issued, and a high-priority audit event is logged.
   - On verified `FAILED` / `CANCELLED` (`vnp_ResponseCode != "00"`):
     - Update `payments` (`payment_status = FAILED` or `CANCELLED`, `gateway_response = rawJson`).
     - Booking remains `PENDING_PAYMENT` until 5-minute hold timeout or customer cancellation.
   - Return standard VNPay IPN JSON acknowledgement (`RspCode`, `Message`).

3. **VNPay Return Endpoint (`GET/POST /api/v1/payments/vnpay/return`) — User Experience Only**:
   - Public endpoint handling browser redirection from VNPay portal.
   - Validate `HMAC-SHA512` signature.
   - Identify payment by `vnp_TxnRef`.
   - Read gateway response code for display/redirection purposes.
   - **Strict Invariant**: Return endpoint NEVER mutates payment or booking state to `SUCCESS`/`PAID`.

4. **Refund Domain Capability (`POST /api/v1/payments/{paymentId}/refund` & `POST /api/v1/admin/bookings/{bookingId}/refund`)**:
   - Process full refund for paid bookings.
   - Strict eligibility validation:
     - Payment must be in `SUCCESS` status.
     - Booking must be in `PAID` status (or orphaned `EXPIRED` with `SUCCESS` payment).
     - Showtime start time check: for customer-initiated refunds, request must be at least 2 hours before `showtime.startTime`. Admin refunds can be processed at any time before/after showtime.
     - Ticket status check: zero tickets in the booking may be in `USED` status.
   - Authoritative refund amount: strictly equal to `payment.amount` (`booking.total_amount`). Client cannot specify amount.
   - Idempotency protection: database unique constraint `uk_refunds_payment` ensures at most 1 refund record per payment.
   - Gateway integration: invoke VNPay Refund API (`vnp_Command = refund`) with secure hash.
   - State transitions:
     - `Payment`: `SUCCESS → REFUNDED`.
     - `Booking`: `PAID → REFUNDED` (or `EXPIRED → REFUNDED` for orphaned payments).
     - `Ticket`: All tickets in booking transition from `VALID → CANCELLED`.
     - `Seats`: Automatically become `AVAILABLE` once tickets become `CANCELLED`.
     - `Promotion`: Voucher quota is **NOT restored** on refund (preserves immutable historical consumption and prevents reuse abuse).
     - `Refund`: Created with status `SUCCESS` (or `FAILED` if gateway rejects).

5. **Payment & Refund Querying**:
   - `GET /api/v1/payments/{id}`: View payment details (Owner or Admin).
   - `GET /api/v1/payments/{id}/refund`: View refund details for a payment (Owner or Admin).
   - `GET /api/v1/admin/refunds`: Search and list all refunds with pagination and filtering (Admin only).

### 2.2 Explicitly Out of Scope

- **Partial Refunds**: Partial ticket refunds within a multi-seat booking are deferred (V2 only performs full order refunds).
- **Multiple Gateways**: MoMo, ZaloPay, ShopeePay, Stripe (V2 strictly uses VNPay Sandbox).
- **Cashier / POS Cash Payments**: Physical box-office cash transactions.

---

## 3. Database Model & Schema Mapping

### 3.1 `payments` Table Mapping

```sql
CREATE TABLE `payments` (
    `id` varchar(36) NOT NULL,
    `booking_id` varchar(36) NOT NULL,
    `payment_method` varchar(20) NOT NULL,
    `payment_code` varchar(50) NOT NULL,
    `gateway_transaction_id` varchar(100) DEFAULT NULL,
    `amount` decimal(12,2) NOT NULL,
    `payment_status` varchar(20) NOT NULL,
    `paid_at` datetime DEFAULT NULL,
    `gateway_response` json DEFAULT NULL,
    `created_at` datetime NOT NULL,
    `updated_at` datetime NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payments_code` (`payment_code`),
    KEY `idx_payments_booking` (`booking_id`),
    KEY `idx_payments_gateway_transaction` (`gateway_transaction_id`),
    CONSTRAINT `fk_payments_booking` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.2 `refunds` Table Mapping

```sql
CREATE TABLE `refunds` (
    `id` varchar(36) NOT NULL,
    `payment_id` varchar(36) NOT NULL,
    `refund_code` varchar(50) NOT NULL,
    `gateway_refund_id` varchar(100) DEFAULT NULL,
    `amount` decimal(12,2) NOT NULL,
    `refund_reason` varchar(255) DEFAULT NULL,
    `refund_status` varchar(20) NOT NULL,
    `processed_at` datetime DEFAULT NULL,
    `created_at` datetime NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refunds_payment` (`payment_id`),
    UNIQUE KEY `uk_refunds_code` (`refund_code`),
    KEY `idx_refunds_gateway_id` (`gateway_refund_id`),
    CONSTRAINT `fk_refunds_payment` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.3 Status Enum Definitions

| Enum | Allowed Values |
|---|---|
| `PaymentStatus` | `PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `REFUNDED` |
| `RefundStatus` | `PENDING`, `SUCCESS`, `FAILED` |
| `PaymentMethod` | `VNPAY` |
| `BookingStatus` | `HOLDING`, `PENDING_PAYMENT`, `PAID`, `CANCELLED`, `EXPIRED`, `REFUNDED` |
| `TicketStatus` | `VALID`, `USED`, `CANCELLED` |

---

## 4. State Machines & Lifecycle Invariants

### 4.1 Payment State Machine

```text
               ┌────────────────┐
               │    PENDING     │
               └───────┬────────┘
                       │
       ┌───────────────┼───────────────┐
       │ (IPN "00")    │ (User Cancel) │ (IPN != "00")
       ▼               ▼               ▼
┌──────────────┐┌──────────────┐┌──────────────┐
│   SUCCESS    ││  CANCELLED   ││    FAILED    │
└──────┬───────┘└──────────────┘└──────────────┘
       │
       │ (Refund Succeeded)
       ▼
┌──────────────┐
│   REFUNDED   │
└──────────────┘
```

### 4.2 Booking State Machine (Payment & Refund Transitions)

```text
┌─────────────────┐
│ PENDING_PAYMENT │
└────────┬────────┘
         │
         │ (confirmPaidBooking)
         ▼
  ┌─────────────┐
  │    PAID     │
  └──────┬──────┘
         │
         │ (Refund Succeeded)
         ▼
  ┌─────────────┐
  │  REFUNDED   │
  └─────────────┘
```

### 4.3 Ticket State Machine

```text
┌─────────────┐
│    VALID    │
└──────┬──────┘
       │
       ├─────────────────────────┐
       │ (Checked in at cinema)  │ (Booking Refunded)
       ▼                         ▼
┌─────────────┐           ┌─────────────┐
│    USED     │           │  CANCELLED  │
└─────────────┘           └─────────────┘
```

---

## 5. Financial Integrity & Authoritative Source of Truth

### 5.1 Amount Derivation Pipeline

$$\text{Seat Base Price} + \text{Seat Type Modifier} \longrightarrow \text{Gross Amount}$$
$$\text{Gross Amount} - \text{Promotion Discount} \longrightarrow \text{booking.total\_amount}$$
$$\text{booking.total\_amount} \longrightarrow \text{payment.amount} \longrightarrow \text{vnp\_Amount} = \text{amount} \times 100$$
$$\text{payment.amount} \longrightarrow \text{refund.amount}$$

### 5.2 Financial Invariants
1. **Zero Client Trust**: Client never supplies payment amount or refund amount in request payloads.
2. **Fixed Scale Arithmetic**: All monetary fields use `BigDecimal` with 2 decimal places (`scale = 2`, `RoundingMode.HALF_UP`).
3. **Gateway Conversion**: VNPay integer amount = `payment.amount.multiply(BigDecimal.valueOf(100)).longValue()`.
4. **Promotion Net Total**: `booking.total_amount` reflects the net amount after voucher discount. Payment amount and refund amount strictly match `booking.total_amount`.

---

## 6. VNPay Sandbox Protocol

### 6.1 Payment Initiation Parameters

| Parameter | Type | Required | Description | Value in CineBook |
|---|---|---|---|---|
| `vnp_Version` | String | Yes | VNPay API Version | `2.1.0` |
| `vnp_Command` | String | Yes | API Command | `pay` |
| `vnp_TmnCode` | String | Yes | Merchant Terminal Code | Configured via `${VNPAY_TMN_CODE}` |
| `vnp_Amount` | Long | Yes | Amount in VND × 100 | `payment.amount * 100` |
| `vnp_CurrCode` | String | Yes | Currency code | `VND` |
| `vnp_TxnRef` | String | Yes | Transaction reference | `payments.payment_code` |
| `vnp_OrderInfo` | String | Yes | Order description | `Thanh toan ve xem phim {bookingCode}` |
| `vnp_OrderType` | String | Yes | Category code | `other` / configured value |
| `vnp_Locale` | String | Yes | Language | `vn` |
| `vnp_ReturnUrl` | String | Yes | Browser redirect URL | Configured return endpoint |
| `vnp_IpAddr` | String | Yes | Client IP address | Extracted client IP |
| `vnp_CreateDate` | String | Yes | Creation timestamp | `yyyyMMddHHmmss` (Vietnam GMT+7) |
| `vnp_ExpireDate` | String | Yes | Expiration timestamp | `booking.hold_expires_at` (`yyyyMMddHHmmss`) |
| `vnp_SecureHash` | String | Yes | HMAC-SHA512 signature | Computed over sorted URL-encoded query string |

### 6.2 VNPay Refund API Protocol (`vnp_Command = refund`)

VNPay Sandbox provides a server-to-server refund endpoint:
- **Refund Endpoint URL**: `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`
- **HTTP Method**: `POST` (JSON Content-Type)

| Parameter | Type | Required | Description | Value in CineBook |
|---|---|---|---|---|
| `vnp_RequestId` | String | Yes | Unique request ID | `UUID.randomUUID().toString()` |
| `vnp_Version` | String | Yes | VNPay API Version | `2.1.0` |
| `vnp_Command` | String | Yes | Command | `refund` |
| `vnp_TmnCode` | String | Yes | Merchant Terminal Code | Configured `${VNPAY_TMN_CODE}` |
| `vnp_TransactionType` | String | Yes | Refund Type | `02` (Full Refund) |
| `vnp_TxnRef` | String | Yes | Merchant order transaction ID | `payments.payment_code` |
| `vnp_Amount` | Long | Yes | Refund amount × 100 | `refund.amount * 100` |
| `vnp_OrderInfo` | String | Yes | Refund reason | `Hoan tien don dat ve {bookingCode}: {reason}` |
| `vnp_TransactionNo` | String | No | Gateway transaction number | `payment.gateway_transaction_id` (if available) |
| `vnp_TransactionDate` | String | Yes | Original payment date | `payment.paid_at` (`yyyyMMddHHmmss`) |
| `vnp_CreateBy` | String | Yes | User initiating refund | Username / Email |
| `vnp_CreateDate` | String | Yes | Request creation time | `yyyyMMddHHmmss` |
| `vnp_IpAddr` | String | Yes | Server IP address | Extracted IP / `127.0.0.1` |
| `vnp_SecureHash` | String | Yes | HMAC-SHA512 signature | `vnp_RequestId|vnp_Version|vnp_Command|vnp_TmnCode|vnp_TransactionType|vnp_TxnRef|vnp_Amount|vnp_TransactionNo|vnp_TransactionDate|vnp_CreateBy|vnp_CreateDate|vnp_IpAddr|vnp_OrderInfo` |

---

## 7. Refund Domain Rules & Business Invariants

### 7.1 Refund Authorization & Eligibility Matrix

| Condition | Customer | Admin | Behavior if Violated |
|---|---|---|---|
| **Payment Status** | Must be `SUCCESS` | Must be `SUCCESS` | `400 Bad Request` ("Chỉ có thể hoàn tiền cho giao dịch đã thanh toán thành công.") |
| **Booking Status** | Must be `PAID` | `PAID` or orphaned `EXPIRED` | `400 Bad Request` ("Trạng thái đơn đặt vé không hợp lệ để hoàn tiền.") |
| **Already Refunded** | Reject duplicate | Return existing refund | `409 Conflict` (or idempotent response) |
| **Showtime Window** | $\ge 2$ hours before `startTime` | Any time | `400 Bad Request` ("Chỉ có thể hoàn tiền trước giờ chiếu ít nhất 2 tiếng.") |
| **Ticket Usage** | Zero `USED` tickets | Zero `USED` tickets | `400 Bad Request` ("Không thể hoàn tiền đơn hàng đã có vé được sử dụng.") |
| **Ownership** | Must own booking | Any booking | `403 Forbidden` |

### 7.2 Refund + Promotion Invariant
- **Rule**: When a `PAID` booking is refunded, `promotions.used_count` is **NOT decremented**.
- **Rationale**: The promotion voucher was legally consumed by the original successful transaction. Restoring quota on refund creates financial abuse vectors (coupon farming, repeated refund-rebuy loops). The historical record in `booking_promotions` remains immutable.

### 7.3 Refund + Ticket & Seat Invariant
- All tickets associated with the booking are transitioned from `ticket_status = VALID` to `ticket_status = CANCELLED`.
- Because seat availability in CineBook queries active `VALID` tickets, marking tickets `CANCELLED` immediately and automatically makes the seats `AVAILABLE` for new customer bookings.

### 7.4 Idempotency Invariants
- `uk_refunds_payment` ensures a payment can have at most one `Refund` record in the database.
- Concurrent refund requests acquire a pessimistic lock on `Payment` row (`paymentRepository.findByIdWithLock(paymentId)`).
- If payment is already in `REFUNDED` status, the service returns the existing `RefundResponse` without calling the payment gateway again.

---

## 8. API Specification

### 8.1 Existing APIs (Maintained & Hardened)

#### 1. Initiate Payment
- `POST /api/v1/bookings/{bookingId}/payments`
- **Auth**: `ROLE_CUSTOMER` (Owner) or `ROLE_ADMIN`
- **Request**:
  ```json
  {
    "paymentMethod": "VNPAY"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "paymentId": "uuid",
    "paymentCode": "PAY-20260831-ABCD1234",
    "amount": 90000.00,
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
    "expiresAt": "2026-08-31T22:05:00"
  }
  ```

#### 2. VNPay IPN Webhook (Authoritative Server-to-Server)
- `GET/POST /api/v1/payments/vnpay/ipn`
- **Auth**: Public (HMAC-SHA512 verification)
- **Response**: `200 OK`
  ```json
  {
    "RspCode": "00",
    "Message": "Confirm Success"
  }
  ```

#### 3. VNPay Return URL (User Browser Redirect)
- `GET/POST /api/v1/payments/vnpay/return`
- **Auth**: Public (HMAC-SHA512 verification)
- **Response**: `200 OK` (`PaymentResultResponse`)

#### 4. Get Payment Detail
- `GET /api/v1/payments/{id}`
- **Auth**: `ROLE_CUSTOMER` (Owner) or `ROLE_ADMIN`
- **Response**: `200 OK` (`PaymentSummaryResponse`)

---

### 8.2 New Refund APIs

#### 1. Customer / Admin Refund Payment
- `POST /api/v1/payments/{paymentId}/refund`
- **Auth**: `ROLE_CUSTOMER` (Owner) or `ROLE_ADMIN`
- **Request**:
  ```json
  {
    "reason": "Khách hàng bận đột xuất, yêu cầu hoàn tiền."
  }
  ```
- **Response**: `200 OK` (`RefundResponse`)
  ```json
  {
    "id": "uuid-refund",
    "paymentId": "uuid-payment",
    "refundCode": "REF-20260831-XYZ987",
    "gatewayRefundId": "VNP-REF-123456",
    "amount": 90000.00,
    "refundReason": "Khách hàng bận đột xuất, yêu cầu hoàn tiền.",
    "refundStatus": "SUCCESS",
    "processedAt": "2026-08-31T22:15:00",
    "createdAt": "2026-08-31T22:15:00"
  }
  ```

#### 2. Get Refund Detail by Payment ID
- `GET /api/v1/payments/{paymentId}/refund`
- **Auth**: `ROLE_CUSTOMER` (Owner) or `ROLE_ADMIN`
- **Response**: `200 OK` (`RefundResponse`)

#### 3. Admin Search & List Refunds
- `GET /api/v1/admin/refunds?status=SUCCESS&page=0&size=20`
- **Auth**: `ROLE_ADMIN`
- **Response**: `200 OK` (`PageResponse<RefundResponse>`)

---

## 9. Transaction Boundaries & Concurrency Strategy

### 9.1 Transaction Boundaries

| Method | Boundary | Lock Acquired | Actions inside Transaction |
|---|---|---|---|
| `initiatePayment` | `@Transactional` | `Booking` (`PESSIMISTIC_WRITE`) | Validate hold & status, check single pending, save `Payment`, build URL |
| `processIpn` | Method-level | `Payment` & `Booking` via `confirmPaidBooking` | Save `Payment(SUCCESS)`, invoke `confirmPaidBooking`, issue `Ticket(VALID)`, delete `seat_holds` |
| `refundPayment` | `@Transactional` | `Payment` (`PESSIMISTIC_WRITE`) | Validate eligibility & showtime, call VNPay Refund API, update `Payment(REFUNDED)`, update `Booking(REFUNDED)`, cancel `Tickets`, save `Refund(SUCCESS)` |

### 9.2 Concurrency Protections
1. **Concurrent Refund Requests**: Protected by `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `PaymentRepository.findByIdWithLock(paymentId)` and database unique constraint `uk_refunds_payment`.
2. **Concurrent IPN Callbacks**: Idempotency check `payment.paymentStatus != PENDING` ensures duplicate IPN messages immediately exit with `02 Order already confirmed`.
3. **Cancel vs IPN Race**: If customer cancels before IPN arrives, booking becomes `CANCELLED`. When IPN arrives with `00`, `confirmPaidBooking` throws exception; payment is safely recorded as `SUCCESS` for manual/automated reconciliation without corrupting booking or issuing duplicate tickets.

---

## 10. Database Migration Decision

**Option A: ZERO Database Schema Migration.**

All tables (`payments`, `refunds`, `bookings`, `tickets`, `booking_promotions`, `promotions`), foreign keys, unique constraints (`uk_refunds_payment`, `uk_refunds_code`, `uk_payments_code`), and enum values (`PaymentStatus.REFUNDED`, `RefundStatus.SUCCESS`, `BookingStatus.REFUNDED`, `TicketStatus.CANCELLED`) already exist in MySQL and JPA entities. Zero DDL changes needed.

---

## 11. Testing Strategy

### 11.1 Unit Tests (`PaymentServiceTest`, `VnPayServiceTest`, `RefundServiceTest`)
- Amount integrity & 100x integer multiplication.
- Cryptographic signature generation and verification.
- Status transition matrix: `PENDING → SUCCESS`, `PENDING → FAILED`, `PENDING → CANCELLED`, `SUCCESS → REFUNDED`.
- Eligibility validation: showtime < 2h rejection for customer, `USED` ticket rejection, non-`PAID` booking rejection.
- Idempotency: duplicate refund request handling.

### 11.2 Integration Tests (`PaymentRefundIntegrationTest`)
- Full payment initiation $\rightarrow$ IPN confirmation $\rightarrow$ ticket issuance.
- Full refund flow: `PAID` booking $\rightarrow$ refund API $\rightarrow$ `Payment(REFUNDED)` $\rightarrow$ `Booking(REFUNDED)` $\rightarrow$ `Ticket(CANCELLED)` $\rightarrow$ Seat availability verified.
- Promotion non-restoration verification: `promotions.used_count` is NOT decremented on refund.

### 11.3 Concurrency Tests (`PaymentRefundConcurrencyTest`)
- 10 concurrent threads attempting to refund the same payment $\rightarrow$ Exactly 1 refund processed, zero duplicate gateway refund calls.
- Concurrent IPN confirmation vs Customer cancellation.

### 11.4 Security Tests (`PaymentSecurityTest`)
- Anonymous accessing `/api/v1/payments/{id}/refund` $\rightarrow$ `401 Unauthorized`.
- Customer accessing another customer's payment refund $\rightarrow$ `403 Forbidden`.
- Customer accessing own payment refund $\rightarrow$ `200 OK`.
- Admin accessing `/api/v1/admin/refunds` $\rightarrow$ `200 OK`.

---

## 12. Definition of Done for Payment V2 & Refund

1. Payment initiation, IPN webhook, return handling, and refund domain logic fully implemented following Monolith Layered Architecture.
2. Full test suite passes (`.\mvnw.cmd clean test`) with zero failures and zero regressions across all 345+ existing tests.
3. E2E verification confirms payment initiation, IPN confirmation, booking state update, ticket issuance, and full refund workflow via Swagger / REST API.
