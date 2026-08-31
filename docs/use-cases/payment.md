# Payment Domain Specification (VNPay Sandbox)

## 0. Purpose

This document is the canonical specification for the **CineBook Payment Management & VNPay Sandbox Integration module**.

It defines the architectural boundaries, database models, payment lifecycle, VNPay Sandbox communication protocol, cryptographic signature verification, IPN authoritative processing, return handling, financial amount integrity, idempotency invariants, and testing strategy for processing online ticket payments.

---

## 1. Canonical References

The implementation agent MUST adhere to:

- `AGENTS.md` (Monolith rules, security constraints, testing requirements)
- `docs/documentation-map.md`
- `docs/architecture.md` (Layered Architecture, Monolith deployment unit)
- `docs/database.md` (§3.7 Payment & Refunds, §5 Constraints, §6 Statuses)
- `docs/business-rules.md` (§9 Payment, §8 Booking & Seat Hold, §2 Authorization)
- `docs/api.md` (§9 Payment, §8 Booking)
- `docs/payment.md` (VNPay Sandbox parameters, HMAC-SHA512, IPN mechanics)
- `docs/use-cases/booking.md` (Booking ↔ Payment domain coordination, `confirmPaidBooking`)
- `.agents/rules/backend.md`
- `.agents/rules/security.md`
- `.agents/rules/database.md`
- `.agents/skills/implement-backend-feature/SKILL.md`

### Source Priority Order
1. Explicit developer instructions (Locked V1 Decisions in this document)
2. `AGENTS.md`
3. Existing working source code (`Payment.java`, `Booking.java`, `PaymentStatus.java`, `PaymentMethod.java`, `BookingServiceImpl.java`)
4. Current database schema (`docs/database.md`)
5. `docs/payment.md` & `docs/business-rules.md`
6. `docs/api.md`
7. Sibling use-case specifications (`docs/use-cases/booking.md`)

---

## 2. Domain Scope & Architectural Boundaries

### 2.1 In Scope (V1 Payment Backend)

1. **Payment Initiation (`POST /api/v1/bookings/{bookingId}/payments`)**:
   - Customer authentication & ownership verification (`booking.user_id == currentUserId`).
   - Booking eligibility validation (status `PENDING_PAYMENT`, `hold_expires_at > now()`, active seat holds exist).
   - Snapshot payment amount strictly from `booking.total_amount`.
   - Create `Payment` record with status `PENDING`, method `VNPAY`, unique `payment_code`.
   - Construct standard VNPay query parameters (version `2.1.0`, command `pay`, amount × 100, transaction reference).
   - Compute `HMAC-SHA512` secure hash using `vnp_HashSecret`.
   - Return redirect payment URL to client.
2. **VNPay IPN Callback (`GET/POST /api/v1/payments/vnpay/ipn`) — Authoritative State Engine**:
   - Validate incoming HMAC-SHA512 cryptographic checksum.
   - Lookup `Payment` record by `vnp_TxnRef` (`payments.payment_code`).
   - Verify transaction amount (`vnp_Amount / 100 == payments.amount`).
   - Enforce idempotency (duplicate IPN requests acknowledge without re-executing transitions).
   - On verified `SUCCESS` (`vnp_ResponseCode == "00"`):
     - Atomically update `payments` (`payment_status = SUCCESS`, `paid_at = now()`, `gateway_transaction_id`, `gateway_response`).
     - Delegate to Booking module hook `bookingService.confirmPaidBooking(bookingId, paymentId)` to transition `Booking → PAID`, create `VALID` tickets, and remove temporary seat holds.
   - On verified `FAILED` / `CANCELLED` (`vnp_ResponseCode != "00"`):
     - Update `payments` (`payment_status = FAILED` or `CANCELLED`, `gateway_response`).
     - Booking remains `PENDING_PAYMENT` until 5-minute hold timeout or customer cancellation.
   - Return standard VNPay IPN JSON acknowledgement (`RspCode`, `Message`).
3. **VNPay Return Endpoint (`GET/POST /api/v1/payments/vnpay/return`) — User Experience Only**:
   - Public endpoint handling browser redirection from VNPay.
   - Validate HMAC-SHA512 signature.
   - Identify payment by `vnp_TxnRef`.
   - Read gateway response code for display/redirection purposes.
   - **Strict Invariant**: Return endpoint NEVER independently mutates payment or booking state to `SUCCESS`/`PAID`.
4. **Financial Amount Integrity**:
   - Zero client trust for payment amount.
   - Deterministic integer conversion (`amount × 100`) using `BigDecimal`. No floating-point arithmetic.
5. **Security & Secrets Management**:
   - Zero hardcoded credentials (`VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` in environment variables).
   - Never log or expose payment secrets in logs, API responses, or exceptions.

### 2.2 Explicitly Out of Scope (V2+ / Future Modules)

- **Payment Retry Engine**: Initiating secondary payment attempts on a single booking is an architectural capability (1-N schema) deferred for V2.
- **Refund Processing & VNPay Refund API**: Automated refund workflows and admin refund UI are deferred for V2 (`docs/use-cases/administration.md`).
- **Additional Payment Gateways**: MoMo, ZaloPay, ShopeePay, Stripe, or direct credit card processing.
- **Frontend / Vue 3 UI**: Payment loading screens, VNPay redirection handlers, Pinia payment store.
- **Cashier / POS In-Person Cash Payments**: Physical box-office ticketing.

---

## 3. Domain Architecture & Relationships

### 3.1 Architecture Overview

```text
┌──────────────────────────────────────────┐           ┌──────────────────────────────────────────┐
│              BOOKING MODULE              │           │              PAYMENT MODULE              │
├──────────────────────────────────────────┤           ├──────────────────────────────────────────┤
│ • Validates seats & showtimes            │           │ • Creates Payment record (booking_id FK) │
│ • Creates 5-min holds                    │           │ • Initiates VNPay Sandbox payment URL    │
│ • Computes booking total_amount          │           │ • Handles VNPay IPN & Return callbacks   │
│ • Manages BookingStatus aggregate        │           │ • Computes & verifies HMAC-SHA512 hashes │
│ • Issues tickets upon verified payment   │◄──────────┤ • Verifies financial transaction amounts │
│ • Releases temporary holds on paid/cancel│ (Success) │ • Manages PaymentStatus state machine    │
│ • No direct gateway / VNPay SDK calls    │           │ • No direct ticket/seat manipulation     │
└──────────────────────────────────────────┘           └──────────────────────────────────────────┘
```

