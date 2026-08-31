# Canonical Specification: Administration & Platform Operations

## 1. Overview & Scope

The **Administration & Platform Operations** domain encompasses all administrative endpoints, business controls, management interfaces, and operational oversight tools restricted to users with `ROLE_ADMIN`.

Administrative functionality in CineBook is partitioned into focused, cohesive domain modules with strict Layered Architecture and RBAC enforcement.

---

## 2. Administrative Sub-Domains & Canonical References

| Administrative Domain | Canonical Specification | Key Endpoints |
|---|---|---|
| **Movie & TMDB Management** | [`docs/use-cases/movie.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/use-cases/movie.md)<br>[`docs/tmdb-import.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/tmdb-import.md) | `POST /api/v1/admin/movies/import/{tmdbId}`<br>`PUT /api/v1/admin/movies/{id}`<br>`DELETE /api/v1/admin/movies/{id}` |
| **Cinema, Auditorium & Seat Matrix** | [`docs/use-cases/cinema.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/use-cases/cinema.md) | `POST /api/v1/admin/cinemas`<br>`POST /api/v1/admin/auditoriums`<br>`POST /api/v1/admin/auditoriums/{id}/seats/matrix`<br>`PATCH /api/v1/admin/auditoriums/{id}/seats/{seatId}` |
| **Showtime Scheduling & Conflicts** | [`docs/use-cases/showtime.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/use-cases/showtime.md) | `POST /api/v1/admin/showtimes`<br>`PUT /api/v1/admin/showtimes/{id}`<br>`DELETE /api/v1/admin/showtimes/{id}` |
| **Promotion & Voucher Management** | [`docs/use-cases/promotion.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/use-cases/promotion.md) | `POST /api/v1/admin/promotions`<br>`PUT /api/v1/admin/promotions/{id}`<br>`PATCH /api/v1/admin/promotions/{id}/status`<br>`DELETE /api/v1/admin/promotions/{id}` |
| **Payment Reconciliation & Admin Refund** | [`docs/use-cases/payment.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/use-cases/payment.md) | `POST /api/v1/admin/bookings/{bookingId}/refund`<br>`GET /api/v1/admin/refunds`<br>`GET /api/v1/admin/payments/{id}` |
| **Reporting & Executive Dashboard** | [`docs/use-cases/reporting.md`](file:///d:/HOCTAP/DoAn/CineBook/docs/use-cases/reporting.md) | `GET /api/v1/admin/reports/dashboard`<br>`GET /api/v1/admin/reports/revenue`<br>`GET /api/v1/admin/reports/movies`<br>`GET /api/v1/admin/reports/cinemas`<br>`GET /api/v1/admin/reports/showtimes/occupancy`<br>`GET /api/v1/admin/reports/export` |

---

## 3. Global Administrative Invariants

1. **Strict RBAC Enforcement**:
   - All endpoints mapped under `/api/v1/admin/**` require `hasRole('ADMIN')`.
   - Anonymous requests yield `401 Unauthorized`.
   - Customer token requests yield `403 Forbidden`.
2. **Audit Logging & Financial Safety**:
   - Critical administrative actions (such as overriding refund restrictions, status transitions, soft deletes) are audit-logged.
   - Zero hard deletes on transactional entities (`payments`, `refunds`, `bookings`, `tickets`).
3. **Data Protection**:
   - Administrative responses never expose raw customer password hashes, reset tokens, or third-party secret keys.

