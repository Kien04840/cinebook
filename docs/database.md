# CineBook Database

## 1. Overview

- **DBMS**: MySQL 8
- **Database name**: `cinebook`
- **Charset**: `utf8mb4` / `utf8mb4_0900_ai_ci`
- **Primary key style**: UUID (`varchar(36)`) for almost all tables; `seat_holds.id` is the only auto-increment bigint
- **Optimistic locking**: `version` column (bigint) present on mutable aggregate roots
- **Soft delete**: `deleted_at` present on major entities (users, movies, cinemas, auditoriums)
- **Source of truth**: MySQL. Redis (if used) is cache only.

The existing schema is **authoritative**. Do not rename tables/columns, drop constraints, or change relationships without explicit approval.

---

## 2. Domain Groups

```text
Identity
├── users
├── roles
├── user_roles
├── refresh_tokens
└── password_reset_tokens

Movie
├── movies
├── genres
└── movies_genres

Cinema
├── cinemas
├── auditoriums
├── seat_types
└── seats

Showtime
└── showtimes

Pricing
├── day_pricing_rules
└── time_slot_pricing_rules

Booking
├── seat_holds
├── bookings
├── tickets
└── booking_promotions

Payment
├── payments
└── refunds

Promotion
└── promotions

Food / Concessions
├── food_items
└── booking_foods

Notification
└── notifications
```

---

## 3. Tables by Domain

### 3.1 Identity

#### `users`
| Column            | Type             | Notes                          |
|-------------------|------------------|--------------------------------|
| id                | varchar(36) PK   | UUID                           |
| email             | varchar(255)     | UNIQUE, NOT NULL               |
| password_hash     | varchar(255)     | NOT NULL                       |
| full_name         | varchar(100)     | NOT NULL                       |
| phone             | varchar(20)      | UNIQUE (nullable)              |
| avatar_url        | varchar(500)     |                                |
| status            | varchar(20)      | NOT NULL                       |
| email_verified    | tinyint(1)       | NOT NULL                       |
| last_login_at     | datetime         |                                |
| created_at        | datetime         | NOT NULL                       |
| updated_at        | datetime         | NOT NULL                       |
| deleted_at        | datetime         | soft delete                    |
| version           | bigint           | optimistic lock, default 0     |

**Indexes**: `uk_users_email`, `uk_users_phone`, `idx_users_full_name`, `idx_users_status`

#### `roles`
| Column      | Type           | Notes            |
|-------------|----------------|------------------|
| id          | varchar(36) PK |                  |
| name        | varchar(50)    | UNIQUE, NOT NULL |
| description | varchar(500)   |                  |
| created_at  | datetime       | NOT NULL         |

#### `user_roles` (M:N)
| Column  | Type           | Notes |
|---------|----------------|-------|
| user_id | varchar(36) PK | FK → users |
| role_id | varchar(36) PK | FK → roles |

#### `refresh_tokens`
| Column      | Type             | Notes                |
|-------------|------------------|----------------------|
| id          | varchar(36) PK   |                      |
| user_id     | varchar(36)      | FK → users, NOT NULL |
| token_hash  | varchar(255)     | UNIQUE, NOT NULL     |
| expires_at  | datetime         | NOT NULL             |
| revoked_at  | datetime         |                      |
| created_at  | datetime         | NOT NULL             |

#### `password_reset_tokens`
| Column      | Type             | Notes                |
|-------------|------------------|----------------------|
| id          | varchar(36) PK   |                      |
| user_id     | varchar(36)      | FK → users, NOT NULL |
| token_hash  | varchar(255)     | UNIQUE, NOT NULL     |
| expires_at  | datetime         | NOT NULL             |
| used_at     | datetime         |                      |
| created_at  | datetime         | NOT NULL             |

