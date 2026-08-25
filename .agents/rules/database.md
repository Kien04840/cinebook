---
name: cinebook-database
description: Mandatory database safety and persistence rules for CineBook.
---

# CineBook Database Rules

Canonical Schema Reference: `docs/database.md`

## 1. Database Safety & Integrity

- **MySQL is the single source of truth**. Redis (if used) is strictly an optional cache.
- **Forbidden without explicit developer instruction**:
  - `DROP TABLE`, `TRUNCATE TABLE`, or bulk deletions.
  - Database resets or destructive schema alterations.
  - Removing existing constraints, foreign keys, or indexes.
  - Changing primary key strategy (UUID for entities, `bigint` auto-increment for `seat_holds`).

## 2. Persistence Conventions

- Existing JPA entities and table mappings in `docs/database.md` are authoritative.
- Do not duplicate entities, repositories, or representations of the same table.
- Use `@Version` for optimistic locking on mutable aggregates (`Movie`, `Booking`, `Showtime`, etc.).
- When managing M:N relationships (e.g. `MovieGenre`):
  - Load related entities from the current persistence context to avoid detached entity conflicts.
  - Use `orphanRemoval = true` safely to synchronize collections.

## 3. Schema Change Procedure

- If a schema gap is discovered:
  1. Identify the exact requirement gap.
  2. Propose the minimal non-destructive addition.
  3. Obtain explicit developer approval before any irreversible migration.