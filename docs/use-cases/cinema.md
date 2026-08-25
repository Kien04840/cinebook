# Cinema & Showtime Domain Specification

## 1. Purpose

This document is the canonical planning specification for the **Cinema, Auditorium, Seat, Seat Type, and Showtime** domain of CineBook.

It is intended to be read by an AI coding agent before implementation. The agent must use this document together with:

- `AGENTS.md`
- `docs/documentation-map.md`
- `docs/architecture.md`
- `docs/database.md`
- `docs/business-rules.md`
- `docs/api.md`
- `.agents/rules/backend.md`
- `.agents/rules/database.md`
- `.agents/skills/implement-backend-feature/SKILL.md`
- `.agents/skills/database-change/SKILL.md`

Existing source code and the current database schema are authoritative for the implementation state. This document describes the intended domain behavior and must not be used to overwrite existing implementation facts without inspection.

---

## 2. Domain Scope

The Cinema domain covers:

1. Cinema management
2. Auditorium management
3. Seat type management
4. Auditorium seat-layout management
5. Showtime management
6. Showtime conflict detection
7. Customer-facing cinema/showtime discovery

The system currently targets cinemas in **three cities only**:

- Hanoi
- Da Nang
- Ho Chi Minh City

The city scope should be implemented in a way that does not unnecessarily hard-code assumptions into the domain model. Future expansion to additional cities should remain possible.

---

## 3. Domain Relationships

The intended relationship is:

```text
City
  │
  └── Cinema
        │
        └── Auditorium
              │
              ├── Seat
              │     └── SeatType
              │
              └── Showtime
                     │
                     └── Movie
```

Important relationships:

- A cinema belongs to a city.
- A cinema contains one or more auditoriums.
- An auditorium belongs to exactly one cinema.
- An auditorium contains seats.
- Each seat has a seat type.
- A showtime belongs to exactly one movie.
- A showtime belongs to exactly one auditorium.
- A movie may have many showtimes.
- An auditorium may have many showtimes over time, but their active time intervals must not overlap.

The exact entity fields, constraints, indexes, and identifier strategy must be taken from `docs/database.md` and the existing schema before implementation.

---

## 4. Cinema Management

### Admin capabilities

An authorized administrator can:

- View cinemas
- Search/filter cinemas
- Create a cinema
- Update a cinema
- Delete/deactivate a cinema where allowed by the existing business rules
- View the auditoriums belonging to a cinema

Cinema data should support the three initial target cities:

- Hanoi
- Da Nang
- Ho Chi Minh City

The implementation should preserve a clean separation between cinema identity/location data and auditorium/seat data.

### Important considerations

Before changing the schema or entity model, inspect the existing `cinemas` table and related foreign keys.

Do not introduce a new city table or additional location hierarchy merely for convenience unless the current schema and business requirements justify it.

---

## 5. Auditorium Management

### Admin capabilities

An authorized administrator can:

- View auditoriums belonging to a cinema
- Create an auditorium
- Update an auditorium
- Delete/deactivate an auditorium where allowed
- Configure its seat layout
- View its seats and seat types

An auditorium must belong to an existing cinema.

The auditorium type and seat configuration must follow the existing database/business rules.

### Seat matrix generation

The system is intended to generate an auditorium's seat layout from administrator-provided dimensions/configuration rather than requiring the administrator to manually create every seat.

For example:

```text
Rows: 12
Columns: 10

A01 A02 A03 ... A10
B01 B02 B03 ... B10
C01 C02 C03 ... C10
...
```

The exact generation rules, identifiers, naming conventions, and editable properties must follow the existing database/business specification.

The administrator should be able to configure **seat type**, while the application remains responsible for maintaining the generated matrix structure.

Do not invent additional seat-layout concepts if they are not present in the existing schema.

---

## 6. Seat Type Management

Seat types represent categories such as standard/VIP or other types already defined by the project.

Admin capabilities may include:

- View seat types
- Create/update seat types if the existing API/domain permits it
- Assign a seat type to a seat
- Change a seat's type

The implementation must preserve referential integrity between:

```text
auditorium → seats → seat_type
```