#### `email_verification_tokens`
| Column      | Type             | Notes                                      |
|-------------|------------------|--------------------------------------------|
| id          | varchar(36) PK   | UUID                                       |
| user_id     | varchar(36)      | FK → users (ON DELETE CASCADE), NOT NULL    |
| token       | varchar(100)     | UNIQUE, NOT NULL                           |
| expires_at  | datetime         | NOT NULL (24 hours TTL)                    |
| created_at  | datetime         | NOT NULL, default CURRENT_TIMESTAMP        |

**Indexes**: `uk_email_verification_tokens_token`, `idx_email_verification_tokens_user`, `idx_email_verification_tokens_expires`

---

### 3.2 Movie

#### `movies`
| Column            | Type               | Notes                          |
|-------------------|--------------------|--------------------------------|
| id                | varchar(36) PK     |                                |
| tmdb_id           | bigint unsigned    | UNIQUE (nullable)              |
| title             | varchar(255)       | NOT NULL                       |
| original_title    | varchar(255)       |                                |
| overview          | text               |                                |
| duration_minutes  | smallint unsigned  | NOT NULL                       |
| director          | varchar(255)       |                                |
| actors            | text               |                                |
| country           | varchar(100)       |                                |
| language          | varchar(100)       |                                |
| release_date      | date               | NOT NULL                       |
| age_rating        | varchar(20)        | NOT NULL                       |
| poster_url        | varchar(500)       |                                |
| backdrop_url      | varchar(500)       |                                |
| trailer_url       | varchar(500)       |                                |
| status            | varchar(20)        | NOT NULL                       |
| created_at        | datetime           | NOT NULL                       |
| updated_at        | datetime           | NOT NULL                       |
| deleted_at        | datetime           | soft delete                    |
| version           | bigint             | optimistic lock                |

**Indexes**: `uk_movies_tmdb_id`, `idx_movies_title`, `idx_movies_release_date`, `idx_movies_status`

#### `genres`
| Column      | Type           | Notes            |
|-------------|----------------|------------------|
| id          | varchar(36) PK |                  |
| name        | varchar(100)   | UNIQUE, NOT NULL |
| description | varchar(255)   |                  |
| tmdb_id     | bigint         | UNIQUE (nullable)|

#### `movies_genres` (M:N)
| Column   | Type           | Notes |
|----------|----------------|-------|
| movie_id | varchar(36) PK | FK → movies |
| genre_id | varchar(36) PK | FK → genres |

---

### 3.3 Cinema

#### `cinemas`
| Column        | Type           | Notes                                      |
|---------------|----------------|--------------------------------------------|
| id            | varchar(36) PK |                                            |
| name          | varchar(255)   | NOT NULL                                   |
| address       | varchar(500)   | NOT NULL                                   |
| city          | varchar(100)   | NOT NULL                                   |
| opening_time  | time           | NOT NULL, default 08:00:00                 |
| closing_time  | time           | NOT NULL, default 23:00:00, CHECK > opening_time |
| status        | varchar(20)    | NOT NULL                                   |
| created_at    | datetime       | NOT NULL                                   |
| updated_at    | datetime       | NOT NULL                                   |
| deleted_at    | datetime       | soft delete                                |
| version       | bigint         | optimistic lock                            |

**Indexes**: `idx_cinemas_city_status`, `idx_cinemas_status`, `idx_cinemas_city`

#### `auditoriums`
| Column                 | Type               | Notes                                          |
|------------------------|--------------------|------------------------------------------------|
| id                     | varchar(36) PK     |                                                |
| cinema_id              | varchar(36)        | FK → cinemas, NOT NULL                         |
| name                   | varchar(100)       | NOT NULL                                       |
| type                   | varchar(20)        | NOT NULL                                       |
| rows_count             | smallint unsigned  | NOT NULL, CHECK > 0                           |
| columns_count          | smallint unsigned  | NOT NULL, CHECK > 0                           |
| turnaround_minutes     | smallint unsigned  | NOT NULL, default 15, CHECK ≥ 0               |
| snap_interval_minutes  | smallint unsigned  | NOT NULL, default 15, CHECK > 0               |
| status                 | varchar(20)        | NOT NULL                                       |
| created_at             | datetime           | NOT NULL                                       |
| updated_at             | datetime           | NOT NULL                                       |
| deleted_at             | datetime           | soft delete                                    |
| version                | bigint             | optimistic lock                                |

