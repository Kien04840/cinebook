# Showtime Domain Specification — V1 Professional Showtime

## 0. Purpose

This document is the canonical specification for the **CineBook Showtime backend — V1 Professional Showtime**.

It intentionally avoids duplicating global architecture, database DDL, API conventions, security rules, and cross-domain business rules already defined elsewhere.

### Canonical references

The implementation agent MUST use:

- `AGENTS.md`
- `docs/documentation-map.md`
- `docs/architecture.md`
- `docs/database.md`
- `docs/business-rules.md`
- `docs/api.md`
- `docs/use-cases/cinema.md`
- `docs/use-cases/movie.md`
- `.agents/rules/backend.md`
- `.agents/rules/database.md`
- `.agents/skills/implement-backend-feature/SKILL.md`
- `.agents/skills/database-change/SKILL.md` only when a schema change is genuinely required

**Source priority:** existing code and current database schema are authoritative for implementation state. This document defines intended Showtime behavior and must be reconciled with those sources during inspection.

**Scope:** backend only. Vue/frontend is out of scope.

---

# 1. Implementation Prompt for Antigravity

Copy this prompt to Antigravity when implementing the Showtime backend module.

```text
Implement the CineBook Showtime backend module — V1 Professional Showtime.

BACKEND ONLY. Do not implement Vue/frontend/admin UI.

============================================================
MANDATORY DOCUMENTATION ROUTING
============================================================

Before coding, read:

1. AGENTS.md
2. docs/documentation-map.md
3. docs/architecture.md
4. docs/database.md
   - especially showtimes, auditoriums, cinemas, statuses,
     indexes, constraints, operating-hours/configuration fields
5. docs/business-rules.md
   - especially Cinema/Auditorium and Showtime rules
6. docs/api.md
   - especially Cinema, Showtime and related public/admin contracts
7. docs/use-cases/showtime.md
8. docs/use-cases/cinema.md
9. docs/use-cases/movie.md
10. .agents/rules/backend.md
11. .agents/rules/database.md
12. .agents/skills/implement-backend-feature/SKILL.md
13. .agents/skills/database-change/SKILL.md only if a schema change is required

Do not load unrelated payment, TMDB, authentication, or booking
documentation unless the implementation specifically needs a rule
from it.

Existing source code and the current database schema are authoritative.
Do not invent entities, enums, API styles, fields, or architecture.

Do not implement Booking, Payment, Pricing administration, or frontend.

============================================================
PHASE 1 — INSPECT FIRST
============================================================

Do not implement immediately.

Inspect:

- current Showtime entity/status/repository/service/controller
- Auditorium entity/status/update API
- Cinema entity/status and operating-hours configuration
- Movie entity/status/duration
- Booking/Ticket queries needed to determine whether a showtime
  has transaction history
- existing DTO/mapper/validation/exception/pagination patterns
- current database schema and indexes

Determine what already exists versus what is missing.

Produce a concise implementation plan before coding.

============================================================
PHASE 2 — MANDATORY AUDITORIUM STATUS FIX
============================================================

Verify that AuditoriumStatus supports:

ACTIVE <-> MAINTENANCE

Both directions must work through the existing backend auditorium
update flow.

Rules:

- ACTIVE -> MAINTENANCE:
  - block new showtimes
  - do not silently delete existing showtimes
  - do not silently cancel existing showtimes unless already defined
    elsewhere

- MAINTENANCE -> ACTIVE:
  - make the auditorium eligible for new showtimes
  - do not auto-create showtimes

Do not add extra auditorium statuses.

New showtimes require:

- auditorium exists
- auditorium is not soft-deleted
- auditorium status = ACTIVE
- parent cinema exists
- cinema is not soft-deleted
- cinema status = ACTIVE

Public showtime APIs must exclude showtimes belonging to ineligible
cinemas/auditoriums.

============================================================
PHASE 3 — V1 PROFESSIONAL SHOWTIME SCOPE
============================================================

Implement:

CORE
- Admin CRUD
- Search/filter
- Movie duration integration
- Auditorium conflict validation
- Booked-showtime protection
- Cancellation
- Public discovery
- Auditorium eligibility

SCHEDULING
- Auditorium turnaround/buffer
- Snap interval
- Cinema operating hours
- Scheduling validation with explicit conflict reasons
- Calendar/range query
- Rule-based auto-generation
- Generation preview before persistence
- Copy schedule
- Staggered start support

Do NOT implement:
- AI/forecasting/revenue optimization
- demand prediction
- automatic optimization across movie popularity
- maintenance-window scheduling unless already supported by the
  existing schema
- schedule audit/history framework
- separate DraftSchedule entity
- payment/refund logic
- distributed/Redis locks
- new microservices
- frontend

============================================================
PHASE 4 — SHOWTIME TIME MODEL
============================================================

Do not model auditorium availability as ending exactly at movie end.

For a showtime:

start_time
    |
    +---- Movie duration ----> end_time
                                |
                                +---- turnaround_minutes ---->
                                      earliest next start
                                      |
                                      +---- snap interval ---->
                                            scheduled next start

Example:

Movie duration = 112 min
start = 14:00
end = 15:52
turnaround = 15 min
earliest next start = 16:07
snap interval = 5 min
next start = 16:10

The scheduling occupancy rule is:

occupied_until = end_time + turnaround_minutes

A subsequent showtime must start at or after occupied_until, then be
snapped forward when generation is being used.

IMPORTANT:
Inspect the actual database/entity fields before adding anything.
If V1 configuration fields are not yet present in the schema, identify
the required schema change during planning and follow the database-change
skill. Do not silently alter the schema.

============================================================
PHASE 5 — TURNAROUND AND SNAP
============================================================

V1 configuration:

- turnaround_minutes: configurable per auditorium
- snap_interval_minutes: scheduling-generation configuration

MVP policy:

- turnaround belongs to an auditorium
- do not create a global/cinema override hierarchy yet
- do not hard-code one global turnaround value

Default values may only be introduced if already defined by the
authoritative database/business-rule documents. Otherwise report the
missing decision.

Snap behavior:

When auto-generating a candidate start time, round it forward to the
next valid snap boundary.

Examples:

snap = 5:
15:24 -> 15:25

snap = 10:
15:24 -> 15:30

Never round backward into a conflict.

============================================================
PHASE 6 — SCHEDULING VALIDATION
============================================================

Do not expose conflict detection as only "overlap/no overlap".

Validation should produce structured reasons where the API contract
supports it.

Possible validation types:

- SHOWTIME_OVERLAP
- TURNAROUND_VIOLATION
- AUDITORIUM_INACTIVE
- CINEMA_INACTIVE
- MOVIE_NOT_AVAILABLE
- OUTSIDE_OPERATING_HOURS
- BOOKED_SHOWTIME_PROTECTED

Example conceptual result:

{
  "valid": false,
  "conflicts": [
    {
      "type": "TURNAROUND_VIOLATION",
      "existingShowtimeId": "...",
      "message": "Auditorium is occupied until 16:05."
    }
  ]
}

Use the project's existing error/DTO conventions. Do not invent a
parallel error-response system.

============================================================
PHASE 7 — CREATE / UPDATE
============================================================

Create validation:

Movie:
- must exist
- only COMING_SOON or NOW_SHOWING are schedulable
- ENDED/HIDDEN must be rejected

Auditorium/Cinema:
- see eligibility rules above

Time:
- end_time > start_time
- movie duration must be consistent with end_time/start_time according
  to the authoritative business/database model
- scheduling must respect turnaround

Price:
- base_price >= 0

Operating hours:
- showtime must fit within cinema opening/closing hours
- use authoritative configured hours
- do not invent auditorium-level hours for V1

Update:
- revalidate all affected scheduling constraints
- exclude the current showtime from conflict checks
- if bookings/tickets exist, protect transaction-critical fields,
  especially movie, auditorium, start_time and end_time
- follow docs/business-rules.md for all other protected fields

============================================================
PHASE 8 — CRUD / CANCEL / BOOKED PROTECTION
============================================================

If a showtime has never generated booking transactions:
- physical deletion is allowed only when existing business rules/schema
  permit it

If it has booking transactions:
- never physically delete it
- cancel using the existing CANCELLED status
- preserve historical data
- refund/payment behavior belongs to Booking/Payment

Do not invent a booking flag on showtimes.

============================================================
PHASE 9 — SEARCH / FILTER
============================================================

Admin:
- pagination
- movie
- cinema
- auditorium
- date/date range
- format
- language
- status
- keyword where consistent with sibling modules

Public:
- movie
- cinema
- date
- status
- pagination

Follow existing Specification/query/pagination conventions.

Never load the entire showtime table into memory.

Empty results must return the project's normal empty page/list.

============================================================
PHASE 10 — CALENDAR / RANGE API
============================================================

Provide a backend API suitable for a visual schedule board.

Conceptual endpoint:

GET /api/v1/admin/showtimes/calendar
    ?cinemaId=...
    &from=YYYY-MM-DD
    &to=YYYY-MM-DD

The response should be grouped or structured so the frontend can render:

Cinema
  Auditorium 01
    - Movie A 10:00-12:00
    - Movie B 12:20-14:00
  Auditorium 02
    - Movie C ...

Use the project's actual DTO/API conventions.

The calendar query must be efficient and date/range bounded.

============================================================
PHASE 11 — LEVEL 1 RULE-BASED AUTO-GENERATION
============================================================

Implement ONLY deterministic rule-based generation.

Admin provides:

- movie
- auditorium
- date or date range
- opening time
- closing time, when the API contract permits overriding configured
  cinema hours
- turnaround
- snap interval
- optional stagger offset

The generator proposes valid showtimes based on:

- movie duration
- turnaround
- snap interval
- cinema operating hours
- existing showtimes
- auditorium eligibility
- movie eligibility
- booked/sold-showtime protection

Do not overwrite or silently modify existing showtimes.

Do not implement AI, demand forecasting, revenue optimization, or
automatic movie allocation.

Generation must be deterministic and testable.

============================================================
PHASE 12 — GENERATION PREVIEW
============================================================

Do NOT make generation directly persist by default.

Provide:

POST /api/v1/admin/showtimes/generate/preview

Conceptual result:

- valid proposed slots
- rejected/conflicting slots
- reason for every rejection

Example:

08:00  VALID
10:20  VALID
12:40  VALID
15:00  CONFLICT
17:20  VALID
19:40  MAINTENANCE/CONFLICT
22:00  VALID

Then provide the actual generation operation:

POST /api/v1/admin/showtimes/generate

It must persist only accepted/valid candidates according to the
project's API contract.

If docs/api.md defines a different path/request shape, follow it.

============================================================
PHASE 13 — COPY SCHEDULE
============================================================

Implement schedule copying as a convenience operation.

Examples:

- copy one day's schedule to another date
- copy a schedule to multiple dates
- copy a weekly pattern to the following week

The operation must:

- never overwrite conflicting existing showtimes silently
- validate movie availability on the target date
- validate auditorium/cinema eligibility
- recalculate/validate time constraints
- respect turnaround and operating hours
- protect booked existing showtimes

Return a clear result showing copied, skipped, and rejected entries.

Do not create a separate template subsystem for V1.

============================================================
PHASE 14 — STAGGERED START
============================================================

Support an optional stagger offset for generated schedules.

Example:

Room 01 -> 18:00
Room 02 -> 18:05
Room 03 -> 18:10
Room 04 -> 18:15

This is a generation input, not an AI optimization feature.

Do not redesign the showtime schema solely to support a stagger value
unless the authoritative schema explicitly requires it.

============================================================
PHASE 15 — CONCURRENCY
============================================================

Conflict detection must consider concurrent create/update requests.

Do not add:
- Redis
- distributed locks
- message brokers
- microservices

Use existing transaction/optimistic-locking/database conventions.

If the current architecture cannot guarantee scheduling conflict
safety and a new database constraint or isolation strategy is required,
STOP before introducing it and report alternatives for approval.

============================================================
PHASE 16 — STATUS
============================================================

Use the existing ShowtimeStatus.

Do not invent:
- DRAFT
- COMPLETED
- FINISHED
- other statuses

unless they already exist in authoritative schema/code.

V1 does NOT introduce a separate DraftSchedule entity.

If draft/publish is already supported by the current project, follow
the existing model. Otherwise do not add it as part of this task.

============================================================
PHASE 17 — IMPLEMENTATION ORDER
============================================================

Implement incrementally:

1. Inspect and reconcile documentation/schema/code
2. Auditorium ACTIVE <-> MAINTENANCE verification/fix
3. Showtime entity/repository/query reuse or completion
4. Core admin list/search/filter
5. Create + scheduling validation
6. Update + booked-showtime protection
7. Delete/cancel
8. Public list/detail
9. Calendar/range query
10. Rule-based generation engine
11. Generation preview
12. Generation persistence
13. Copy schedule
14. Staggered generation
15. Tests
16. Full regression verification

Do not refactor unrelated modules.

============================================================
PHASE 18 — TESTING
============================================================

At minimum test:

CORE
- create success
- movie not found
- ENDED/HIDDEN movie rejected
- invalid time rejected
- auditorium not found
- MAINTENANCE auditorium rejected
- inactive/soft-deleted cinema rejected
- conflict detection
- turnaround violation
- valid next show after turnaround
- snap-forward behavior
- operating-hours violation
- search/filter
- empty result

BOOKING SAFETY
- booked showtime cannot change movie
- booked showtime cannot change auditorium
- booked showtime cannot change start/end
- booked showtime cannot be physically deleted
- cancellation preserves row/status

AUDITORIUM STATUS
- ACTIVE -> MAINTENANCE
- MAINTENANCE -> ACTIVE
- maintenance blocks new showtimes
- reactivated auditorium accepts new showtimes
- transitions do not auto-create showtimes

GENERATION
- deterministic slot generation
- movie duration respected
- turnaround respected
- snap interval respected
- existing showtimes respected
- operating hours respected
- invalid slots reported rather than silently overwritten
- preview does not persist
- generate persists only accepted candidates
- copy schedule handles conflicts
- stagger offset works

PUBLIC
- public APIs exclude ineligible cinema/auditorium
- public API exposes only appropriate/bookable statuses

AUTH
- CUSTOMER -> 403 for admin operations
- ADMIN -> allowed

REGRESSION
- existing Cinema/Movie tests remain passing
- full test suite passes

============================================================
PHASE 19 — DEFINITION OF DONE
============================================================

The module is complete only when:

- architecture conventions are preserved
- schema matches authoritative documentation
- CRUD/search/filter work
- movie duration is used correctly
- turnaround buffer is enforced
- snap interval is supported by generation
- cinema operating hours are enforced
- auditorium eligibility is enforced
- calendar/range API works
- deterministic auto-generation works
- preview does not persist
- generation persists only valid/accepted candidates
- copy schedule works safely
- staggered generation works
- conflicts are reported with useful reasons
- booked showtimes are protected
- cancellation preserves history
- ACTIVE <-> MAINTENANCE works
- public APIs exclude ineligible rooms/cinemas
- authorization works
- relevant tests pass
- existing tests pass
- no unnecessary schema/infrastructure is introduced
- no frontend is implemented
- no payment/refund logic is reimplemented
- all assumptions are reported

At completion, report:
1. changed files
2. APIs added/changed
3. database changes, if any
4. tests executed and results
5. important business decisions
6. assumptions/open issues
```