### 3.2 Cardinality: Booking 1 ───── N Payment

The database schema and JPA entities enforce a **1-to-N relationship between `Booking` and `Payment`**:

```text
User (Customer)
  │ 1
  │
  ▼ *
Booking (1) ───────────► (N) Payment (1) ───────────► (0..1) Refund
  │ 1                         │ 1
  ├──────────────┬────────────┤
  │ 1            │ 1          │
  ▼ *            ▼ *          │
SeatHold       Ticket         │
  │              │            │
  └──────────────┴────────────┘
```

- **`Booking → Payment` (`1:N`)**: A booking can have multiple payment records over its lifetime, preserving audit history for failed, cancelled, and successful attempts.
- **No `UNIQUE(payments.booking_id)`**: The database intentionally omits a unique constraint on `booking_id`.
- **`Payment → Refund` (`1:0..1`)**: A payment record can have at most one refund record (`uk_refunds_payment`).
- **V1 Business Policy vs Database Capability**:
  - *Database Capability*: Full 1-N history.
  - *V1 Business Flow*: One payment attempt initiated per standard booking flow. Payment retry is deferred for V2 without requiring any schema changes.

---

## 4. Existing Database Model

The database schema (`docs/database.md` §3.7) defines the authoritative tables:

### 4.1 Table `payments`

| Column | Type | Constraints / Modifiers | Description |
|---|---|---|---|
| `id` | `varchar(36)` | Primary Key (UUID), Not Null | Unique payment entity identifier |
| `booking_id` | `varchar(36)` | FK → `bookings.id`, Not Null | Associated booking (**1:N cardinality**) |
| `payment_method` | `varchar(20)` | Not Null (`VNPAY`, `MOMO`, `CASH`) | Payment gateway / method used |
| `payment_code` | `varchar(50)` | Unique, Not Null (`uk_payments_code`) | Merchant transaction code (`vnp_TxnRef`) |
| `gateway_transaction_id`| `varchar(100)` | Nullable | VNPay transaction number (`vnp_TransactionNo`) |
| `amount` | `decimal(12,2)` | Not Null, Check `amount >= 0` | Authoritative snapshotted payment amount |
| `payment_status` | `varchar(20)` | Not Null (`PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `REFUNDED`) | Lifecycle state |
| `paid_at` | `datetime` | Nullable | Timestamp when payment confirmed success |
| `gateway_response` | `json` | Nullable | Raw IPN response payload from VNPay |
| `created_at` | `datetime` | Not Null, updatable = false | Entity creation timestamp |
| `updated_at` | `datetime` | Not Null | Entity last update timestamp |

**Indexes & Constraints**:
- Unique: `uk_payments_code` (`payment_code`)
- Index: `idx_payments_booking` (`booking_id`)
- Index: `idx_payments_gateway_transaction` (`gateway_transaction_id`)
- Foreign Key: `fk_payments_booking` → `bookings.id`

### 4.2 Table `refunds` (Reserved for V2+)

| Column | Type | Constraints / Modifiers | Description |
|---|---|---|---|
| `id` | `varchar(36)` | Primary Key (UUID), Not Null | Refund identifier |
| `payment_id` | `varchar(36)` | FK → `payments.id`, Unique, Not Null (`uk_refunds_payment`) | Exactly one refund per payment |
| `refund_code` | `varchar(50)` | Unique, Not Null | Unique refund tracking code |
| `gateway_refund_id`| `varchar(100)`| Nullable | VNPay refund transaction ID |
| `amount` | `decimal(12,2)` | Not Null, Check `amount >= 0` | Refunded amount |
| `refund_reason` | `varchar(255)` | Nullable | Administrative reason for refund |
| `refund_status` | `varchar(20)` | Not Null | Status (`PENDING`, `SUCCESS`, `FAILED`) |
| `processed_at` | `datetime` | Nullable | Execution timestamp |
| `created_at` | `datetime` | Not Null | Creation timestamp |

---

## 5. Authorization & Security

### 5.1 RBAC Matrix

| Endpoint | HTTP Method | Required Role | Auth Mechanism | Notes |
|---|---|---|---|---|
| `/api/v1/bookings/{bookingId}/payments` | `POST` | `ROLE_CUSTOMER` | JWT Bearer Token | User must be owner of `bookingId` (or `ROLE_ADMIN`) |
| `/api/v1/payments/vnpay/ipn` | `GET` / `POST` | **Public** | HMAC-SHA512 Signature | Server-to-server webhook from VNPay |
| `/api/v1/payments/vnpay/return` | `GET` / `POST` | **Public** | HMAC-SHA512 Signature | Browser redirect from VNPay |

### 5.2 Ownership Validation Invariant

When initiating a payment for a booking (`POST /api/v1/bookings/{bookingId}/payments`):

```java
UserDetailsImpl currentUser = SecurityUtils.getCurrentUserDetails()
        .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));

Booking booking = bookingRepository.findById(bookingId)
        .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

boolean isAdmin = currentUser.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

if (!isAdmin && !booking.getUser().getId().equals(currentUser.getId())) {
    throw new ForbiddenException("Bạn không có quyền thanh toán đơn đặt vé này.");
}
```

**Security Rules**:
1. User ID MUST be resolved directly from the authenticated JWT security context (`SecurityUtils.getCurrentUserId()`), never accepted from client body or request parameters.
2. VNPay callbacks (`/ipn` and `/return`) are public and bypass JWT filter chains, authenticated strictly via **HMAC-SHA512 cryptographic verification**.
3. Credentials (`VNPAY_HASH_SECRET`, `VNPAY_TMN_CODE`) MUST NEVER be logged, output in error responses, or exposed to the frontend.

---

## 6. Payment Lifecycle & State Machine

### 6.1 `PaymentStatus` State Machine

```text
               POST /api/v1/bookings/{id}/payments
                               │
                               ▼
                    ┌─────────────────────┐
                    │       PENDING       │
                    └──────────┬──────────┘
                               │
               ┌───────────────┼───────────────┐
               │               │               │
       VNPay IPN Success    VNPay IPN Fail   User Cancel
      (vnp_ResponseCode=00)(ResponseCode!=00)(ResponseCode=24)
               │               │               │
               ▼               ▼               ▼
        ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
        │   SUCCESS   │ │   FAILED    │ │  CANCELLED  │
        └──────┬──────┘ └─────────────┘ └─────────────┘
               │
      (V2+ Admin Refund)
               │
               ▼
        ┌─────────────┐
        │  REFUNDED   │
        └─────────────┘