Seat-type pricing behavior must not be mixed into the seat entity unless explicitly required by the existing pricing model.

Pricing rules are separately documented and should remain governed by:

`docs/business-rules.md`

and the relevant database definitions.

---

# 7. Showtime Management

## 7.1 Overview

An administrator can manage movie showtimes for auditoriums.

The use case includes:

- View showtimes
- Search showtimes
- Filter showtimes
- Create showtimes
- Update showtimes
- Delete showtimes
- Cancel showtimes
- Detect schedule conflicts

A showtime contains, according to the current specification:

- `id`
- `movie_id`
- `auditorium_id`
- `format`
- `language`
- `subtitle`
- `start_time`
- `end_time`
- `base_price`
- `status`
- `created_at`

The exact field types and additional audit fields must follow `docs/database.md` and the existing entity/schema.

---

## 7.2 Showtime List and Search

The admin showtime management screen should support viewing showtimes with at least:

- Movie
- Auditorium
- Format
- Language
- Subtitle
- Start time
- End time
- Base price
- Status

Search/filter criteria should support, where applicable:

- Date
- Movie
- Auditorium
- Format
- Language
- Status
- Keyword or other relevant criteria

Queries should be implemented using the existing backend conventions and should avoid unnecessary database loading.

Movie and auditorium information should be resolved through their respective relationships rather than duplicating movie/auditorium data inside the showtime record.

---

## 7.3 Creating a Showtime

Before creating a showtime, the system must validate:

### Authorization

Only an administrator with the required management permission may create a showtime.

### Movie existence

`movie_id` must reference an existing movie.

### Movie status

Only movies with status:

- `COMING_SOON`
- `NOW_SHOWING`

may receive new showtimes.

Movies with:

- `ENDED`
- `HIDDEN`

must not receive new showtimes.

### Auditorium existence

`auditorium_id` must reference an existing auditorium.

### Time validity

```text
end_time > start_time
```

must always hold.

### Schedule conflict

An auditorium must not contain two overlapping showtimes.

For two intervals:

```text
A = [startA, endA]
B = [startB, endB]
```

they conflict when their time ranges overlap.

A new showtime is valid only if there is no conflicting existing showtime for the same auditorium.

The implementation must account for boundary conditions correctly. For example, a showtime ending exactly when another begins should not be treated as an overlap unless the existing business rules explicitly define otherwise.

### Successful creation

After validation succeeds:

- Create the showtime.
- Persist the requested metadata.
- Record creation time.
- Return the appropriate API response.

---

# 8. Updating a Showtime

An administrator can update an existing showtime.

The system must identify the showtime by its `id` and validate all affected constraints again.

At minimum, validation must cover:

- Movie existence
- Movie status
- Auditorium existence
- `end_time > start_time`
- Auditorium schedule conflict
- Booking/ticket impact

## 8.1 Showtimes without bookings

If no booking/ticket transaction has been created for the showtime, normal editable fields may be changed according to the business rules.

## 8.2 Showtimes with existing bookings

If the showtime already has bookings/tickets, the system must protect data required for the correctness of sold tickets.

In particular, the following fields must not be changed in a way that invalidates existing tickets:

- `movie_id`
- `auditorium_id`
- `start_time`
- `end_time`

Other fields must also be evaluated if they directly affect an existing transaction.

The exact restriction should follow `docs/business-rules.md`.

---

# 9. Deleting and Cancelling Showtimes

Deletion behavior depends on whether the showtime has existing booking transactions.

### No existing bookings

If the showtime has never generated booking transactions:

- The administrator may delete it after confirmation.
- The showtime record may be physically deleted if permitted by the existing schema/business rules.

### Existing bookings

If the showtime has generated booking transactions:

- The record must not be physically deleted.
- Its status must be changed to:

```text
CANCELLED
```

This preserves booking history and data integrity.

Cancellation must also follow the system's booking/refund policy.

The payment/refund behavior is governed by:

- `docs/business-rules.md`
- `docs/payment.md`

Do not implement refund behavior independently inside the cinema module unless the existing architecture explicitly assigns it there.

---