---

# 2. V1 Scope

V1 is deliberately limited to **professional rule-based scheduling**.

| Area | V1 |
|---|---|
| Showtime CRUD | ✓ |
| Search/filter | ✓ |
| Movie duration | ✓ |
| Auditorium eligibility | ✓ |
| Cinema operating hours | ✓ |
| Turnaround buffer | ✓ |
| Snap interval | ✓ |
| Scheduling validation | ✓ |
| Calendar/range query | ✓ |
| Rule-based auto-generation | ✓ |
| Generation preview | ✓ |
| Copy schedule | ✓ |
| Staggered start | ✓ |
| Booked-showtime protection | ✓ |
| Cancellation | ✓ |
| Auditorium `ACTIVE ↔ MAINTENANCE` | ✓ |

### Explicitly out of scope

- AI scheduling
- Demand/revenue forecasting
- Automatic movie allocation optimization
- Separate draft-schedule subsystem
- Maintenance-window subsystem
- Schedule audit/history
- Payment/refund implementation
- Distributed locking
- Frontend

---

# 3. Core Scheduling Model

## 3.1 Showtime duration & Canonical End Time

A showtime represents the actual movie screening:

```text
start_time ───────────── end_time
             movie duration
```

**CANONICAL RULE:** `Movie.durationMinutes` is the **single source of truth** for screening duration.
```text
endTime = startTime + Movie.durationMinutes
```
- Client-supplied `endTime` in `CreateShowtimeRequest` and `UpdateShowtimeRequest` is completely ignored or omitted.
- Backend recalculates `endTime` whenever `startTime` or `movieId` changes.