**Unique**: `uk_auditoriums_cinema_name` (cinema_id, name)  
**Indexes**: `idx_auditoriums_cinema_status`, `idx_auditoriums_cinema_id`

#### `seat_types`
| Column         | Type               | Notes                        |
|----------------|--------------------|------------------------------|
| id             | varchar(36) PK     | UUID                         |
| code           | varchar(50)        | UNIQUE, NOT NULL (e.g. STANDARD, VIP, COUPLE, PREMIUM) |
| name           | varchar(100)       | UNIQUE, NOT NULL             |
| capacity       | smallint unsigned  | NOT NULL, default 1, CHECK > 0 (immutable if referenced by seats) |
| color_token    | varchar(30)        | Whitelisted token (slate, amber, rose, indigo, emerald, purple, cyan, pink) |
| icon           | varchar(50)        | Whitelisted icon identifier (armchair, crown, heart, star, sofa, sparkles, shield, gem) |
| price_modifier | decimal(12,2)      | NOT NULL, CHECK ≥ 0          |
| description    | varchar(255)       |                              |
| status         | varchar(20)        | NOT NULL                     |
| created_at     | datetime           | NOT NULL                     |
| updated_at     | datetime           | NOT NULL                     |

**Unique**: `uk_seat_types_code`, `uk_seat_types_name`

#### `seats`
| Column         | Type               | Notes                              |
|----------------|--------------------|------------------------------------|
| id             | varchar(36) PK     |                                    |
| auditorium_id  | varchar(36)        | FK → auditoriums, NOT NULL         |
| seat_type_id   | varchar(36)        | FK → seat_types, NOT NULL          |
| row_label      | varchar(5)         | NOT NULL                           |
| seat_number    | smallint unsigned  | NOT NULL, CHECK > 0               |
| status         | varchar(20)        | NOT NULL                           |

**Unique**: `uk_seats_position` / `uk_seats_auditorium_position` (auditorium_id, row_label, seat_number)

---

### 3.4 Showtime

#### `showtimes`
| Column         | Type             | Notes                              |
|----------------|------------------|------------------------------------|
| id             | varchar(36) PK   |                                    |
| movie_id       | varchar(36)      | FK → movies, NOT NULL              |
| auditorium_id  | varchar(36)      | FK → auditoriums, NOT NULL         |
| format         | varchar(20)      | NOT NULL (e.g. 2D/3D/IMAX)         |
| language       | varchar(20)      | NOT NULL                           |
| subtitle       | varchar(30)      |                                    |
| start_time     | datetime         | NOT NULL                           |
| end_time       | datetime         | NOT NULL, CHECK > start_time       |
| base_price     | decimal(12,2)    | NOT NULL, CHECK ≥ 0                |
| status         | varchar(20)      | NOT NULL                           |
| created_at     | datetime         | NOT NULL                           |
| updated_at     | datetime         | NOT NULL                           |
| version        | bigint           | optimistic lock                    |

**Indexes**:
- `idx_showtimes_movie_start` (movie_id, start_time)
- `idx_showtimes_auditorium_start` (auditorium_id, start_time)
- `idx_showtimes_start_status` (start_time, status)
- `idx_showtimes_movie_id`, `idx_showtimes_auditorium_id`, `idx_showtimes_status`, `idx_showtimes_start_time`
- `idx_showtimes_movie_status_start` (movie_id, status, start_time)
- `idx_showtimes_auditorium_time_range` (auditorium_id, start_time, end_time)
- `idx_showtimes_calendar` (start_time, end_time, status)

---

### 3.5 Pricing

#### `day_pricing_rules`
| Column      | Type           | Notes                        |
|-------------|----------------|------------------------------|
| id          | varchar(36) PK | UUID                         |
| day_of_week | varchar(20)    | UNIQUE, NOT NULL (MONDAY..SUNDAY) |
| modifier    | decimal(12,2)  | NOT NULL (supports 0, positive surcharges, negative discounts) |
| created_at  | datetime       | NOT NULL                     |
| updated_at  | datetime       | NOT NULL                     |