# 10. Showtime Status and Lifecycle

The implementation must use the project's existing `ShowtimeStatus` definition rather than inventing a second status model.

The following lifecycle is expected conceptually:

```text
Scheduled/Active
      │
      ├── completed → appropriate final status
      │
      └── cancelled → CANCELLED
```

The exact enum values and transitions must be inspected from the current code/database.

Do not introduce status values solely because they appear convenient for the UI.

---

# 11. Business Rules

The following rules are mandatory for this domain:

1. Only authorized administrators can perform cinema/auditorium/seat/showtime management operations.
2. Every auditorium belongs to an existing cinema.
3. Every seat belongs to an existing auditorium.
4. Every seat references a valid seat type.
5. Every showtime references an existing movie.
6. Every showtime references an existing auditorium.
7. A showtime may only be created for a movie in an allowed lifecycle state (`COMING_SOON` or `NOW_SHOWING`).
8. `end_time` must be later than `start_time`.
9. An auditorium must not have overlapping showtimes.
10. A showtime with existing booking transactions must not be physically deleted.
11. A booked showtime must be cancelled using `CANCELLED` when cancellation is required.
12. Existing sold tickets must remain historically consistent.
13. Updating a booked showtime must not modify information that invalidates existing tickets.
14. Database operations must preserve foreign-key and transaction integrity.
15. Existing database safety rules in `.agents/rules/database.md` are mandatory.

---

# 12. Customer-Facing Requirements

The cinema/showtime domain will later support customer-facing functionality such as:

- Browse cinemas
- Filter cinemas by city
- View cinema details
- View auditoriums where appropriate
- Browse available showtimes
- Filter showtimes by movie/date/cinema
- Select a showtime before seat selection and booking

The customer APIs should expose only data appropriate for public use.

Admin management endpoints and customer-facing endpoints should remain separated according to the existing API conventions.

---

# 13. Initial City Scope

For the current project scope, cinemas are limited to:

| City | Initial support |
|---|---|
| Hanoi | Yes |
| Da Nang | Yes |
| Ho Chi Minh City | Yes |

Do not assume that these are permanent system-wide limitations.

The implementation should allow future expansion without requiring major domain redesign.

The exact representation of city/location must follow the existing database schema. If the schema currently stores city as a field, use that model rather than introducing unnecessary normalization.

---

# 14. API Expectations

Before implementing endpoints, inspect:

- `docs/api.md`
- Existing Movie/Genre APIs
- Existing controller/service/repository conventions
- Existing DTO and mapper patterns

Expected API groups conceptually include:

```text
Admin
├── Cinema CRUD
├── Auditorium CRUD
├── Seat / Seat Type management
└── Showtime CRUD + search/filter

Customer
├── Cinema discovery
├── Cinema-by-city
├── Movie showtimes
└── Showtime discovery/filtering
```

Exact endpoint paths, request/response DTOs, pagination format, sorting format, validation error format, and authorization rules must follow the project's existing API conventions.

Do not create a new API style for this module.

---

# 15. Database and Transaction Considerations

Before modifying anything:

1. Inspect the current schema.
2. Inspect the relevant entities.
3. Inspect repositories and existing query patterns.
4. Inspect existing services/controllers.
5. Compare implementation with `docs/database.md` and `docs/business-rules.md`.
6. Identify whether the required schema already exists.

Avoid schema changes unless they are genuinely necessary.

For showtime conflict detection, the implementation must be safe against concurrent creation/update requests. Application-level checking alone may be insufficient if the current architecture allows concurrent writes.

The implementation strategy must be proposed and reviewed before introducing locking, transaction isolation changes, or database-level constraints.

Use the existing project transaction/concurrency conventions.

---

# 16. Frontend Preparation

The eventual admin UI should provide separate management views for:

```text
Cinema
  ├── Cinema list/search/filter
  ├── Create/Edit Cinema
  └── Auditorium management

Auditorium
  ├── Auditorium list
  ├── Seat matrix
  └── Seat type assignment

Showtime
  ├── Showtime list
  ├── Search/filter
  ├── Create Showtime
  ├── Edit Showtime
  └── Cancel/Delete Showtime
```