## 3.2 Auditorium occupancy

`end_time` is **not** the time at which the auditorium is immediately ready for the next show.

```text
start
  │
  ├──── Movie duration ────┤
  │                         │
10:00                     12:25 (duration = 145m)
                            │
                            ├── Turnaround ──┤
                            │
                         12:40 (turnaround = 15m)
                            │
                            ├── Snap UP ────►
                            │
                         12:45 (snap = 15m)
```

For scheduling:

```text
occupied_until = end_time + turnaround_minutes
```

A subsequent showtime must start at or after `occupied_until`, then be snapped forward (rounded UP) to the next minute boundary during generation.

## 3.3 Turnaround

Configurable **per auditorium** (`auditorium.turnaround_minutes`, default `15`).

## 3.4 Snap interval

Configurable per auditorium (`auditorium.snap_interval_minutes`, default `15`) or overridden via request (`snap_interval_minutes`):
- Round UP to the next boundary, never round backward.
- Example: `10:40` with snap 15 → `10:45`.

## 3.5 Auditorium Lifecycle

Auditorium lifecycle is modeled with 3 distinct statuses:
1. `ACTIVE`: Normal operation, eligible for scheduling.
2. `MAINTENANCE`: Temporary maintenance. Exists and manageable by Admin (`ACTIVE ↔ MAINTENANCE`). Scheduling is rejected with `AUDITORIUM_MAINTENANCE` conflict.
3. `DECOMMISSIONED`: Permanently retired/demolished. Terminal state (`DECOMMISSIONED → ACTIVE` is blocked). Historical data remains accessible for audit. Scheduling is rejected with `AUDITORIUM_DECOMMISSIONED`.