#### `time_slot_pricing_rules`
| Column     | Type           | Notes                              |
|------------|----------------|------------------------------------|
| id         | varchar(36) PK | UUID                               |
| name       | varchar(100)   | NOT NULL (e.g. Early Bird, Peak Hours, Late Night) |
| start_time | time           | NOT NULL                           |
| end_time   | time           | NOT NULL, CHECK > start_time       |
| modifier   | decimal(12,2)  | NOT NULL (supports 0, positive surcharges, negative discounts) |
| created_at | datetime       | NOT NULL                           |
| updated_at | datetime       | NOT NULL                           |

**Pricing Formula V1 Finalized**:
$$\text{Ticket Price} = \max(0, \text{base\_price} + \text{seat\_type.price\_modifier} + \text{day\_pricing\_rules.modifier} + \text{time\_slot\_pricing\_rules.modifier})$$
Managed authoritatively by `PricingService`. Time slot rules match half-open intervals $[start\_time, end\_time)$. Historical ticket prices are snapshotted in `tickets.ticket_price` and `bookings.total_amount` upon creation/payment and are never recalculated.

---

### 3.6 Booking

#### `bookings`
| Column               | Type           | Notes                              |
|----------------------|----------------|------------------------------------|
| id                   | varchar(36) PK |                                    |
| booking_code         | varchar(30)    | UNIQUE, NOT NULL                   |
| check_in_code        | varchar(36)    | UNIQUE, NOT NULL (QR code credential for check-in) |
| user_id              | varchar(36)    | FK → users, NOT NULL               |
| showtime_id          | varchar(36)    | FK → showtimes, NOT NULL           |
| total_amount         | decimal(12,2)  | NOT NULL, CHECK ≥ 0                |
| booking_status       | varchar(30)    | NOT NULL                           |
| hold_expires_at      | datetime       |                                    |
| cancelled_at         | datetime       |                                    |
| cancelled_by_user_id | varchar(36)    | FK → users (nullable)              |
| cancelled_reason     | varchar(500)   |                                    |
| created_at           | datetime       | NOT NULL                           |
| updated_at           | datetime       | NOT NULL                           |
| version              | bigint         | optimistic lock                    |

**Indexes**: user+created, showtime+status, hold_expires_at, `uk_bookings_check_in_code` (check_in_code)

#### `seat_holds`
| Column      | Type               | Notes                              |
|-------------|--------------------|------------------------------------|
| id          | bigint unsigned PK | AUTO_INCREMENT (only non-UUID PK)  |
| showtime_id | varchar(36)        | FK → showtimes, NOT NULL           |
| seat_id     | varchar(36)        | FK → seats, NOT NULL               |
| booking_id  | varchar(36)        | FK → bookings, NOT NULL            |
| expires_at  | datetime           | NOT NULL                           |
| created_at  | datetime           | NOT NULL                           |

**Unique (critical)**: `uk_seat_holds_showtime_seat` (showtime_id, seat_id)  
→ A seat can be held only once per showtime.

#### `tickets`
| Column        | Type           | Notes                              |
|---------------|----------------|------------------------------------|
| id            | varchar(36) PK |                                    |
| booking_id    | varchar(36)    | FK → bookings, NOT NULL            |
| seat_id       | varchar(36)    | FK → seats, NOT NULL               |
| ticket_price  | decimal(12,2)  | NOT NULL, CHECK ≥ 0                |
| ticket_status | varchar(20)    | NOT NULL                           |
| qr_code       | varchar(255)   | UNIQUE (nullable)                  |
| created_at    | datetime       | NOT NULL                           |

**Unique**: `uk_tickets_booking_seat` (booking_id, seat_id)