The customer UI can later expose:

```text
City
  → Cinema
      → Movie
          → Showtime
              → Seat selection
```

Frontend implementation should follow `.agents/rules/frontend.md` and existing Vue/Pinia/API patterns.

---

# 17. Error Scenarios

The backend should provide consistent errors for cases such as:

- Unauthorized/forbidden management access
- Cinema not found
- Auditorium not found
- Seat/seat type not found
- Movie not found
- Movie status does not allow showtime creation
- Invalid start/end time
- Showtime overlap
- Attempt to modify protected booked-showtime fields
- Attempt to delete a showtime with bookings
- Invalid city
- Database failure

Exact HTTP status codes and response structures must follow `docs/api.md` and existing global exception handling.

Do not invent a separate error-response mechanism.

---

# 18. Implementation Guidance for the AI Agent

When beginning the Cinema module:

### Phase 1 — Inspect

Read:

- `docs/use-cases/cinema.md` if it exists
- `docs/business-rules.md` relevant cinema/showtime sections
- `docs/database.md` relevant schema sections
- `docs/api.md`
- `docs/architecture.md`
- `.agents/rules/backend.md`
- `.agents/rules/database.md`

Then inspect the actual codebase and database schema.

### Phase 2 — Scope

Determine:

- Which cinema entities already exist
- Which auditorium/seat entities already exist
- Which repositories/services/controllers already exist
- Which APIs already exist
- Which schema changes, if any, are necessary
- Which requirements belong to the Cinema module versus Booking/Pricing/Payment

Do not implement yet.

### Phase 3 — Plan

Produce a concrete implementation plan covering:

- Entity/model changes
- Repository/query changes
- DTOs/mappers
- Services
- Controllers
- Validation
- Transactions/concurrency
- Tests
- API verification
- Documentation updates

Explicitly identify risks around showtime overlap and booked-showtime modification.

### Phase 4 — Implement

Follow:

`.agents/skills/implement-backend-feature/SKILL.md`

and, only when necessary:

`.agents/skills/database-change/SKILL.md`

Implement incrementally and preserve existing working behavior.

### Phase 5 — Verify

At minimum verify:

- Cinema CRUD
- Auditorium CRUD
- Seat generation/configuration
- Seat type assignment
- Showtime CRUD
- Showtime search/filter
- Movie-status validation
- Invalid time validation
- Overlap detection
- Booked-showtime protection
- Cancellation behavior
- Authorization
- Database integrity
- Regression tests

Use the project's established testing and API verification approach.

### Phase 6 — Review and Report

Before finishing:

- Run the full test suite.
- Review changed files.
- Check for duplicated logic.
- Check transaction boundaries.
- Check API consistency.
- Check documentation consistency.
- Report all assumptions and decisions that were not explicitly defined by this document.

---

# 19. Decisions That Must Not Be Invented Without Review

The following are intentionally left open unless already defined elsewhere:

- Exact cinema fields beyond the existing schema
- Exact city representation
- Additional cities
- Auditorium categories beyond the current model
- Additional seat types
- Showtime status enum changes
- Pricing algorithms
- Booking/refund implementation details
- Automatic showtime lifecycle jobs
- Database-level exclusion/locking strategy
- New external services

If implementation requires one of these decisions, stop at the planning stage, explain the alternatives, and request approval rather than silently inventing a new architecture.

---

# 20. Definition of Done

The Cinema domain is considered complete only when:

- The implementation matches the existing architecture.
- Cinema/auditorium/seat/seat-type relationships are valid.
- Showtime CRUD is functional.
- Showtime search/filter is functional.
- Movie eligibility is enforced.
- Invalid time ranges are rejected.
- Auditorium schedule conflicts are prevented.
- Booked showtimes cannot be destructively modified.
- Cancellation preserves historical booking data.
- Authorization is enforced.
- Relevant unit/integration tests pass.
- Existing tests continue to pass.
- APIs follow the project's established conventions.
- No unnecessary schema changes are introduced.
- Documentation is updated where implementation decisions change the canonical project behavior.