---

# 4. Scheduling Validation

Validation is broader than simple interval overlap.

Conflict Types (`SchedulingConflictType`):

- `SHOWTIME_OVERLAP`: Direct screening overlap with an existing scheduled showtime.
- `TURNAROUND_VIOLATION`: Violation of the cleaning/turnaround buffer window.
- `OUTSIDE_OPERATING_HOURS`: Screening starts before cinema `openingTime` or ends after `closingTime`.
- `AUDITORIUM_INACTIVE`: Auditorium is inactive or soft-deleted.
- `AUDITORIUM_MAINTENANCE`: Auditorium is under maintenance.
- `AUDITORIUM_DECOMMISSIONED`: Auditorium is permanently decommissioned.
- `CINEMA_INACTIVE`: Cinema is inactive or closed.
- `MOVIE_NOT_AVAILABLE`: Movie has status `ENDED`, `HIDDEN`, or invalid duration.
- `INVALID_TIME`: Invalid timestamps (`endTime <= startTime`).
- `ALREADY_EXISTS`: Exact duplicate showtime slot already exists (idempotency).

---

# 5. Auto-Generation

V1 uses deterministic rule-based generation only.

Inputs:

```text
Movie
Auditorium
Date / Date range
Opening time
Closing time
Turnaround
Snap interval
Optional stagger offset
```