```

### 6.2 State Transition Matrix

| Current Status | Event / Trigger | Target Status | Triggered By | Invariants & Side Effects |
|---|---|---|---|---|
| *(None)* | Initiate Payment | `PENDING` | Customer | Sourced from `booking.total_amount`, generates `payment_code`. |
| `PENDING` | VNPay IPN Success (`00`) | `SUCCESS` | VNPay IPN Callback | `paid_at = now()`, `gateway_transaction_id` recorded. Invokes `bookingService.confirmPaidBooking()`. |
| `PENDING` | VNPay IPN Error (`!= 00, != 24`)| `FAILED` | VNPay IPN Callback | `gateway_response` saved. Booking remains `PENDING_PAYMENT`. |
| `PENDING` | VNPay IPN Cancel (`24`) | `CANCELLED` | VNPay IPN Callback | Customer clicked Cancel on VNPay gateway. |
| `PENDING` | Booking Housekeeping Timeout | *(No mutation)* | BookingCleanupTask | Payment remains `PENDING`/audit, Booking transitions to `EXPIRED`. |
| `SUCCESS` | Any IPN Callback | *(No mutation)* | Duplicate IPN | **Idempotent**: Returns `RspCode 02` (Order already confirmed). |
| `FAILED` | Any IPN Callback | *(No mutation)* | Duplicate IPN | **Idempotent**: Returns `RspCode 02`. |
| `SUCCESS` | Admin Refund (V2+) | `REFUNDED` | Admin Module | Creates `refunds` record. |

### 6.3 Separation: `PaymentStatus` vs `BookingStatus`

`PaymentStatus` and `BookingStatus` are **distinct state machines** operating across domain boundaries:

| `PaymentStatus` | `BookingStatus` | Relationship & Behavior |
|---|---|---|
| `PENDING` | `PENDING_PAYMENT` | Payment created; customer redirected to VNPay; 5-minute seat hold running. |
| `SUCCESS` | `PAID` | Payment verified; Booking immediately transitioned to `PAID`; tickets issued; seat holds deleted. |
| `FAILED` | `PENDING_PAYMENT` | Gateway transaction failed. Booking remains `PENDING_PAYMENT` until 5-minute hold expires (or user cancels). There is **no `BookingStatus.FAILED`**. |
| `CANCELLED` | `PENDING_PAYMENT` / `CANCELLED` | User cancelled on VNPay. Booking remains `PENDING_PAYMENT` until customer explicitly cancels or hold expires. |

---

## 7. Create Payment Flow

### Endpoint: `POST /api/v1/bookings/{bookingId}/payments`

```text
Customer Client                Payment Controller / Service                 Database / BookingService
      │                                    │                                           │
      │ 1. POST /bookings/{id}/payments    │                                           │
      │    { "paymentMethod": "VNPAY" }    │                                           │
      ├───────────────────────────────────►│                                           │
      │                                    │ 2. Authenticate & Verify Ownership        │
      │                                    │    booking.user_id == currentUserId       │
      │                                    ├──────────────────────────────────────────►│
      │                                    │◄──────────────────────────────────────────┤
      │                                    │                                           │
      │                                    │ 3. Validate Booking Invariants:           │
      │                                    │    • status == PENDING_PAYMENT            │
      │                                    │    • hold_expires_at > now()              │
      │                                    │    • active seat_holds exist              │
      │                                    │                                           │
      │                                    │ 4. Snapshot booking.total_amount          │
      │                                    │    Create Payment (status = PENDING)      │
      │                                    ├──────────────────────────────────────────►│
      │                                    │◄──────────────────────────────────────────┤
      │                                    │                                           │
      │                                    │ 5. Construct VNPay Parameters             │
      │                                    │    • vnp_Amount = amount * 100            │
      │                                    │    • vnp_TxnRef = payment.payment_code    │
      │                                    │    • Compute HMAC-SHA512 SecureHash       │
      │                                    │                                           │
      │ 6. 200 OK (paymentUrl, expiresAt)  │                                           │
      │◄───────────────────────────────────┤                                           │
      │                                    │                                           │
      │ 7. Browser redirects to paymentUrl │                                           │
      ├───────────────────────────────────► VNPay Sandbox                              │
