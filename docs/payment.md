# Payment Integration Specification (VNPay Sandbox) — Payment V2 & Refund

## 1. Document Purpose

This document defines the technical integration mechanics between CineBook and the **VNPay payment gateway (Sandbox mode)**, as well as the complete Refund processing flow.

**Canonical Rule**:
- Business rules governing payment and booking state live in [`docs/business-rules.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/business-rules.md) §9 and [`docs/use-cases/payment.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/use-cases/payment.md).
- Detailed REST API contracts live in [`docs/api.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/api.md) §9.

---

## 2. Scope Boundaries

- **Gateway**: VNPay Sandbox only.
- **Backend Only**: Vue/frontend is out of scope.
- **Cardinality**: `Booking 1 ─── N Payment` (A booking can have multiple payment attempts; at most 1 in `PENDING` status at any time).
- **Security**: All hash computations, HMAC-SHA512 verification, and secret keys remain strictly server-side.

---

## 3. Environment & Configuration

- Mode: **Sandbox only** during development.
- Configuration Properties:
  - `vnpay.tmn-code`: Merchant terminal code (`${VNPAY_TMN_CODE:}`)
  - `vnpay.hash-secret`: HMAC-SHA512 secret key (`${VNPAY_HASH_SECRET:}`)
  - `vnpay.payment-url`: Sandbox payment URL (`${VNPAY_PAYMENT_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}`)
  - `vnpay.api-url`: Sandbox transaction/refund API URL (`${VNPAY_API_URL:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}`)
  - `vnpay.return-url`: Client return URL (`${VNPAY_RETURN_URL:http://localhost:5173/payment/result}`)
  - `cinebook.payment.gateway`: Gateway selector (`vnpay` for live sandbox, `mock` for deterministic unit testing)

**Security & Precedence Invariants**:
- VNPay Sandbox credentials (`tmn-code` & `hash-secret`) must be real credentials issued to the merchant account from `https://sandbox.vnpayment.vn/devreg/`.
- Public/template credentials (e.g. `2QXUI4J4`) must **never** be used at runtime.
- Local credentials must be supplied via environment variables (`VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`) or local secrets file (`application-local.yml`).
- `application-local.yml` is strictly `.gitignore`d and must **never** be committed to Git.
- Hash secrets are never logged, never returned in API responses, and never exposed in error messages.

### 3.1. Single Public Origin (ngrok) Development Architecture

For local development and live VNPay Sandbox integration, CineBook uses a **Single Public Origin Reverse Proxy** via Vite:

```text
                                  Internet
                                     │
                                     ▼
                  ┌─────────────────────────────────────┐
                  │                ngrok                │
                  │  https://<NGROK_DOMAIN>.ngrok-free.dev
                  └──────────────────┬──────────────────┘
                                     │ (Single Tunnel)
                                     ▼
                  ┌─────────────────────────────────────┐
                  │           Vite Dev Server           │
                  │             (Port 5173)             │
                  │  - allowedHosts: [.ngrok-free.dev]  │
                  │  - proxy: /api -> localhost:8080    │
                  └─────────┬─────────────────┬─────────┘
                            │                 │
              (Static assets / Vue routes)    │ (Reverse proxy /api/*)
                            │                 │
                            ▼                 ▼
                     [ Vue 3 App ]     [ Spring Boot Backend ]
                     (Browser UI)            (Port 8080)
                                              - /api/v1/bookings
                                              - /api/v1/payments
                                              - /api/v1/payments/vnpay/ipn
```

- **Unified Launcher (`dev.ps1`)**: Runs `.\dev.ps1` to orchestrate Spring Boot (`:8080`), Vite (`:5173`), and ngrok tunnel concurrently with health check polling.
- **Frontend Client (`api.ts`)**: Sets `BASE_URL = ''` (relative path) to execute same-origin requests through the Vite reverse proxy, avoiding Mixed Content / CORS errors.
- **VNPay Return URL**: `https://<NGROK_DOMAIN>/payment/result`
- **VNPay IPN Webhook**: `https://<NGROK_DOMAIN>/api/v1/payments/vnpay/ipn` (Forwarded to Spring Boot via Vite proxy).

---

## 4. Payment Lifecycle, Resume/Retry & Trust Boundary

```text
1. Customer initiates payment: POST /api/v1/bookings/{id}/payments
   - If no PENDING payment -> Creates Payment(PENDING), computes HMAC-SHA512 vnp_SecureHash, returns paymentUrl
   - If PENDING payment already exists and hold valid -> RESUMES existing Payment(PENDING), generates fresh paymentUrl
2. Customer completes transaction on VNPay Sandbox page
3. If Customer cancels or goes back -> Payment remains PENDING or is marked CANCELLED via IPN/Return
   - Customer can click "Thử lại thanh toán" -> POST /api/v1/bookings/{id}/payments (Resumes or creates new attempt)
4. Browser redirects to GET /api/v1/payments/vnpay/return (Read-only for UX)
5. VNPay calls authoritative webhook GET /api/v1/payments/vnpay/ipn
6. Backend verifies signature + amount:
   - Valid (00) -> Payment(SUCCESS), Booking(PAID), Ticket(VALID issued), SeatHold released
   - Cancelled (24) -> Payment(CANCELLED)
   - Other -> Payment(FAILED)
7. Response acknowledges IPN to VNPay
```

**Return vs IPN**: The IPN webhook is the single authoritative source of truth that mutates database state. The return endpoint is strictly read-only for frontend UX display.

**Payment Resume Invariant**:
- When `booking.status == PENDING_PAYMENT` and `holdExpiresAt > now`:
  - If an active `Payment(PENDING)` exists: Reuses the existing `Payment` record and returns a newly generated `paymentUrl` (HTTP 200 OK). Does NOT throw 409 Conflict.
  - If previous payment attempt was `FAILED` or `CANCELLED`: Creates a new `Payment(PENDING)` attempt and returns the payment URL.
  - If `holdExpiresAt <= now`: Marks booking `EXPIRED`, deletes seat holds, and returns 400 Bad Request.

---

## 5. Parameter Mapping, Encoding & Signature Construction (VNPay 2.1.0)

All parameters and keys are encoded using **UTF-8** (`StandardCharsets.UTF_8`). Key names are sorted alphabetically before building the query string and computing HMAC-SHA512 hash data.

| Parameter | Meaning | CineBook Source | Encoding Rule |
|---|---|---|---|
| `vnp_Version` | API Version | `2.1.0` | UTF-8 |
| `vnp_Command` | Command | `pay` | UTF-8 |
| `vnp_TmnCode` | Merchant Code | `cinebook.payment.vnpay.tmn-code` | UTF-8 |
| `vnp_Amount` | Amount $\times 100$ | `payment.amount * 100` (Integer in minor units) | UTF-8 |
| `vnp_CurrCode` | Currency | `VND` | UTF-8 |
| `vnp_TxnRef` | Unique Transaction Reference | `payment.paymentCode` | UTF-8 |
| `vnp_OrderInfo` | Order Description | `Thanh toan ve xem phim {bookingCode}` | UTF-8 URL Encoded |
| `vnp_OrderType` | Order Type | `other` | UTF-8 |
| `vnp_Locale` | Locale | `vn` | UTF-8 |
| `vnp_ReturnUrl` | Return URL | Configured return URL | UTF-8 URL Encoded |
| `vnp_IpAddr` | Client IP Address | Sanitized IPv4 (`127.0.0.1` for localhost/IPv6) | UTF-8 |
| `vnp_CreateDate` | Timestamp | `yyyyMMddHHmmss` | UTF-8 |
| `vnp_ExpireDate` | Expiration Timestamp | `yyyyMMddHHmmss` (`booking.holdExpiresAt`) | UTF-8 |
| `vnp_SecureHash` | HMAC-SHA512 Signature | Computed over sorted `encodedKey=encodedValue` pairs joined with `&` using `hash-secret` | Hex lower-case |

---

## 6. Refund Processing (Payment V2)

### 6.1 Customer Refund (`POST /api/v1/payments/{paymentId}/refund`)
- Authorized for booking owner (`CUSTOMER`).
- Valid only for `PaymentStatus.SUCCESS` and `BookingStatus.PAID`.
- Requires current time $\ge 2$ hours before showtime `startTime`.
- Full refund amount matching original payment amount.

### 6.2 Admin Refund (`POST /api/v1/admin/bookings/{bookingId}/refund`)
- Authorized for `ADMIN`.
- Allows refunding any paid booking or orphaned successful payment without the 2-hour window restriction.

### 6.3 State Transitions upon Refund Success:
- `Refund.refundStatus = SUCCESS`
- `Payment.paymentStatus = REFUNDED`
- `Booking.bookingStatus = REFUNDED`
- `Ticket.ticketStatus = CANCELLED`
- Held/reserved seats become immediately available for new reservations.
- Promotion quota (`usedCount`) is not incremented or refunded; discount snapshot remains immutable.

---

## 7. Idempotency & Concurrency

- **Pessimistic Locking**: `PaymentServiceImpl` uses pessimistic write locks (`SELECT ... FOR UPDATE`) on `payments` and `bookings` during IPN confirmation and refund execution to eliminate race conditions.
- **Idempotent Webhooks**: Repeated IPN calls for an already `SUCCESS` or `FAILED` payment return `RspCode: 02` (Order already confirmed) without re-processing.
