# CineBook API Contract

## 1. Overview

- **Style**: RESTful JSON API
- **Base path**: `/api/v1`
- **Format**: `application/json`
- **Charset**: UTF-8
- **Authentication**: Bearer JWT (except public endpoints)
- **API documentation target**: OpenAPI 3 (Swagger) — to be generated from code later

This document is the **contract** between backend and frontend.  
Do not silently break existing endpoints once they are implemented and consumed.

```text
TODO / DECISION REQUIRED:
- Exact date-time format (recommend ISO-8601 UTC: 2026-08-24T14:30:00Z)
- Pagination defaults (page size, max page size)
- Error response envelope (see section 3 — confirm final shape)
- Whether HATEOAS links are required (currently: not required)
```

---

## 2. Versioning

- Current version: **v1**
- Version is part of the URL: `/api/v1/...`
- Breaking changes require a new version (`/api/v2/...`) or explicit migration plan.
- Non-breaking additions (new optional fields, new endpoints) may stay in v1.

---

## 3. Common Conventions

### 3.1 HTTP Methods

| Method   | Usage                          |
|----------|--------------------------------|
| GET      | Read / list                    |
| POST     | Create / action                |
| PUT      | Full update (or replace)       |
| PATCH    | Partial update (when needed)   |
| DELETE   | Delete / soft-delete / cancel  |

### 3.2 Status Codes

| Code | Meaning                                      |
|------|----------------------------------------------|
| 200  | OK                                           |
| 201  | Created                                      |
| 204  | No Content                                   |
| 400  | Bad Request (validation / business rule)     |
| 401  | Unauthorized (missing/invalid token)         |
| 403  | Forbidden (insufficient role)                |
| 404  | Not Found                                    |
| 409  | Conflict (e.g. seat already held)            |
| 422  | Unprocessable Entity (semantic errors)       |
| 500  | Internal Server Error                        |

### 3.3 Error Response (recommended shape)

```json
{
  "timestamp": "2026-08-24T14:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Human readable summary",
  "path": "/api/v1/bookings",
  "details": [
    {
      "field": "seatIds",
      "message": "One or more seats are no longer available"
    }
  ]
}
```

```text
TODO / DECISION REQUIRED:
Confirm final error envelope with existing exception handler (if any).
```

### 3.4 Authentication Header

```http
Authorization: Bearer <access_token>
```

### 3.5 Pagination (list endpoints)

Query parameters (recommended):

| Param    | Type    | Default | Description        |
|----------|---------|---------|--------------------|
| page     | int     | 0       | Zero-based page    |
| size     | int     | 20      | Page size          |
| sort     | string  | —       | e.g. `startTime,asc` |

Response envelope (recommended):

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 120,
  "totalPages": 6
}
```

### 3.6 IDs

- Resource IDs are UUID strings (`varchar(36)`), except internal technical IDs if any.
- Clients must treat IDs as opaque strings.

---

## 4. Authentication

### 4.1 Register

```http
POST /api/v1/auth/register
```

**Auth**: Public

**Request**:
```json
{
  "email": "user@example.com",
  "password": "string",
  "fullName": "Nguyen Van A",
  "phone": "0901234567"          // optional
}
```

**Response**: `201 Created`  
Body: user summary + tokens (`accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `user`). Email verification is not required for registration.

**Possible errors**: 400 (validation), 409 (email/phone already exists)

---

### 4.2 Login

```http
POST /api/v1/auth/login
```

**Auth**: Public

**Request**:
```json
{
  "email": "user@example.com",
  "password": "string"
}
```

**Response**: `200 OK`
```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900,              // seconds (15 minutes)
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "fullName": "Nguyen Van A",
    "roles": ["CUSTOMER"]
  }
}
```

---

### 4.3 Refresh Token

```http
POST /api/v1/auth/refresh
```

**Auth**: Public (body contains refresh token)

**Request**:
```json
{
  "refreshToken": "..."
}
```

**Response**: `200 OK` — new access token and rotated refresh token (old refresh token is revoked)

---

### 4.4 Logout / Revoke

```http
POST /api/v1/auth/logout
```

**Auth**: Required

**Request** (optional body with refresh token to revoke specifically)

**Response**: `204 No Content`

---

### 4.5 Password Reset (request + confirm)

```http
POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/confirm
```

**Auth**: Public

Details of token delivery (email) and exact payload are implementation concerns; keep tokens hashed server-side.