```

### Execution Steps & Rules

1. **Authentication & Security Context**:
   - Extract `currentUserId` from JWT Security Context.
2. **Pessimistic Lock & Booking Lookup**:
   - Acquire exclusive write lock on Booking: `bookingRepository.findByIdWithLock(bookingId)`. Throw `ResourceNotFoundException (404)` if not found.
   - Verify ownership: Ensure `booking.getUser().getId().equals(currentUserId)` or caller has `ROLE_ADMIN`. Throw `ForbiddenException (403)` if unauthorized.
3. **Booking Eligibility Invariants**:
   - `booking.getBookingStatus() == BookingStatus.PENDING_PAYMENT`. If already `PAID`, `CANCELLED`, or `EXPIRED`, throw `BadRequestException (400)`.
   - `booking.getHoldExpiresAt() > LocalDateTime.now()`. If expired, throw `BadRequestException (400)` (`"Đơn đặt vé đã hết hạn giữ chỗ."`).
   - Active seat holds exist for the booking (`seatHoldRepository.findByBookingId(bookingId).size() > 0`). If holds missing, throw `BadRequestException (400)`.
4. **Single Active PENDING Payment Concurrency Protection**:
   - Inside the locked transaction, check if an active `PENDING` payment already exists:
     `paymentRepository.existsByBookingIdAndPaymentStatus(booking.getId(), PaymentStatus.PENDING)`.
   - If a `PENDING` payment exists, throw `ConflictException (409)` (`"Đơn đặt vé đang có một phiên thanh toán đang chờ xử lý. Vui lòng hoàn tất hoặc chờ giao dịch hết hạn."`).
   - *Payment Retry Policy*: If previous payments for this booking are in terminal non-success states (`FAILED` or `CANCELLED`), initiating a new payment attempt is fully allowed as long as the booking is still `PENDING_PAYMENT` and seat holds are still unexpired (`hold_expires_at > now()`).
5. **Amount Snapshotting**:
   - Read `booking.getTotalAmount()`.
   - Verify `totalAmount.compareTo(BigDecimal.ZERO) > 0`.
   - Client body MUST NOT provide amount. Amount is strictly sourced from `bookings.total_amount`.
6. **Payment Entity Creation & Commit**:
   - Generate unique `paymentCode`: `PAY-` + date prefix (`yyyyMMdd`) + 8 alphanumeric secure random characters (e.g. `PAY-20260901-7F8A2B1C`).
   - Create and persist `Payment` entity (`booking`, `paymentMethod = VNPAY`, `paymentCode`, `amount`, `paymentStatus = PENDING`).
   - Flush and complete transaction.
7. **VNPay URL Construction (Out of Transaction)**:
   - Build parameter map per Section 8.
   - Calculate `vnp_SecureHash` via HMAC-SHA512 over sorted URL-encoded parameters.
   - Assemble full redirect URL: `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?` + query string + `&vnp_SecureHash=` + hash.
8. **Response**: Return `200 OK` with `InitiatePaymentResponse` (`paymentId`, `paymentCode`, `amount`, `paymentUrl`, `expiresAt`).

---

## 8. VNPay Request Construction & Field Mapping

### 8.1 VNPay 2.1.0 Request Parameters

| Parameter | Type | Required | Format / Value | Description & Source |
|---|---|---|---|---|
| `vnp_Version` | String | Yes | `2.1.0` | VNPay API protocol version |
| `vnp_Command` | String | Yes | `pay` | API command for payment initiation |
| `vnp_TmnCode` | String | Yes | Max 8 chars | Merchant Terminal ID from `${VNPAY_TMN_CODE}` |
| `vnp_Amount` | Long / String | Yes | Number (e.g. `24000000`) | Payment amount × 100 (`payment.amount * 100`) |
| `vnp_CurrCode` | String | Yes | `VND` | Currency code |
| `vnp_TxnRef` | String | Yes | Max 100 chars | Unique merchant reference = `payment.payment_code` |
| `vnp_OrderInfo` | String | Yes | Max 255 chars | Order description: `"Thanh toan ve xem phim " + booking.booking_code` |
| `vnp_OrderType` | String | Yes | `other` | VNPay category code |
| `vnp_Locale` | String | Yes | `vn` (or `en`) | UI language on payment page |
| `vnp_ReturnUrl` | String | Yes | Max 255 chars | URL to redirect user after payment from `${VNPAY_RETURN_URL}` |
| `vnp_IpAddr` | String | Yes | Max 45 chars | Client IP address extracted from `HttpServletRequest` |
| `vnp_CreateDate` | String | Yes | `yyyyMMddHHmmss` | Request timestamp in GMT+7 |
| `vnp_ExpireDate` | String | Yes | `yyyyMMddHHmmss` | Hold expiration timestamp (`booking.hold_expires_at`) in GMT+7 |
| `vnp_SecureHash` | String | Yes | Max 256 chars | HMAC-SHA512 cryptographic hash of all sorted parameters |

### 8.2 Client IP Extraction

Extract client IP securely from HTTP headers, handling potential reverse proxy headers:
1. `X-Forwarded-For` (first entry in comma-separated list)
2. `X-Real-IP`
3. `request.getRemoteAddr()`
4. Fallback default: `127.0.0.1`

---

## 9. VNPay Secure Hash & Cryptographic Verification

### 9.1 HMAC-SHA512 Hash Generation Algorithm

```text
Parameters Map (key-value)
       │
       ▼
1. Filter out null / empty values
2. Filter out "vnp_SecureHash" and "vnp_SecureHashType"
       │
       ▼
3. Sort all parameter keys in ASCII alphabetical order (Standard US-ASCII)
       │
       ▼
4. URL-encode each key and value using StandardCharsets.US_ASCII (or UTF-8)
       │
       ▼
5. Concatenate as key1=value1&key2=value2&...
       │
       ▼
6. Compute HMAC-SHA512 using vnp_HashSecret (raw bytes in UTF-8)
       │
       ▼
7. Convert output bytes to lowercase Hex string (128 characters)
```

### 9.2 Verification Implementation Rules

```java
public boolean verifySignature(Map<String, String> fields, String secureHash, String secretKey) {
    if (fields == null || secureHash == null || secretKey == null) {
        return false;
    }
    String computedHash = calculateHmacSha512(fields, secretKey);
    return MessageDigest.isEqual(
            computedHash.getBytes(StandardCharsets.UTF_8),
            secureHash.getBytes(StandardCharsets.UTF_8)
    );
}
```

- **Constant-Time Comparison**: Use `MessageDigest.isEqual()` to prevent timing attacks.
- **Hash Secret Protection**: Never log `secretKey` or print intermediate signing strings containing secrets.

---

## 10. IPN Processing Flow (Authoritative State Engine)

### Endpoint: `GET/POST /api/v1/payments/vnpay/ipn`

VNPay calls the IPN URL asynchronously via server-to-server HTTP request. This endpoint is the **sole authoritative engine** for updating payment and booking states.

```text
VNPay Server                                     CineBook IPN Endpoint                                      Database
     │                                                     │                                                    │
     │ 1. HTTP GET/POST /api/v1/payments/vnpay/ipn         │                                                    │
     ├────────────────────────────────────────────────────►│                                                    │
     │                                                     │ 2. Validate HMAC-SHA512 Signature                 │
     │                                                     │    (if invalid -> return RspCode 97)               │
     │                                                     │                                                    │
     │                                                     │ 3. Lookup Payment by vnp_TxnRef (payment_code)     │
     │                                                     ├───────────────────────────────────────────────────►│
     │                                                     │◄───────────────────────────────────────────────────┤
     │                                                     │    (if not found -> return RspCode 01)             │
     │                                                     │                                                    │
     │                                                     │ 4. Verify Amount:                                  │
     │                                                     │    vnp_Amount / 100 == payment.amount              │
     │                                                     │    (if mismatch -> return RspCode 04)              │
     │                                                     │                                                    │
     │                                                     │ 5. Check Idempotency:                              │
     │                                                     │    if payment_status != PENDING                    │
     │                                                     │    (already confirmed -> return RspCode 02)        │
     │                                                     │                                                    │
     │                                                     │ 6. Process State Transition:                       │
     │                                                     │    If vnp_ResponseCode == "00":                    │
     │                                                     │      • payment_status = SUCCESS                    │
     │                                                     │      • paid_at = now()                             │
     │                                                     │      • gateway_transaction_id = vnp_TransactionNo  │
     │                                                     │      • gateway_response = rawJson                  │
     │                                                     │      • Call confirmPaidBooking(bookingId, paymentId)│
     │                                                     │    Else (Failed / Cancelled):                      │
     │                                                     │      • payment_status = FAILED / CANCELLED         │
     │                                                     │      • gateway_response = rawJson                  │
     │                                                     ├───────────────────────────────────────────────────►│
     │                                                     │◄───────────────────────────────────────────────────┤
     │                                                     │                                                    │
     │ 7. 200 OK JSON { RspCode: "00", Message: "..." }    │                                                    │
     │◄────────────────────────────────────────────────────┤                                                    │
