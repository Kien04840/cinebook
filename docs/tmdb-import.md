# CineBook TMDB Import / Seed

## 1. Purpose

This document describes **how CineBook imports movie data from TMDB** into the CineBook database via a backend-only import/seed workflow.

It complements:

- `business-rules.md` §11 — rules on curated-data protection.
- `database.md` §3.2 — target schema (`movies`, `genres`, `movies_genres`).
- `architecture.md` §8 — TMDB is a data source, not the runtime source of truth.

---

## 2. Scope Boundary (avoid duplication)

| Concern | Lives in |
|---|---|
| Whether TMDB may overwrite curated fields | `business-rules.md` §11 |
| `movies` / `genres` / `movies_genres` schema | `database.md` §3.2 |
| Field mapping, workflow mechanics, dedup strategy | **this file** |

---

## 3. High-Level Flow

```text
TMDB API
   ↓ (backend only, server-side API key)
Import / Seed workflow (admin-triggered or scripted)
   ↓
CineBook MySQL (movies, genres, movies_genres)
   ↓
CineBook Backend API
   ↓
Vue Frontend
```

- The frontend never calls TMDB directly.
- The TMDB API key stays server-side, outside source control (`AGENTS.md` §28).

---

## 4. Field Mapping (TMDB → `movies`)

| TMDB field | CineBook column | Notes |
|---|---|---|
| `id` | `tmdb_id` | UNIQUE in CineBook — the dedup key |
| `title` | `title` | |
| `original_title` | `original_title` | |
| `overview` | `overview` | |
| `runtime` | `duration_minutes` | Only present on the `/movie/{id}` detail endpoint, not on list/search results |
| `release_date` | `release_date` | format conversion to SQL `date` |
| `poster_path` | `poster_url` | must be composed with the TMDB image base URL |
| `backdrop_path` | `backdrop_url` | must be composed with the TMDB image base URL |
| `genre_ids` / `genres` | `movies_genres` (via `genres`) | mapping table, see §5 |
| — | `director` | from `/movie/{id}` with `append_to_response=credits` (crew, job = Director) |
| — | `actors` | from `/movie/{id}` with `append_to_response=credits` (top 10 cast by order) |
| — | `trailer_url` | from `/movie/{id}` with `append_to_response=videos` (YouTube Trailer, official preferred) |
| — | `country` | TMDB `production_countries` (primary country name) |
| — | `language` | TMDB `original_language` |
| — | `age_rating` | from `/movie/{id}` with `append_to_response=release_dates` (US certification, fallback `"NR"`) |
| — | `status` | CineBook-only field: new movies set based on release date (<= today: `NOW_SHOWING`, > today: `COMING_SOON`) |

---

## 5. Genre Mapping

- TMDB genres use TMDB's own fixed genre IDs, separate from CineBook's `genres.id` (UUID).
- Import maps TMDB `genres` to existing CineBook `genres` rows using `genres.tmdb_id`.
- If a TMDB genre has no corresponding CineBook `genres` row yet, it is auto-created during movie import.
- Admin can also trigger full genre sync via `POST /api/v1/admin/tmdb/genres/sync`.

---

## 6. Deduplication Strategy

`movies.tmdb_id` is UNIQUE (`uk_movies_tmdb_id`) — this is the primary dedup key.

For each TMDB movie, the import workflow should:

1. Look up an existing CineBook movie by `tmdb_id`.
2. If not found → create a new `movies` row (fresh internal UUID) + `movies_genres` links.
3. If found → update per the re-import policy (§7). Never create a duplicate row.

Movies with `tmdb_id IS NULL` (manually created, not linked to TMDB) must never be matched or overwritten by import.

---

## 7. Re-import & Curated Data Policy (Finalized)

Per developer instruction:

- **Overwritten / Synchronized on re-import**:
  - `title`, `original_title`, `overview`, `duration_minutes`
  - `director`, `actors`, `country`, `language`
  - `release_date`, `poster_url`, `backdrop_url`, `trailer_url`
  - `movies_genres` relationships (synced in a Hibernate-safe manner)
  - `age_rating` (from TMDB certification or `"NR"`)

- **Strictly Preserved (NEVER overwritten by TMDB re-import)**:
  - `id` (internal UUID)
  - `tmdb_id`
  - `status` (admin-set status such as `HIDDEN` or `ENDED` is preserved)
  - `deleted_at` (soft-deleted movie remains soft-deleted, not automatically restored)
  - `created_at`
  - `version` / `updated_at` (managed automatically by JPA/Hibernate lifecycle)

---

## 8. Import Trigger

Import is triggered server-side by administrators via REST API endpoints:

- `POST /api/v1/admin/tmdb/genres/sync` — Synchronizes all official TMDB genres.
- `POST /api/v1/admin/tmdb/movies/{tmdbId}/import` — Imports or updates a specific movie by its TMDB ID.

Both endpoints are idempotent, run entirely on the backend, and require `ROLE_ADMIN`.

---

- TMDB's API has request rate limits; bulk import throttles/paginates requests.
- Import tolerates partial failure — individual movie import errors log a warning, return a descriptive error message to the admin caller, and do not abort system execution.


---

## 10. Large / Irreversible Imports

Per `AGENTS.md` §17 and §9: large or irreversible imports require explicit instruction/approval. A bulk import that would create or modify a large number of `movies` rows is not something the AI should trigger on its own initiative.

---

## 11. Testing

- Verify import logic against a small, known TMDB sample (a handful of `tmdb_id`s) before any bulk run.
- Confirm: correct field mapping, correct genre linkage, no duplicate `tmdb_id`, curated fields untouched on a second run.

---

## 12. Related Documents

| Document | Concern |
|---|---|
| `business-rules.md` §11 | Curated-data protection rule |
| `database.md` §3.2 | `movies` / `genres` / `movies_genres` schema |
| `architecture.md` §8 | TMDB integration overview |
| `AGENTS.md` §17 | TMDB import policy (top-level rules) |

---

*This document describes the mechanics of TMDB → CineBook import. The business policy on curated-field overwriting must be confirmed by the developer before implementation treats any default as final.*
