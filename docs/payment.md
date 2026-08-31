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
- Properties:
  - `cinebook.payment.vnpay.tmn-code`: Merchant terminal code (`${VNPAY_TMN_CODE:default}`)
  - `cinebook.payment.vnpay.hash-secret`: HMAC-SHA512 secret key (`${VNPAY_HASH_SECRET:default}`)
  - `cinebook.payment.vnpay.payment-url`: Sandbox payment URL (`https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`)
  - `cinebook.payment.vnpay.refund-url`: Sandbox refund URL (`https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`)
  - `cinebook.payment.vnpay.return-url`: Client return URL (`http://localhost:5173/payment/vnpay/return`)
  - `cinebook.payment.gateway`: Gateway selector (`vnpay` for production/sandbox, `mock` for deterministic testing)

---

## 4. Payment Lifecycle & Trust Boundary

```text
1. Customer initiates payment: POST /api/v1/bookings/{id}/payments
2. Backend creates Payment(PENDING), computes HMAC-SHA512 vnp_SecureHash, returns paymentUrl
3. Customer completes transaction on VNPay Sandbox page
4. Browser redirects to GET /api/v1/payments/vnpay/return (Read-only for UX)
5. VNPay calls authoritative webhook GET /api/v1/payments/vnpay/ipn
6. Backend verifies signature + amount:
   - Valid -> Payment(SUCCESS), Booking(PAID), Ticket(VALID issued)
   - Invalid -> Reject with VNPay error response
7. Response acknowledges IPN to VNPay
```

**Return vs IPN**: The IPN webhook is the single authoritative source of truth that mutates database state. The return endpoint is strictly read-only for frontend UX display.

---

## 5. Parameter Mapping & Amount Integrity

| Parameter | Meaning | CineBook Source |
|---|---|---|
| `vnp_Version` | API Version | `2.1.0` |
| `vnp_Command` | Command | `pay` |
| `vnp_TmnCode` | Merchant Code | `cinebook.payment.vnpay.tmn-code` |
| `vnp_Amount` | Amount $\times 100$ | `payment.amount * 100` (Integer in minor units) |
| `vnp_CurrCode` | Currency | `VND` |
| `vnp_TxnRef` | Unique Transaction Reference | `payment.paymentCode` |
| `vnp_OrderInfo` | Order Description | `Thanh toan don dat ve {bookingCode}` |
| `vnp_OrderType` | Order Type | `other` |
| `vnp_Locale` | Locale | `vn` |
| `vnp_ReturnUrl` | Return URL | Configured return URL |
| `vnp_IpAddr` | Client IP Address | Request remote IP |
| `vnp_CreateDate` | Timestamp | `yyyyMMddHHmmss` |
| `vnp_SecureHash` | HMAC-SHA512 Signature | Computed with `hash-secret` |

---

## 6. Refund Processing (Payment V2)

### 6.1 Customer Refund (`POST /api/v1/payments/{paymentId}/refund`)
- Authorized for booking owner (`ROLE_CUSTOMER`).
- Valid only for `PaymentStatus.SUCCESS` and `BookingStatus.PAID`.
- Requires current time $\ge 2$ hours before showtime `startTime`.
- Full refund amount matching original payment amount.

### 6.2 Admin Refund (`POST /api/v1/admin/bookings/{bookingId}/refund`)
- Authorized for `ROLE_ADMIN`.
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
