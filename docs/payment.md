# CineBook Payment Integration (VNPay Sandbox)

## 1. Purpose

This document describes **how CineBook integrates with VNPay Sandbox** for payment processing.

It complements:

- `business-rules.md` §9 — defines *when* a booking may become `PAID`.
- `api.md` §9 — defines the public endpoint contract exposed to the frontend.
- `database.md` §3.7 — defines the `payments` / `refunds` table structure.

This file focuses on **mechanics**: request construction, secure hash, return/IPN handling, and status mapping — not on business policy.

---

## 2. Scope Boundary (avoid duplication)

| Concern | Lives in |
|---|---|
| Booking → `PAID` transition rule | `business-rules.md` §9 |
| Public endpoint contract (`/api/v1/bookings/{id}/payments`, `/payments/{id}`) | `api.md` §9 |
| `payments` / `refunds` table structure | `database.md` §3.7 |
| VNPay request/response field mapping, signature | **this file** |

Do not restate business rules here; reference them instead.

---

## 3. Environment

- Mode: **Sandbox only** during development. Never point to VNPay production endpoints/credentials without explicit instruction (`AGENTS.md` §16, §29).
- Credentials (`vnp_TmnCode`, `vnp_HashSecret`) are stored server-side via environment variables / secure configuration — never committed, never sent to the frontend.
- Sandbox host is provided by VNPay for the merchant test account used in this project.

```text
TODO / DECISION REQUIRED:
- Confirm the exact sandbox payment URL configured in application properties.
- Confirm vnp_TmnCode / vnp_HashSecret are provisioned and stored outside source control.
```

---

## 4. High-Level Flow

```text
1. Frontend: user confirms booking → calls POST /api/v1/bookings/{id}/payments
2. Backend: builds VNPay payment request, computes vnp_SecureHash, returns paymentUrl
3. Frontend: redirects browser to paymentUrl (VNPay hosted page)
4. User pays on the VNPay sandbox page
5. VNPay redirects browser → GET/POST /api/v1/payments/vnpay/return   (user-facing result)
6. VNPay calls backend asynchronously → GET/POST /api/v1/payments/vnpay/ipn  (source of truth)
7. Backend verifies signature + amount on IPN → updates payments + bookings atomically
8. Backend responds to VNPay IPN with the required acknowledgement code
```

**`return` is for UX only. `ipn` is the authoritative confirmation.** The return endpoint must never mark a booking `PAID` by itself unless the project explicitly decides otherwise (see §9).

---

## 5. Creating a Payment Request

### 5.1 Standard VNPay parameters

| Parameter | Meaning | CineBook source |
|---|---|---|
| `vnp_Version` | API version | fixed |
| `vnp_Command` | `pay` | fixed |
| `vnp_TmnCode` | Merchant code | server config |
| `vnp_Amount` | Amount × 100 (VNPay requires an integer, no decimals) | derived from `payments.amount` / `bookings.total_amount` |
| `vnp_CurrCode` | `VND` | fixed |
| `vnp_TxnRef` | Unique transaction reference | `payments.payment_code` |
| `vnp_OrderInfo` | Order description | e.g. booking code |
| `vnp_OrderType` | Order category | fixed/agreed value |
| `vnp_Locale` | `vn` or `en` | request/user locale |
| `vnp_ReturnUrl` | Return URL | configured backend/frontend return endpoint |
| `vnp_IpAddr` | Client IP | request IP |
| `vnp_CreateDate` | `yyyyMMddHHmmss` | server time |
| `vnp_SecureHash` | HMAC signature over sorted params | computed with `vnp_HashSecret` |

`vnp_TxnRef` must be unique per attempt — reuse `payments.payment_code` (already unique via `uk_payments_code`).

### 5.2 Amount handling

VNPay expects an integer amount × 100 (no decimals). CineBook stores `amount` as `decimal(12,2)` VND — conversion must not lose precision and must match `bookings.total_amount` exactly for that payment attempt.

```text
TODO / DECISION REQUIRED:
- Whether a booking may have more than one payment attempt (retry after failure) before success.
  (see business-rules.md §9.2, database.md §8 Open Decisions)
```

---

## 6. Secure Hash / Signature

- All request parameters (except `vnp_SecureHash` / `vnp_SecureHashType`) are sorted alphabetically by key, URL-encoded, concatenated, and hashed with `HMACSHA512` using `vnp_HashSecret`.
- The same procedure verifies incoming `return` and `ipn` callbacks: recompute the hash from the received parameters and compare with the `vnp_SecureHash` VNPay sent.
- If signature verification fails → treat as invalid callback, **do not change any state**, log the event (without logging the secret itself).

