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
| — | `director` | from `/movie/{id}/credits` (crew, job = Director) — not on the base movie object |
| — | `actors` | from `/movie/{id}/credits` (cast) — not on the base movie object |
| — | `trailer_url` | from `/movie/{id}/videos` — not on the base movie object |
| — | `country` | TMDB `production_countries` |
| — | `language` | TMDB `original_language` |
| — | `age_rating` | TMDB has no single field equivalent to CineBook's `age_rating` |
| — | `status` | CineBook-only field, not sourced from TMDB |

```text
TODO / DECISION REQUIRED:
- Exact TMDB endpoints used (search/discover for listing vs /movie/{id} + /credits + /videos
  for detail) and how many extra calls per movie are acceptable.
- How age_rating is derived (TMDB's release_dates endpoint has per-country certification data,
  or this field may be manually curated instead of imported).
- Image base URL / size variant used for poster_url / backdrop_url.
- trailer_url selection rule when multiple videos exist (e.g. first YouTube "Trailer" type).
```

---

## 5. Genre Mapping

- TMDB genres use TMDB's own fixed genre IDs, separate from CineBook's `genres.id` (UUID).
- Import must map TMDB `genre_ids` to existing CineBook `genres` rows (by name, or a maintained TMDB-id → CineBook-id lookup) — it must not create duplicate genre rows for the same concept.
- If a TMDB genre has no corresponding CineBook `genres` row yet, a decision is needed on whether to auto-create it.

```text
TODO / DECISION REQUIRED:
- Auto-create missing genres during import, or require them to be pre-seeded/curated manually?
```

---

## 6. Deduplication Strategy

`movies.tmdb_id` is UNIQUE (`uk_movies_tmdb_id`) — this is the primary dedup key.

For each TMDB movie, the import workflow should:

1. Look up an existing CineBook movie by `tmdb_id`.
2. If not found → create a new `movies` row + `movies_genres` links.
3. If found → update per the curated-field policy (§7). Never create a duplicate row.

Movies with `tmdb_id IS NULL` (manually created, not linked to TMDB) must never be matched or overwritten by import.

---

## 7. Curated Data Protection

Per `business-rules.md` §11: **do not unexpectedly overwrite manually curated data.**

```text
TODO / DECISION REQUIRED (tracked in business-rules.md §11 — do not resolve independently here):
- Which fields are considered "curated" and protected from re-import overwrite
  (e.g. a poster_url or description manually replaced by an admin).
- Conflict resolution policy: skip existing non-null curated fields vs always overwrite vs
  require admin confirmation.
```

Until this is decided, import logic must default to the **safest** option: do not overwrite an existing non-null field on update; only fill nulls / add new movies. This default may be revised once the developer confirms the policy — do not silently change it without approval (`AGENTS.md` §9).

---

## 8. Import Trigger

```text
TODO / DECISION REQUIRED:
- Is import triggered manually by an admin action (e.g. POST /api/v1/admin/movies/import),
  a one-off seed script, or a scheduled job?
- Is it single-movie import (by TMDB id) or bulk import (by a discover/search query)?
```

Whichever mechanism is chosen, it must:

- Run entirely server-side.
- Respect existing database constraints (`uk_movies_tmdb_id`, `NOT NULL` columns).
- Be safe to re-run (idempotent), per the dedup strategy in §6.

---

## 9. Rate Limiting & Reliability

- TMDB's API has request rate limits; bulk import should throttle/paginate requests rather than firing all calls at once.
- Import should tolerate partial failure — one movie failing to import must not abort the whole batch — and should report which items failed.

```text
TODO / DECISION REQUIRED:
- Retry policy for transient TMDB failures.
- Logging/reporting format for import results (success/skip/fail counts).
```

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