```

### 10.1 IPN Response Codes (`IpnResponse`)

VNPay requires a JSON response with exact status codes:

| `RspCode` | `Message` | Condition |
|---|---|---|
| `00` | `Confirm Success` | Transaction processed successfully (or recorded as failed/cancelled). |
| `01` | `Order not Found` | No `Payment` found matching `vnp_TxnRef` (`payment_code`). |
| `02` | `Order already confirmed` | Idempotent response: Payment is already in `SUCCESS` or `FAILED` state. |
| `04` | `Invalid Amount` | `vnp_Amount / 100` does not match `payments.amount`. |
| `97` | `Invalid Checksum` | Cryptographic HMAC-SHA512 verification failed. |
| `99` | `Unknown Error` | System exception during database processing. |

### 10.2 IPN Processing Algorithm

```java
@Transactional(rollbackFor = Exception.class)
public IpnResponse processIpn(Map<String, String> params) {
    // 1. Verify Checksum
    String vnpSecureHash = params.get("vnp_SecureHash");
    if (!vnpayService.verifySignature(params, vnpSecureHash)) {
        return new IpnResponse("97", "Invalid Checksum");
    }

    // 2. Verify Terminal ID (TMN Code)
    String incomingTmnCode = params.get("vnp_TmnCode");
    if (incomingTmnCode == null || !incomingTmnCode.equals(vnPayConfig.getTmnCode())) {
        return new IpnResponse("01", "Order not Found"); // Reject unrecognized merchant ID
    }

    // 3. Find Payment by vnp_TxnRef
    String paymentCode = params.get("vnp_TxnRef");
    Payment payment = paymentRepository.findByPaymentCode(paymentCode).orElse(null);
    if (payment == null) {
        return new IpnResponse("01", "Order not Found");
    }

    // 4. Verify Amount
    long incomingAmount = Long.parseLong(params.get("vnp_Amount"));
    long expectedAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
    if (incomingAmount != expectedAmount) {
        return new IpnResponse("04", "Invalid Amount");
    }

    // 5. Idempotency Check (Terminal states cannot mutate)
    if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
        return new IpnResponse("02", "Order already confirmed");
    }

    // 6. State Transition
    String responseCode = params.get("vnp_ResponseCode");
    String transactionStatus = params.get("vnp_TransactionStatus");
    String transactionNo = params.get("vnp_TransactionNo");
    String rawJson = toJson(params);

    if ("00".equals(responseCode) && ("00".equals(transactionStatus) || transactionStatus == null)) {
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setGatewayTransactionId(transactionNo);
        payment.setGatewayResponse(rawJson);
        paymentRepository.saveAndFlush(payment);

        // Cross-module coordination with Booking
        try {
            bookingService.confirmPaidBooking(payment.getBooking().getId(), payment.getId());
        } catch (AppException ex) {
            // Critical edge case: Booking expired or holds released before payment IPN arrived
            log.error("CRITICAL FINANCIAL EXCEPTION: Payment {} succeeded on VNPay but Booking {} could not be confirmed: {}. Requires V2/Admin reconciliation.",
                    payment.getPaymentCode(), payment.getBooking().getId(), ex.getMessage());
            // Invariant: Payment SUCCESS is retained in database for audit/reconciliation.
            // Booking remains EXPIRED, no tickets are issued, no double-sold seats.
        }
    } else if ("24".equals(responseCode)) {
        payment.setPaymentStatus(PaymentStatus.CANCELLED);
        payment.setGatewayTransactionId(transactionNo);
        payment.setGatewayResponse(rawJson);
        paymentRepository.save(payment);
    } else {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setGatewayTransactionId(transactionNo);
        payment.setGatewayResponse(rawJson);
        paymentRepository.save(payment);
    }

    return new IpnResponse("00", "Confirm Success");
}
```

---

## 11. Return Endpoint Flow (UX & Browser Redirection)

### Endpoint: `GET/POST /api/v1/payments/vnpay/return`

When a customer completes or cancels a transaction on VNPay Sandbox, VNPay redirects the customer's browser to this endpoint.

### 11.1 Purpose & Strict Boundaries

- **User-Facing Display**: Extracts gateway parameters to construct a structured response or redirect to the frontend result view.
- **Strict Invariant**: The return endpoint **MUST NOT** mutate `PaymentStatus` to `SUCCESS` or `BookingStatus` to `PAID`. State changes are reserved exclusively for the verified IPN callback.
- **Frontend Contract**: Returns a structured DTO (`PaymentResultResponse`) containing transaction summary for frontend rendering.

### 11.2 Processing Logic

1. Validate HMAC-SHA512 `vnp_SecureHash`. If invalid, throw `BadRequestException (400)` (`"Chữ ký không hợp lệ."`).
2. Lookup `Payment` by `vnp_TxnRef`. If not found, throw `ResourceNotFoundException (404)`.
3. Check `vnp_ResponseCode`:
   - `00`: Successful transaction (display success).
   - `24`: User cancelled transaction.
   - Others: Failed transaction.
4. Return `PaymentResultResponse`:
   - `bookingId`, `bookingCode`, `paymentCode`, `amount`, `paymentStatus` (current entity status), `responseCode`, `message`.

---

## 12. Cross-Module Coordination (Payment SUCCESS → Booking PAID)

When IPN verifies `PaymentStatus.SUCCESS`, the Payment module coordinates with the Booking module via the canonical service interface:

```java
bookingService.confirmPaidBooking(bookingId, paymentId);
```

### Invariants Maintained by Booking Module
1. **Verification**: Verifies payment belongs to booking, status is `SUCCESS`, amount strictly matches `booking.total_amount`.
2. **State Transition**: Transitions `booking_status = PAID`.
3. **Ticket Issuance**: Creates `Ticket` entities with snapshot price and `qr_code = ticket.id` (UUID).
4. **Hold Cleanup**: Deletes associated `seat_holds` records so seats are permanently sold (`SOLD`).
5. **Idempotency**: If booking is already `PAID`, `confirmPaidBooking` returns current detail safely without re-creating tickets.

---

## 13. Financial Amount & Precision Integrity

1. **Currency**: Vietnam Dong (`VND`).
2. **Internal Database Type**: `DECIMAL(12,2)` with precision 12 and scale 2.
3. **VNPay Unit**: VNPay requires amounts multiplied by 100 without decimals (e.g., `120,000 VND` → `12000000`).
4. **Deterministic Conversion**:
   ```java
   long vnpAmount = amount.multiply(BigDecimal.valueOf(100)).longValue();
   ```
   *Never use floating-point types (`float`, `double`) to avoid precision loss.*
5. **Callback Verification**:
   ```java
   long expectedVnpAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
   if (incomingVnpAmount != expectedVnpAmount) {
       return new IpnResponse("04", "Invalid Amount");
   }
   ```

---

## 14. Idempotency, Race Conditions & Concurrency

### 14.1 Duplicate IPN Requests (Idempotency)
- **Scenario**: VNPay sends retry IPN calls if network latency delays acknowledgement.
- **Handling**: IPN checks `payment.getPaymentStatus() != PaymentStatus.PENDING`. If already `SUCCESS` or `FAILED`, returns `{ "RspCode": "02", "Message": "Order already confirmed" }` immediately without re-executing booking confirmation or creating duplicate tickets.

### 14.2 Concurrent IPN Requests (Optimistic Locking)
- **Scenario**: Two IPN webhook threads arrive simultaneously for the same payment.
- **Handling**:
  - `Booking` entity uses `@Version private Long version;`.
  - `Ticket` entity enforces `@UniqueConstraint(name = "uk_tickets_booking_seat", columnNames = {"booking_id", "seat_id"})`.
  - The first committing thread succeeds. The second thread encounters optimistic lock failure / duplicate ticket constraint, rolls back, and returns `RspCode 02`.

### 14.3 Payment Success vs Booking Expiration Race Condition
- **Scenario**:
  - `10:00:00`: Booking created with 5-minute hold (`hold_expires_at = 10:05:00`).
  - `10:05:01`: Background `BookingCleanupTask` marks booking `EXPIRED` and deletes `seat_holds`.
  - `10:05:02`: VNPay IPN callback arrives with `vnp_ResponseCode = "00"`.
- **Handling & Financial Consistency**:
  - VNPay successfully collected money from customer. Reverting `Payment` to `PENDING` or `FAILED` would contradict financial reality.
  - Therefore, `Payment` is recorded as `SUCCESS` (with `gateway_transaction_id` and raw gateway JSON saved).
  - Calling `confirmPaidBooking` fails because booking is `EXPIRED` (or seat holds no longer exist).
  - The exception is caught and logged with high-priority audit tags (`CRITICAL FINANCIAL EXCEPTION`).
  - **Result**: No tickets are issued for the expired booking. Seats released to the public pool are NOT double-sold.
  - IPN returns `{ "RspCode": "00", "Message": "Confirm Success" }` to prevent VNPay from continuously re-transmitting callbacks.
  - The payment record remains indexed and queryable for manual reconciliation or V2 automated admin refunds.

---

## 15. VNPay Response Codes & Error Mapping

| `vnp_ResponseCode` | Meaning | `PaymentStatus` | Customer Description |
|---|---|---|---|
| `00` | Giao dịch thành công | `SUCCESS` | Giao dịch thanh toán thành công. |
| `07` | Trừ tiền thành công nhưng giao dịch nghi ngờ | `FAILED` | Giao dịch bị nghi ngờ gian lận. |
| `09` | Thẻ/Tài khoản chưa đăng ký Internet Banking | `FAILED` | Thẻ/Tài khoản chưa đăng ký dịch vụ Internet Banking. |
| `10` | Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần | `FAILED` | Xác thực thông tin không chính xác quá 3 lần. |
| `11` | Hết hạn chờ thanh toán | `FAILED` | Đã hết thời gian chờ thanh toán trên cổng VNPay. |
| `12` | Thẻ/Tài khoản bị khóa | `FAILED` | Thẻ hoặc tài khoản thanh toán đang bị khóa. |
| `24` | Khách hàng hủy giao dịch | `CANCELLED` | Khách hàng đã hủy giao dịch trên cổng thanh toán. |
| `51` | Tài khoản không đủ số dư | `FAILED` | Số dư tài khoản không đủ để thực hiện giao dịch. |
| `65` | Tài khoản vượt quá hạn mức giao dịch trong ngày | `FAILED` | Tài khoản đã vượt quá hạn mức giao dịch trong ngày. |
| `75` | Ngân hàng thanh toán đang bảo trì | `FAILED` | Ngân hàng thanh toán đang trong quá trình bảo trì. |
| `99` | Các lỗi khác | `FAILED` | Giao dịch không thành công do lỗi hệ thống ngân hàng. |

---

## 16. Payment Query & History APIs

Payment information in CineBook is exposed primarily through the **Booking Aggregate**:

1. **`GET /api/v1/bookings/{id}` (`BookingDetailResponse`)**:
   - Contains `payments: List<PaymentSummaryResponse>`:
     - `id`: Payment UUID
     - `paymentMethod`: `VNPAY`
     - `paymentCode`: Merchant code (`PAY-...`)
     - `amount`: `decimal(12,2)`
     - `paymentStatus`: `PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`
     - `paidAt`: Timestamp
2. **Dedicated Payment Detail (`GET /api/v1/payments/{id}`)**:
   - Customer (owner) or Admin can query individual payment record details.

---

## 17. Transaction Boundaries & Isolation

1. **Initiate Payment Transaction (`POST /api/v1/bookings/{id}/payments`)**:
   - Scope: Read booking, validate eligibility, save `Payment` entity (`PENDING`).
   - Boundary: Commit database transaction BEFORE generating VNPay URL and returning response. (Avoid open transactions during external network/string formatting).
2. **IPN Processing Transaction (`GET/POST /api/v1/payments/vnpay/ipn`)**:
   - Scope: Lookup payment, verify amount/state, update `Payment`, invoke `confirmPaidBooking` (update `Booking → PAID`, insert `Ticket`s, delete `SeatHold`s).
   - Boundary: Single atomic `@Transactional(rollbackFor = Exception.class)` block ensuring payment status and booking/ticket state commit together.

---

## 18. Validation & Error Scenarios

| Scenario | HTTP Status | Error Code | Client Message |
|---|---|---|---|
| Anonymous user initiates payment | `401 Unauthorized` | `UNAUTHORIZED` | "Full authentication is required to access this resource" |
| User pays for another user's booking | `403 Forbidden` | `FORBIDDEN` | "Bạn không có quyền thanh toán đơn đặt vé này." |
| Booking ID does not exist | `404 Not Found` | `RESOURCE_NOT_FOUND` | "Không tìm thấy đơn đặt vé với id: ..." |
| Booking is not in `PENDING_PAYMENT` (e.g. `PAID`) | `400 Bad Request` | `BAD_REQUEST` | "Đơn đặt vé đã ở trạng thái PAID, không thể tạo thanh toán mới." |
| Booking seat hold expired (`hold_expires_at < now`) | `400 Bad Request` | `BAD_REQUEST` | "Đơn đặt vé đã hết hạn giữ chỗ." |
| Missing seat holds for booking | `400 Bad Request` | `BAD_REQUEST` | "Không tìm thấy thông tin giữ chỗ cho đơn đặt vé này." |
| Invalid IPN Checksum | `200 OK` (VNPay contract) | `97` | `{ "RspCode": "97", "Message": "Invalid Checksum" }` |
| IPN payment code not found | `200 OK` (VNPay contract) | `01` | `{ "RspCode": "01", "Message": "Order not Found" }` |
| IPN amount mismatch | `200 OK` (VNPay contract) | `04` | `{ "RspCode": "04", "Message": "Invalid Amount" }` |
| Duplicate IPN callback | `200 OK` (VNPay contract) | `02` | `{ "RspCode": "02", "Message": "Order already confirmed" }` |

---

## 19. Consolidated Hard Invariants

1. **Zero Client Amount Trust**: The payment amount is ALWAYS sourced from `bookings.total_amount` and snapshotted into `payments.amount`.
2. **IPN Is Authoritative**: The IPN webhook (`/api/v1/payments/vnpay/ipn`) is the ONLY endpoint authorized to transition payment status to `SUCCESS` and booking status to `PAID`.
3. **Return Endpoint Is Read-Only**: The Return endpoint (`/api/v1/payments/vnpay/return`) NEVER mutates state to `SUCCESS` or `PAID`.
4. **Cross-Module Boundary**: The Payment module NEVER directly inserts tickets or deletes seat holds; it strictly invokes `bookingService.confirmPaidBooking()`.
5. **No Double Selling / Ticket Duplication**: Concurrency constraints (`uk_tickets_booking_seat`) and idempotency checks prevent duplicate tickets.
6. **No Phantom Paid Bookings**: An expired booking cannot be converted to `PAID`.
7. **Deterministic Currency Conversion**: Amount conversion between CineBook `DECIMAL(12,2)` and VNPay `integer × 100` uses `BigDecimal` arithmetic.
8. **Constant-Time Hash Comparison**: Signature verification uses `MessageDigest.isEqual()` to prevent timing attacks.
9. **Zero Credential Exposure**: `VNPAY_HASH_SECRET` and `VNPAY_TMN_CODE` are never logged, returned in API payloads, or committed to source control.
10. **Schema 1-N Cardinality**: `Booking 1 ───── N Payment` is preserved without unique constraints on `booking_id`.

---

## 20. API Contract Specifications

### 20.1 Endpoints Summary

| Method | Endpoint | Auth | Request Body | Success Response | Error Codes |
|---|---|---|---|---|---|
| `POST` | `/api/v1/bookings/{bookingId}/payments` | `ROLE_CUSTOMER` | `InitiatePaymentRequest` | `200 OK` (`InitiatePaymentResponse`) | `400`, `401`, `403`, `404` |
| `GET` / `POST` | `/api/v1/payments/vnpay/ipn` | **Public** (Signature) | Query / Form params | `200 OK` (`IpnResponse`) | None (200 JSON with RspCode) |
| `GET` / `POST` | `/api/v1/payments/vnpay/return` | **Public** (Signature) | Query / Form params | `200 OK` (`PaymentResultResponse`) | `400`, `404` |
| `GET` | `/api/v1/payments/{id}` | `ROLE_CUSTOMER` / `ROLE_ADMIN` | None | `200 OK` (`PaymentDetailResponse`) | `401`, `403`, `404` |

### 20.2 DTO Shapes

#### `InitiatePaymentRequest`
```json
{
  "paymentMethod": "VNPAY"
}
```

#### `InitiatePaymentResponse`
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "paymentCode": "PAY-20260901-7F8A2B1C",
  "amount": 240000.00,
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=24000000&vnp_Command=pay&vnp_CreateDate=20260901100000&...",
  "expiresAt": "2026-09-01T10:05:00"
}
```