---

## 7. Return Endpoint (`/api/v1/payments/vnpay/return`)

- Public (no JWT — protected by signature verification instead).
- Purpose: give the user immediate feedback and redirect them to a friendly frontend result page.
- Steps:
  1. Verify `vnp_SecureHash`.
  2. Look up the payment by `vnp_TxnRef` (= `payment_code`).
  3. Read `vnp_ResponseCode` / `vnp_TransactionStatus` for **display purposes only**.
  4. Do not treat this endpoint as the final authority for state change unless explicitly decided otherwise (§9).

---

## 8. IPN Endpoint (`/api/v1/payments/vnpay/ipn`)

- Public (protected by signature verification).
- Purpose: authoritative server-to-server confirmation.
- Steps:
  1. Verify `vnp_SecureHash`. If invalid → respond with VNPay's defined "invalid signature" code, do not change state.
  2. Look up the payment by `vnp_TxnRef`.
  3. Verify `vnp_Amount` matches the stored `payments.amount` (converted back from ×100). Mismatch → reject, do not update state.
  4. If already processed (idempotency: payment already `SUCCESS`/`FAILED`), respond with VNPay's "already confirmed" code without reprocessing.
  5. On valid + matching + first-time confirmation, in one transaction:
     - Update `payments.payment_status`, `paid_at`, `gateway_transaction_id`, `gateway_response`.
     - Update `bookings.booking_status` to `PAID` (or a failed state) consistently — see `business-rules.md` §9.2.
  6. Respond to VNPay with the required acknowledgement JSON (`RspCode`, `Message`) per VNPay's IPN contract.

**Idempotency is critical** — VNPay may call IPN more than once for the same transaction.

```text
TODO / DECISION REQUIRED:
- Exact set of vnp_ResponseCode / vnp_TransactionStatus values CineBook treats as SUCCESS vs FAILED.
- Timeout/abandoned payment handling: does an unconfirmed HOLD/PENDING_PAYMENT booking expire
  independently of seat_holds.expires_at? (see business-rules.md §9.2 TODO)
```

---

## 9. Return vs IPN — Trust Boundary

```text
TODO / DECISION REQUIRED — confirm final policy:

Option A (recommended, standard practice):
  IPN is the only endpoint allowed to change payment/booking state.
  The return endpoint is read-only / redirect-only.

Option B:
  The return endpoint may also confirm state if IPN is unreachable in the dev
  sandbox environment (common when the backend has no public IP for VNPay to call).

This must be decided explicitly — do not silently let the return endpoint mutate state.
```

---

## 10. Payment Status Mapping

`payments.payment_status` is an application-level enum (`varchar`). Do not invent values not already established in code.

```text
TODO / DECISION REQUIRED (also listed in business-rules.md §9.2):
- Exact payment_status values and transitions (e.g. PENDING → SUCCESS / FAILED / CANCELLED).
```

---

## 11. Refunds

- At most one refund per payment (`uk_refunds_payment` — see `database.md` §3.7).
- `refunds.refund_status` is an application-level enum — do not invent values.
- If VNPay's sandbox refund API is used, it follows the same signature-verification pattern as §6.

```text
TODO / DECISION REQUIRED:
- Whether refund is triggered manually by admin, automatically on cancellation, or is out of
  scope for this graduation project.
- Partial refund support (see business-rules.md §9.2, database.md §8).
```

---

## 12. Error Handling

- Any VNPay call failure (network, invalid signature, amount mismatch) must result in a clear 4xx/409 response to the frontend per `api.md` §3.2/§3.3 — never a silent success.
- Never expose `vnp_HashSecret` or raw gateway credentials in logs or responses. Store `gateway_response` as returned by VNPay, but never log the values used to compute the hash.

---

## 13. Testing

Use VNPay-provided sandbox test cards/accounts for manual and automated testing.

Risk-based priority (per `AGENTS.md` §24):

- Successful payment → booking becomes `PAID`.
- Failed payment → booking stays not-`PAID`.
- Duplicate IPN call → idempotent, no double processing.
- Invalid signature → rejected, no state change.
- Amount mismatch → rejected, no state change.

---

## 14. Related Documents

| Document | Concern |
|---|---|
| `business-rules.md` §9 | When a booking may become `PAID` |
| `api.md` §9 | Public payment endpoints |
| `database.md` §3.7 | `payments` / `refunds` table structure |
| `architecture.md` §8 | External integration overview |

---

*This document describes the VNPay sandbox integration mechanics. Business rules governing payment/booking state remain in `business-rules.md` — do not duplicate them here.*