#### `booking_promotions` (M:N snapshot)
| Column          | Type           | Notes                    |
|-----------------|----------------|--------------------------|
| promotion_id    | varchar(36) PK | FK → promotions          |
| booking_id      | varchar(36) PK | FK → bookings            |
| discount_amount | decimal(12,2)  | NOT NULL, CHECK ≥ 0      |
| created_at      | datetime       | NOT NULL                 |

---

### 3.7 Payment

#### `payments`
| Column                 | Type           | Notes                              |
|------------------------|----------------|------------------------------------|
| id                     | varchar(36) PK |                                    |
| booking_id             | varchar(36)    | FK → bookings, NOT NULL            |
| payment_method         | varchar(20)    | NOT NULL                           |
| payment_code           | varchar(50)    | UNIQUE, NOT NULL                   |
| gateway_transaction_id | varchar(100)   |                                    |
| amount                 | decimal(12,2)  | NOT NULL, CHECK ≥ 0                |
| payment_status         | varchar(20)    | NOT NULL                           |
| paid_at                | datetime       |                                    |
| gateway_response       | json           |                                    |
| created_at             | datetime       | NOT NULL                           |
| updated_at             | datetime       | NOT NULL                           |

#### `refunds`
| Column            | Type           | Notes                              |
|-------------------|----------------|------------------------------------|
| id                | varchar(36) PK |                                    |
| payment_id        | varchar(36)    | FK → payments, UNIQUE, NOT NULL    |
| refund_code       | varchar(50)    | UNIQUE, NOT NULL                   |
| gateway_refund_id | varchar(100)   |                                    |
| amount            | decimal(12,2)  | NOT NULL, CHECK ≥ 0                |
| refund_reason     | varchar(255)   |                                    |
| refund_status     | varchar(20)    | NOT NULL                           |
| processed_at      | datetime       |                                    |
| created_at        | datetime       | NOT NULL                           |

**Cardinality**: One payment has at most one refund (`uk_refunds_payment`).

---

### 3.8 Promotion

#### `promotions`
| Column              | Type             | Notes                                      |
|---------------------|------------------|--------------------------------------------|
| id                  | varchar(36) PK   |                                            |
| code                | varchar(50)      | UNIQUE, NOT NULL                           |
| name                | varchar(255)     | NOT NULL                                   |
| description         | varchar(500)     |                                            |
| discount_type       | varchar(20)      | NOT NULL                                   |
| discount_value      | decimal(12,2)    | NOT NULL, CHECK ≥ 0                        |
| min_order_amount    | decimal(12,2)    | nullable, CHECK ≥ 0                        |
| max_discount_amount | decimal(12,2)    | nullable, CHECK ≥ 0                        |
| start_at            | datetime         | NOT NULL                                   |
| end_at              | datetime         | NOT NULL, CHECK > start_at                 |
| usage_limit         | int unsigned     | nullable                                   |
| used_count          | int unsigned     | NOT NULL, default 0, CHECK ≤ usage_limit  |
| status              | varchar(20)      | NOT NULL                                   |
| created_at          | datetime         | NOT NULL                                   |
| updated_at          | datetime         | NOT NULL                                   |
| version             | bigint           | optimistic lock                            |

---

### 3.9 Food & Concessions (Phase 3.1)

#### `food_items`
| Column            | Type             | Notes                                                 |
|-------------------|------------------|-------------------------------------------------------|
| id                | varchar(36) PK   | UUID                                                  |
| name              | varchar(100)     | NOT NULL                                              |
| description       | varchar(255)     |                                                       |
| price             | decimal(12,2)    | NOT NULL, CHECK ≥ 0                                   |
| image_url         | varchar(500)     |                                                       |
| status            | varchar(20)      | NOT NULL, default 'ACTIVE' (ACTIVE, INACTIVE)         |
| created_at        | datetime         | NOT NULL, default CURRENT_TIMESTAMP                   |
| updated_at        | datetime         | NOT NULL, default CURRENT_TIMESTAMP ON UPDATE         |
| deleted_at        | datetime         | soft delete                                           |