Constraints:

```text
Movie duration
+
Turnaround
+
Snap
+
Existing showtimes
+
Operating hours
+
Movie availability
+
Auditorium/cinema eligibility
```

No AI, demand prediction, revenue optimization, or automatic movie
allocation is included.

---

# 6. Generation Preview

Workflow:

```text
Generate Preview
      ↓
Validate proposed slots
      ↓
Admin reviews
      ↓
Generate / Persist
```

Preview must not persist showtimes.

Conceptual result:

```text
08:00  ✓ VALID
10:20  ✓ VALID
12:40  ✓ VALID
15:00  ⚠ CONFLICT
17:20  ✓ VALID
19:40  ⚠ OUTSIDE / CONFLICT
22:00  ✓ VALID
```

The backend should return structured reasons for rejected candidates.

---

# 7. Copy Schedule

V1 supports copying existing schedules without a separate template
subsystem.

Examples:

```text
Monday → Tuesday
Monday → Tuesday, Wednesday, Thursday
This week → next week
```

Every copied showtime must be revalidated for the target date.

Never silently overwrite existing or booked showtimes.

Return copied, skipped, and rejected entries.

---

# 8. Staggered Start

Generation may apply an optional stagger offset:

```text
Room 01 → 18:00
Room 02 → 18:05
Room 03 → 18:10
Room 04 → 18:15
```