**Token Lifetimes (Finalized)**:
- Access Token: 15 minutes (900 seconds)
- Refresh Token: 7 days (604800 seconds)
- Password Reset Token: 15 minutes (900 seconds)

**Registration Behavior**:
- Returns tokens immediately (no email verification required before login).

---

## 5. Movies (Public + Admin)

### 5.1 List movies

```http
GET /api/v1/movies
```

**Auth**: Public

**Query** (examples): `status`, `genre`, `q` (search), `page`, `size`, `sort`

**Response**: Paginated list of movie summaries (id, title, poster, duration, ageRating, status, …)

---

### 5.2 Get movie detail

```http
GET /api/v1/movies/{id}
```

**Auth**: Public

**Response**: Full movie + genres

---

### 5.3 Admin – Create / Update / Soft-delete

```http
POST   /api/v1/admin/movies
PUT    /api/v1/admin/movies/{id}
DELETE /api/v1/admin/movies/{id}
```

**Auth**: Required — Admin role

Soft-delete preferred over hard delete.

---

## 6. Cinemas, Auditoriums, Seats

### 6.1 List cinemas

```http
GET /api/v1/cinemas
```

**Auth**: Public  
**Query**: `city`, `status`, pagination

### 6.2 Cinema detail

```http
GET /api/v1/cinemas/{id}
```

### 6.3 Auditoriums of a cinema

```http
GET /api/v1/cinemas/{id}/auditoriums
```

### 6.4 Seat map of an auditorium (or of a showtime)

```http
GET /api/v1/showtimes/{showtimeId}/seats
```

**Response**: seats with status for that showtime (AVAILABLE / HELD / SOLD / BLOCKED, …)

**Auth**: Public (or authenticated — decide)

Admin CRUD for cinemas / auditoriums / seat-types under `/api/v1/admin/...`

---

## 7. Showtimes

### 7.1 List showtimes

```http
GET /api/v1/showtimes
```

**Auth**: Public  
**Query**: `movieId`, `cinemaId`, `date`, `status`, pagination

### 7.2 Showtime detail

```http
GET /api/v1/showtimes/{id}
```

Admin create/update/cancel under `/api/v1/admin/showtimes`

---

## 8. Booking (Core)

### 8.1 Create hold / start booking

```http
POST /api/v1/bookings
```

**Auth**: Required

**Request** (example shape):
```json
{
  "showtimeId": "uuid",
  "seatIds": ["uuid1", "uuid2"]
}
```

**Response**: `201 Created`
```json
{
  "id": "uuid",
  "bookingCode": "CB-...",
  "status": "HOLD",
  "holdExpiresAt": "2026-08-24T14:40:00Z",
  "totalAmount": 180000,
  "seats": [ ... ],
  "showtime": { ... }
}
```

**Possible errors**:
- 409 Conflict — one or more seats no longer available
- 400 — invalid showtime / seats

**Rules**:
- Backend must re-check availability inside a transaction.
- Creates `seat_holds` + `bookings` record.

---

### 8.2 Get my booking

```http
GET /api/v1/bookings/{id}
```

**Auth**: Required (owner or admin)

### 8.3 List my bookings

```http
GET /api/v1/bookings/me
```

**Auth**: Required

### 8.4 Cancel booking

```http
POST /api/v1/bookings/{id}/cancel
```

**Auth**: Required (owner or admin)

```text
TODO / DECISION REQUIRED:
- Cancellation window and conditions
- Whether this endpoint also triggers refund flow
```

---

## 9. Payment

### 9.1 Create payment (initiate VNPay)

```http
POST /api/v1/bookings/{bookingId}/payments
```

**Auth**: Required (owner)

**Request** (example):
```json
{
  "paymentMethod": "VNPAY"
}
```

**Response**: `200 OK`
```json
{
  "paymentId": "uuid",
  "paymentCode": "...",
  "amount": 180000,
  "paymentUrl": "https://sandbox.vnpayment.vn/...",
  "expiresAt": "..."
}
```

Frontend redirects user to `paymentUrl`.

### 9.2 VNPay Return / IPN (callback)

```http
GET|POST /api/v1/payments/vnpay/return
GET|POST /api/v1/payments/vnpay/ipn
```

**Auth**: Public (signature verification instead)

- Backend verifies signature and amount.
- Updates `payments` + `bookings` status atomically on success.
- Never trust a frontend “success” flag alone.