**Constraints & Indexes**:
- `uk_food_items_name_deleted UNIQUE (name, deleted_at)`
- `idx_food_items_status (status)`
- `idx_food_items_deleted (deleted_at)`
- **Data Integrity & Encoding**: Table collation is `utf8mb4_0900_ai_ci`. All food items are persisted in clean Vietnamese UTF-8 text. JDBC connection string in `application.yml` explicitly enforces `&characterEncoding=UTF-8` to prevent mojibake across transport layers.

#### `booking_foods`
| Column            | Type             | Notes                                                 |
|-------------------|------------------|-------------------------------------------------------|
| id                | varchar(36) PK   | UUID                                                  |
| booking_id        | varchar(36)      | FK → bookings (ON DELETE CASCADE), NOT NULL           |
| food_item_id      | varchar(36)      | FK → food_items, NOT NULL                             |
| food_name         | varchar(100)     | NOT NULL (snapshot at booking time)                   |
| unit_price        | decimal(12,2)    | NOT NULL (snapshot at booking time)                   |
| quantity          | int              | NOT NULL, 1..20                                       |
| subtotal          | decimal(12,2)    | NOT NULL (unit_price * quantity)                      |
| created_at        | datetime         | NOT NULL, default CURRENT_TIMESTAMP                   |

**Indexes**:
- `idx_booking_foods_booking (booking_id)`
- `idx_booking_foods_food_item (food_item_id)`

---

### 3.10 Notifications (Phase 3.2: In-App Notifications)

#### `notifications`
| Column            | Type             | Notes                                                 |
|-------------------|------------------|-------------------------------------------------------|
| id                | varchar(36) PK   | UUID                                                  |
| user_id           | varchar(36)      | FK → users (ON DELETE CASCADE), NOT NULL              |
| booking_id        | varchar(36)      | FK → bookings (ON DELETE CASCADE), NOT NULL           |
| type              | varchar(50)      | NOT NULL (PAYMENT_SUCCESS, BOOKING_CANCELLED, REFUND_COMPLETED) |
| title             | varchar(255)     | NOT NULL                                              |
| message           | text             | NOT NULL                                              |
| is_read           | tinyint(1)       | NOT NULL, default 0                                   |
| created_at        | datetime         | NOT NULL, default CURRENT_TIMESTAMP                   |
| read_at           | datetime         | NULL (timestamp when marked as read)                 |

**Unique Constraints & Idempotency**:
- `uk_notifications_booking_type UNIQUE (booking_id, type)`: Ensures strictly one notification per business event per booking at database level.

**Indexes**:
- `idx_notifications_user_unread (user_id, is_read, created_at)`
- `idx_notifications_user_created (user_id, created_at)`
- `idx_notifications_booking (booking_id)`

---

## 4. Important Relationships (Cardinality)

```text
users 1 ─── * bookings
users 1 ─── * refresh_tokens
users 1 ─── * password_reset_tokens
users 1 ─── * notifications
users * ─── * roles                  (via user_roles)

movies * ─── * genres                (via movies_genres)
movies 1 ─── * showtimes

cinemas 1 ─── * auditoriums
auditoriums 1 ─── * seats
seat_types 1 ─── * seats

showtimes 1 ─── * bookings
showtimes 1 ─── * seat_holds
seats 1 ─── * seat_holds
seats 1 ─── * tickets

bookings 1 ─── * tickets
bookings 1 ─── * seat_holds
bookings 1 ─── * payments
bookings 1 ─── * booking_foods
bookings 1 ─── * notifications
bookings * ─── * promotions          (via booking_promotions)
food_items 1 ─── * booking_foods

payments 1 ─── 0..1 refunds
```

---

## 5. Critical Constraints & Invariants (Database Level)