This is a generation input, not an optimization engine.

Do not add unnecessary persistent schema solely for the stagger value.

---

# 9. Calendar / Schedule Board API

Provide a bounded range query suitable for a visual schedule board.

Concept:

```http
GET /api/v1/admin/showtimes/calendar
    ?cinemaId=...
    &from=2026-08-26
    &to=2026-09-01
```

Conceptual response:

```text
Auditorium 01
├── 10:00 Movie A
├── 12:20 Movie A
├── 14:40 Movie B
└── 17:00 Movie A
```

---

# 10. Scheduling Helper Endpoints

### 10.1 Single Slot Validation
- `POST /api/v1/admin/showtimes/validate`
- Validates a single proposed slot against Movie availability, Auditorium/Cinema status, operating hours, and existing showtime/turnaround overlaps without saving to database.

### 10.2 Suggest Next Available Slot
- `POST /api/v1/admin/showtimes/suggest-next-slot`
- Proposes the earliest available slot starting at or after `requestedStartTime` satisfying duration, turnaround, snap interval, and cinema operating hours.

### 10.3 Scheduling Configuration
- `GET /api/v1/admin/cinemas/{cinemaId}/scheduling-config` (or `/api/v1/admin/showtimes/scheduling-config?cinemaId=...`)
- Returns cinema operating hours and auditorium turnaround/snap configurations.

### 10.4 Auditorium Availability Intervals
- `GET /api/v1/admin/auditoriums/{auditoriumId}/availability?date=...` (or `/api/v1/admin/showtimes/auditorium-availability?auditoriumId=...&date=...`)
- Returns detailed timeline intervals (`SHOWTIME`, `TURNAROUND`, `AVAILABLE`) across cinema operating hours.

Auditorium 02
├── 09:30 Movie C
├── 11:50 Movie C
└── ...
```

Actual DTOs must follow `docs/api.md`.

---

# 10. Auditorium Eligibility

New showtimes require:

```text
Auditorium exists
        ↓
not soft-deleted
        ↓
status = ACTIVE
        ↓
Cinema exists
        ↓
not soft-deleted
        ↓
status = ACTIVE
```

`MAINTENANCE` is temporary and reversible.

### ACTIVE → MAINTENANCE

- block new showtimes
- do not silently delete existing showtimes
- do not silently cancel existing showtimes

### MAINTENANCE → ACTIVE

- allow new showtimes again
- do not auto-create showtimes

No new auditorium status is introduced.

---

# 11. Public Showtime Rules

Public APIs must exclude showtimes that are unsuitable for customer
booking because of:

- inactive/soft-deleted cinema
- maintenance/soft-deleted auditorium
- non-bookable showtime status

Conceptual endpoints:

```http
GET /api/v1/showtimes
GET /api/v1/showtimes/{id}
GET /api/v1/showtimes/{id}/seats
```

Reuse an existing seat-map endpoint if present. Seat-map ownership
remains with Cinema/Booking according to the architecture.

---

# 12. API Surface

Exact paths, DTOs, response envelopes, validation errors, pagination,
sorting, and authorization MUST follow `docs/api.md`.

Conceptually:

### Public

```text
GET /api/v1/showtimes
GET /api/v1/showtimes/{id}
GET /api/v1/showtimes/{id}/seats
```

### Admin

```text
GET    /api/v1/admin/showtimes
POST   /api/v1/admin/showtimes
GET    /api/v1/admin/showtimes/{id}
PUT    /api/v1/admin/showtimes/{id}
DELETE /api/v1/admin/showtimes/{id}

GET    /api/v1/admin/showtimes/calendar

POST   /api/v1/admin/showtimes/generate/preview
POST   /api/v1/admin/showtimes/generate