Detailed field mapping → `docs/payment.md`.

### 9.3 Get payment status

```http
GET /api/v1/payments/{id}
```

**Auth**: Required (owner or admin)

---

## 10. Promotions

```http
GET  /api/v1/promotions/validate?code=XXX&bookingId=...
POST /api/v1/bookings/{id}/promotions          // apply
```

Admin CRUD under `/api/v1/admin/promotions`

```text
TODO / DECISION REQUIRED:
- Stacking rules
- Exact discount calculation
```

---

## 11. Admin Endpoints (summary)

All under `/api/v1/admin/...` and require Admin (or appropriate) role.

| Area        | Examples                                      |
|-------------|-----------------------------------------------|
| Movies      | CRUD + soft-delete                            |
| Genres      | CRUD                                          |
| Cinemas     | CRUD                                          |
| Auditoriums | CRUD                                          |
| Seat types  | CRUD                                          |
| Seats       | bulk generate / update status                 |
| Showtimes   | CRUD / cancel                                 |
| Pricing     | day rules, time-slot rules                    |
| Promotions  | CRUD                                          |
| Users       | list, lock/unlock, assign roles               |
| Reports     | (future) bookings, revenue                    |

Exact paths should follow the same resource naming as public API where possible.

---

## 12. User Profile

```http
GET   /api/v1/users/me
PUT   /api/v1/users/me
PATCH /api/v1/users/me/password
```

**Auth**: Required

---

## 13. Health / Meta (optional)

```http
GET /api/v1/health
GET /api/v1/version
```

Useful for deployment checks; not part of core business.

---

## 14. OpenAPI / Swagger

**Target**:
- SpringDoc OpenAPI 3 (or equivalent already in the project)
- UI available at `/swagger-ui.html` or `/swagger-ui/index.html` (confirm path)
- Spec at `/v3/api-docs`

Rules for the AI:
- Keep controller annotations and DTOs in sync with this contract.
- When an endpoint is implemented, its OpenAPI description should match this document.
- Do not expose internal entities directly; use DTOs.
- Do not document secrets or internal admin-only debug endpoints publicly.

```text
TODO / DECISION REQUIRED:
- Exact SpringDoc / springfox dependency already present in the project
- Whether admin endpoints are hidden from public Swagger or protected by auth in UI
```

---

## 15. Endpoint Summary (v1)