| Constraint | Purpose |
|------------|---------|
| `uk_seat_holds_showtime_seat` | Prevent double-hold of the same seat on the same showtime |
| `uk_tickets_booking_seat` | One ticket per seat inside a booking |
| `uk_bookings_code` | Human-readable booking code is unique |
| `uk_payments_code` | Payment code is unique |
| `uk_refunds_payment` | At most one refund per payment |
| `uk_movies_tmdb_id` | One CineBook movie per TMDB id |
| `uk_users_email` / `uk_users_phone` | Identity uniqueness |
| `chk_*_amount` / `chk_*_price` | Non-negative monetary values |
| `chk_showtimes_time` | end_time > start_time |
| `chk_cinemas_operating_hours` | closing_time > opening_time |
| `chk_auditoriums_turnaround` / `chk_auditoriums_snap_interval` | turnaround ≥ 0, snap_interval > 0 |
| `version` columns | Optimistic concurrency control on aggregates |

**Application layer must still enforce**:
- A seat is sold only once per showtime (hold → paid ticket flow).
- Booking total consistency with pricing rules.
- Payment status ↔ Booking status consistency.
- Expired holds are no longer valid.

---

## 6. Status / Enum-like Columns

Exact allowed values are **not hard-coded as MySQL ENUM**. They are `varchar` and must be treated as application-level enums.

Known columns that act as status:

| Table          | Column           | Typical values (to be confirmed in code/docs) |
|----------------|------------------|-----------------------------------------------|
| users          | status           | ACTIVE, LOCKED, …                             |
| movies         | status           | COMING_SOON, NOW_SHOWING, ENDED, …            |
| cinemas        | status           | ACTIVE, INACTIVE, CLOSED                      |
| auditoriums    | status           | ACTIVE, MAINTENANCE, DECOMMISSIONED           |
| seats          | status           | ACTIVE, BROKEN                                |
| showtimes      | status           | SCHEDULED, CANCELLED                          |
| bookings       | booking_status   | HOLD, PENDING_PAYMENT, PAID, CANCELLED, …     |
| tickets        | ticket_status    | VALID, USED, CANCELLED, …                     |
| payments       | payment_status   | PENDING, SUCCESS, FAILED, …                   |
| refunds        | refund_status    | PENDING, SUCCESS, FAILED, …                   |
| promotions     | status           | ACTIVE, INACTIVE, EXPIRED, …                  |
| seat_types     | status           | ACTIVE, INACTIVE                              |

**Do not invent status values.** Inspect existing entities/enums in source code when implementing.

---

## 7. Soft Delete & Versioning

- Soft-deleted entities (`deleted_at IS NOT NULL`) must be excluded from normal business queries unless the use case explicitly needs historical data.
- Entities with `version` column should use optimistic locking (`@Version` in JPA) on update paths that can conflict.

---

## 8. Open Decisions Related to Data

Do **not** invent values:

- Exact set of allowed status strings for each status column
- Seat-hold expiration duration (and whether it is stored only in `hold_expires_at` / `expires_at` or also configurable)
- Pricing calculation formula / precedence (day vs time-slot vs seat-type)
- Whether a booking can have multiple payments or only one active payment
- Refund business rules (partial refund, time windows, etc.)
- Promotion stacking rules

When a feature depends on one of the above, ask the developer or inspect existing code first.

---

## 9. Schema Change Policy

From `AGENTS.md`:

- Never drop / truncate tables or large data without explicit instruction.
- Never remove important constraints or indexes.
- Never change primary keys or break foreign keys.
- If schema is insufficient → identify gap → propose smallest safe change → get approval before destructive changes.

Non-destructive additive changes required by a feature may be implemented when consistent with the existing design.

### 9.1 Phase 4 Schema Impact (Business Analytics & Reporting)

Phase 4 (4.1 Report Export XLSX/CSV & 4.2 Final System Audit) introduces **zero database schema modifications** (no new tables, columns, constraints, or indexes).
All analytics, dashboard aggregates, and report exports operate strictly as read-only projections and native SQL aggregations over the existing 29 JPA entities (`payments`, `refunds`, `bookings`, `tickets`, `showtimes`, `auditoriums`, `cinemas`, `seats`, `movies`, `users`, etc.).

---

*This document is the reference for how CineBook data is organized.  
Full DDL lives in the database dump / migration scripts; do not duplicate the entire DDL here.*