POST   /api/v1/admin/showtimes/copy
```

These are conceptual endpoints. If `docs/api.md` defines different
paths or shapes, use those instead.

---

# 13. Database Guidance

The current schema is authoritative.

Before implementation:

1. inspect `docs/database.md`
2. inspect the actual database
3. inspect current entities
4. inspect indexes/constraints
5. identify whether V1 configuration fields already exist

Potential persistent V1 scheduling configuration:

```text
auditorium.turnaround_minutes
cinema.opening_time
cinema.closing_time
```

`Snap interval` is a generation parameter and does not necessarily need
to be persisted.

If a required persistent field is absent, identify the schema change
during planning and follow `.agents/skills/database-change/SKILL.md`.

Do not silently modify unrelated tables.

---

# 14. Concurrency

Conflict detection must consider concurrent writes.

V1 must not introduce:

- Redis
- distributed locks
- message brokers
- new services

Use existing transaction/optimistic-locking/database conventions.

If a new database constraint or isolation strategy is required but is
not already supported, stop and report alternatives before introducing
it.

---

# 15. Status

Use the existing `ShowtimeStatus`.

Do not add `DRAFT`, `COMPLETED`, `FINISHED`, or other statuses unless
already present in authoritative schema/code.

V1 does not introduce a separate `DraftSchedule` table.

---

# 16. Deferred Features

### Possible Phase 2

- Global → Cinema → Auditorium turnaround hierarchy
- Maintenance windows
- Draft → Review → Publish workflow
- Schedule change history/audit trail
- Schedule templates
- richer calendar operations

### Advanced

- demand forecasting
- occupancy prediction
- revenue optimization
- AI-assisted scheduling
- automatic movie/screen allocation

V1 is a **rule-based scheduling engine**, not an AI optimizer.

---

# 17. Definition of Done

Showtime V1 is complete when:

- CRUD works
- search/filter works
- movie duration is respected
- auditorium/cinema eligibility is enforced
- turnaround is enforced
- snap interval is supported by generation
- cinema operating hours are enforced
- conflicts return useful reasons
- calendar/range query works
- generation preview does not persist
- rule-based generation works
- generation does not overwrite existing/booked showtimes
- copy schedule works safely
- staggered generation works
- booked showtimes are protected
- cancellation preserves history
- `ACTIVE ↔ MAINTENANCE` works
- public APIs exclude ineligible rooms/cinemas
- authorization works
- relevant tests pass
- existing tests pass
- no unnecessary infrastructure/schema is introduced
- no frontend is implemented
- no payment/refund logic is implemented
- all implementation assumptions are reported

At completion, report:

1. changed files
2. APIs added/changed
3. database changes, if any
4. tests executed and results
5. important business decisions
6. assumptions/open issues

---

# 18. Related Documents

| Document | Responsibility |
|---|---|
| `AGENTS.md` | Global AI/engineering rules |
| `docs/documentation-map.md` | Documentation routing |
| `docs/architecture.md` | Architecture/layering |
| `docs/database.md` | Current schema |
| `docs/business-rules.md` | Cross-domain invariants |
| `docs/api.md` | REST contracts |
| `docs/use-cases/cinema.md` | Cinema/auditorium/seat |
| `docs/use-cases/movie.md` | Movie lifecycle |
| `.agents/rules/backend.md` | Backend engineering rules |
| `.agents/rules/database.md` | Database safety |
| `.agents/skills/implement-backend-feature/SKILL.md` | Backend workflow |
| `.agents/skills/database-change/SKILL.md` | Schema-change workflow |

Avoid duplicating full DDL, full API schemas, or unrelated domain rules.

---

# 19. Final Domain Boundary

```text
                         CineBook Showtime
                               │
              ┌────────────────┴────────────────┐
              │                                 │
          Public APIs                       Admin APIs
              │                                 │
      List / Filter / Detail       CRUD / Search / Calendar
      Movie / Cinema / Date        Generate / Preview / Copy
              │                    Conflict / Cancellation
              └────────────────┬────────────────┘
                               │
                       Scheduling Engine
                               │
                ┌──────────────┼──────────────┐
                ↓              ↓              ↓
             Movie        Auditorium       Cinema
            duration      eligibility     operating hours
                               │
                       Existing showtimes
                               │
                            Database
```

Ownership remains clear:

- **Movie** owns movie data and lifecycle.
- **Cinema** owns cinema, auditorium, seats, and seat types.
- **Showtime** owns scheduling and auditorium scheduling eligibility.
- **Booking** owns seat holds, bookings, and tickets.
- **Payment** owns payment/refund processing.
- **Pricing** owns pricing rules.

The Showtime module may read related domain data and invoke existing
queries/services, but must not duplicate their ownership.