#### `IpnResponse` (VNPay Acknowledgement)
```json
{
  "RspCode": "00",
  "Message": "Confirm Success"
}
```

#### `PaymentResultResponse` (User Return View)
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "bookingId": "c4b1e840-7988-4c6e-a2f1-9d2123456789",
  "bookingCode": "CB-20260901-8F32A1",
  "paymentCode": "PAY-20260901-7F8A2B1C",
  "amount": 240000.00,
  "paymentStatus": "SUCCESS",
  "responseCode": "00",
  "message": "Giao dịch thành công."
}
```

---

## 21. Configuration Specifications

### 21.1 `application.yml`

```yaml
vnpay:
  tmn-code: ${VNPAY_TMN_CODE:}
  hash-secret: ${VNPAY_HASH_SECRET:}
  payment-url: ${VNPAY_PAYMENT_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}
  return-url: ${VNPAY_RETURN_URL:http://localhost:5173/payment/result}
  version: "2.1.0"
  command: "pay"
  order-type: "other"
```

### 21.2 Environment Variables

| Variable | Required in Dev | Default Value | Description |
|---|---|---|---|
| `VNPAY_TMN_CODE` | Yes (for real sandbox) | (empty) | VNPay Sandbox Merchant Terminal ID |
| `VNPAY_HASH_SECRET` | Yes (for real sandbox) | (empty) | VNPay Sandbox Secret Key for HMAC-SHA512 |
| `VNPAY_PAYMENT_URL` | No | `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` | VNPay Sandbox gateway URL |
| `VNPAY_RETURN_URL` | No | `http://localhost:5173/payment/result` | Frontend payment outcome landing URL |

---

## 22. Risk-Based Testing Strategy

### 22.1 Unit Tests (`PaymentServiceTest`, `VnPayServiceTest`)
- `initiatePayment_Success`: Generates payment record with `PENDING`, correct snapshotted amount, and valid signed URL.
- `initiatePayment_NotOwner_ThrowsForbidden`: Other users rejected with 403 Forbidden.
- `initiatePayment_BookingNotPending_ThrowsBadRequest`: Already paid or cancelled booking rejected with 400 Bad Request.
- `initiatePayment_BookingExpired_ThrowsBadRequest`: Booking past 5-minute hold rejected with 400 Bad Request.
- `verifySignature_ValidHash_ReturnsTrue`: Computes identical HMAC-SHA512.
- `verifySignature_TamperedParam_ReturnsFalse`: Tampered amount or order info rejected.
- `processIpn_Success_UpdatesPaymentAndConfirmsBooking`: `vnp_ResponseCode == "00"` marks `PaymentStatus.SUCCESS` and calls `confirmPaidBooking`.
- `processIpn_FailedResponse_UpdatesPaymentToFailed`: `vnp_ResponseCode != "00"` marks `PaymentStatus.FAILED` without confirming booking.
- `processIpn_CancelledResponse_UpdatesPaymentToCancelled`: `vnp_ResponseCode == "24"` marks `PaymentStatus.CANCELLED`.
- `processIpn_InvalidSignature_ReturnsRspCode97`: Signature mismatch returns `RspCode 97`.
- `processIpn_UnknownPayment_ReturnsRspCode01`: Unknown `payment_code` returns `RspCode 01`.
- `processIpn_AmountMismatch_ReturnsRspCode04`: Mismatched amount returns `RspCode 04`.
- `processIpn_DuplicateCall_ReturnsRspCode02_Idempotent`: Already confirmed payment returns `RspCode 02`.

### 22.2 Concurrency Tests (`PaymentConcurrencyTest`)
- Concurrent duplicate IPN callbacks for the same payment: exactly 1 state transition executes; zero duplicate tickets issued.

### 22.3 Controller Tests (`PaymentControllerTest`)
- Security filter validation on `/api/v1/bookings/{id}/payments` (JWT required).
- Public endpoint validation on `/api/v1/payments/vnpay/ipn` and `/api/v1/payments/vnpay/return` (anonymous allowed).

---

## 23. Manual / Sandbox Verification Checklist

```text
1. [ ] Login as customer -> Create booking for 2 seats -> Status = PENDING_PAYMENT (5-min hold).
2. [ ] Initiate VNPay payment: POST /api/v1/bookings/{id}/payments -> Receive paymentUrl.
3. [ ] Open paymentUrl in browser (VNPay Sandbox hosted page).
4. [ ] Test Scenario A (Success):
       - Input sandbox test card (e.g. NCB: 9704198526191432198, NGUYEN VAN A, OTP: 123456).
       - Complete payment -> Observe Return redirect.
       - Verify IPN received -> payments.payment_status = SUCCESS, bookings.booking_status = PAID.
       - Verify 2 tickets created with VALID status.
5. [ ] Test Scenario B (User Cancellation):
       - Initiate payment on a new booking -> Click Cancel on VNPay gateway.
       - Verify IPN received -> payments.payment_status = CANCELLED.
       - Verify booking remains PENDING_PAYMENT until hold expires.
6. [ ] Test Scenario C (Tampered Amount / Checksum):
       - Send mock IPN request with invalid checksum -> Verify RspCode 97.
       - Send mock IPN with modified vnp_Amount -> Verify RspCode 04.
7. [ ] Test Scenario D (Expired Booking):
       - Create booking -> Wait 5 minutes for hold to expire.
       - Send mock IPN success -> Verify booking confirmation is rejected safely without issuing tickets.
```

> **Local Development Note**: Since VNPay cannot reach `localhost:8080` directly, for automated integration and local manual verification, developers can use a tunneling proxy (such as `ngrok http 8080` or Cloudflare Tunnel) configured in `VNPAY_RETURN_URL` / VNPay Merchant Portal, or use unit/integration test harnesses simulating IPN payloads.

---

## 24. Definition of Done

The Payment Management & VNPay Sandbox module is complete when:

- [ ] `Payment` entity, DTOs, mappers, repository, services, and controllers are fully implemented.
- [ ] HMAC-SHA512 signature computation and constant-time verification are implemented and tested.
- [ ] IPN endpoint functions as the sole authoritative confirmation engine with full idempotency (`RspCode` 00, 01, 02, 04, 97).
- [ ] Return endpoint functions in read-only mode for user display without mutating state.
- [ ] Payment initiation snapshots amount strictly from `bookings.total_amount`.
- [ ] Payment success invokes `bookingService.confirmPaidBooking()` across the domain boundary.
- [ ] Booking 1-N Payment schema cardinality is strictly preserved.
- [ ] Zero secrets are logged or exposed in API responses.
- [ ] Unit, concurrency, and controller tests achieve high coverage.
- [ ] `.\mvnw.cmd clean test` passes with 0 failures, 0 errors.

---

## 25. Open Decisions

All core V1 design decisions have been locked. The following items are explicitly tracked:

1. **Local Sandbox Webhook Testing**:
   - *Current state*: Local developer environments have private IP addresses.
   - *Recommended decision*: Support tunnel URL (`ngrok`) or mock IPN simulator for manual testing; rely on comprehensive automated unit/integration tests for automated CI/CD.
   - *Reason*: Standard practice for third-party webhook integrations.
2. **Payment Detail Endpoint Exposure**:
   - *Current state*: Payment summary is exposed in `BookingDetailResponse`.
   - *Recommended decision*: Provide `GET /api/v1/payments/{id}` for dedicated payment verification if requested by frontend.
   - *Reason*: Convenient for payment-specific receipt generation.

---

## 26. Future Extensions (V2+)

1. **Payment Retry Flow (V2)**: Creating a new payment attempt on an existing unpaid booking before hold expires (`Booking 1 ─── N Payment`).
2. **Automated VNPay Sandbox Refund API (V2)**: Administrative refund integration via VNPay refund endpoint.
3. **Additional Payment Gateways (V2)**: MoMo, ZaloPay, ViettelPay.