| Method | Path                                      | Auth     | Purpose                    |
|--------|-------------------------------------------|----------|----------------------------|
| POST   | /api/v1/auth/register                     | Public   | Register                   |
| POST   | /api/v1/auth/login                        | Public   | Login                      |
| POST   | /api/v1/auth/refresh                      | Public   | Refresh token              |
| POST   | /api/v1/auth/logout                       | Required | Logout / revoke            |
| POST   | /api/v1/auth/password-reset/request       | Public   | Request reset              |
| POST   | /api/v1/auth/password-reset/confirm       | Public   | Confirm reset              |
| GET    | /api/v1/movies                            | Public   | List movies                |
| GET    | /api/v1/movies/{id}                       | Public   | Movie detail               |
| GET    | /api/v1/cinemas                           | Public   | List cinemas               |
| GET    | /api/v1/cinemas/{id}                      | Public   | Cinema detail              |
| GET    | /api/v1/showtimes                         | Public   | List showtimes             |
| GET    | /api/v1/showtimes/{id}                    | Public   | Showtime detail            |
| GET    | /api/v1/showtimes/{id}/seats              | Public*  | Seat map for showtime      |
| POST   | /api/v1/bookings                          | Required | Create hold / booking      |
| GET    | /api/v1/bookings/me                       | Required | My bookings                |
| GET    | /api/v1/bookings/{id}                     | Required | Booking detail             |
| POST   | /api/v1/bookings/{id}/cancel              | Required | Cancel booking             |
| POST   | /api/v1/bookings/{id}/payments            | Required | Initiate payment           |
| GET/POST | /api/v1/payments/vnpay/return           | Public** | VNPay return               |
| GET/POST | /api/v1/payments/vnpay/ipn              | Public** | VNPay IPN                  |
| GET    | /api/v1/users/me                          | Required | Current user profile       |
| …      | /api/v1/admin/**                          | Admin    | Management endpoints       |

\* May require auth depending on final decision.  
\*\* Protected by VNPay signature, not JWT.

---

## 16. Design Principles for API Changes

1. Inspect existing controllers/DTOs before adding or changing an endpoint.
2. Prefer consistency with already implemented endpoints over theoretical perfection.
3. Never break a contract that the frontend already consumes without coordination.
4. New fields should be optional whenever possible (backward compatible).
5. Business rule violations → 4xx with clear message, not 500.
6. Seat/booking conflicts → prefer **409 Conflict**.

---

## 17. Related Documents

| Document                | Concern                          |
|-------------------------|----------------------------------|
| `architecture.md`       | Layers, request flow             |
| `database.md`           | Data model behind the API        |
| `business-rules.md`     | Rules the API must enforce       |
| `payment.md`            | VNPay field mapping & callbacks  |
| `tmdb-import.md`        | TMDB import workflow & mapping   |
| `use-cases/*.md`        | End-to-end scenarios             |

---

*This is the living API contract for CineBook v1.  
When an endpoint is implemented or its shape is finalized, update this file so it remains the single source of truth for frontend and backend.*

---

## 18. Admin TMDB Import API

Admin-only endpoints for synchronizing data from TMDB into CineBook.

All endpoints require `ROLE_ADMIN`. Covered by `/api/v1/admin/**` rule in `SecurityConfig`.

---

### 18.1 Sync Genres from TMDB

`POST /api/v1/admin/tmdb/genres/sync`

Fetches the official TMDB movie genre list and synchronizes with CineBook `genres` table.

- Creates new genres if TMDB genre ID is not found in CineBook.
- Updates genre name if it has changed on TMDB.
- Does **not** delete existing CineBook genres that are not in TMDB.
- Safe to re-run (idempotent).

**Response `200 OK`:**
```json
{
  "created": 5,
  "updated": 2,
  "unchanged": 11,
  "total": 18
}
```

**Error responses:**
| Status | Cause |
|--------|-------|
| 401 | No/invalid JWT |
| 403 | Not an ADMIN |
| 503 | TMDB API key invalid or TMDB unreachable |

---

### 18.2 Import Movie from TMDB

`POST /api/v1/admin/tmdb/movies/{tmdbId}/import`

Imports or updates a movie by its TMDB movie ID.

- If no movie with that `tmdbId` exists → creates a new Movie (fresh UUID, status derived from release date).
- If a movie with that `tmdbId` exists → updates TMDB-sourced fields only; **preserves** `status`, `deletedAt`, `createdAt`, `id`.
- Idempotent: safe to call multiple times.

**Path parameter:** `tmdbId` — the TMDB movie ID (e.g., `550` for Fight Club).

**Response `200 OK`:**
```json
{
  "movieId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "tmdbId": 550,
  "title": "Fight Club",
  "originalTitle": "Fight Club",
  "action": "CREATED",
  "status": "NOW_SHOWING",
  "releaseDate": "1999-10-15",
  "ageRating": "R",
  "genres": ["Drama", "Thriller"],
  "posterUrl": "https://image.tmdb.org/t/p/w500/poster.jpg",
  "trailerUrl": "https://www.youtube.com/watch?v=SUXWAEX2jlg"
}
```

`action` is `"CREATED"` or `"UPDATED"`.

**Error responses:**
| Status | Cause |
|--------|-------|
| 400 | Movie data missing required fields (title, overview, release_date) |
| 401 | No/invalid JWT |
| 403 | Not an ADMIN |
| 404 | Movie does not exist on TMDB |
| 503 | TMDB API key invalid or TMDB unreachable |

---

### 18.3 Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `TMDB_API_KEY` | **Yes** | (empty) | TMDB API Read Access Token (Bearer) |
| `TMDB_BASE_URL` | No | `https://api.themoviedb.org/3` | TMDB API base URL |
| `TMDB_IMAGE_BASE_URL` | No | `https://image.tmdb.org/t/p` | TMDB image CDN base URL |
| `TMDB_LANGUAGE` | No | `en-US` | Default language for API calls |
| `TMDB_POSTER_SIZE` | No | `w500` | TMDB image size for poster URLs |
| `TMDB_BACKDROP_SIZE` | No | `original` | TMDB image size for backdrop URLs |
| `TMDB_CONNECT_TIMEOUT` | No | `5000` | HTTP connect timeout (ms) |
| `TMDB_READ_TIMEOUT` | No | `10000` | HTTP read timeout (ms) |

> ⚠️ `TMDB_API_KEY` must **never** be committed to source control. Set via environment variable or CI/CD secret.
