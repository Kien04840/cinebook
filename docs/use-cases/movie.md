# CineBook Movie Management Rule

## 1. Purpose

This document defines the business rules, scope, authorization, API
behavior, and implementation guidance for the Movie Management domain of
CineBook.

It covers both:

-   Public/customer-facing movie discovery.
-   Administrator movie and genre management.

The movie domain must follow the existing CineBook layered architecture
and reuse the existing database/entity model.

Detailed API contracts belong to `docs/api.md`.

Detailed database structure belongs to `docs/database.md`.

General engineering rules belong to `AGENTS.md`.

------------------------------------------------------------------------

## 2. Scope

The Movie domain covers:

-   Listing currently showing and upcoming movies.
-   Searching movies by title.
-   Filtering movies by genre.
-   Filtering movies by release/showing status or relevant release date
    information.
-   Viewing movie details.
-   Managing movies as an administrator.
-   Managing genres as an administrator.
-   Soft-deleting / stopping display of movies.
-   Linking movies to genres through the existing movie-genre
    relationship.
-   Supporting the public movie information needed by the
    customer-facing frontend.

The domain does not own cinema, auditorium, seat, or showtime
management.

Those are separate domains.

Movie detail pages may present cinema/showtime information to customers,
but the authoritative showtime data belongs to the Showtime domain and
must use the existing Showtime API/relationships rather than duplicating
showtime data inside Movie.

------------------------------------------------------------------------

## 3. Existing Domain Model

The existing database model contains:

``` text
movies
genres
movie_genres
```

The implementation must inspect and reuse the existing entities,
repositories, relationships, constraints, indexes, timestamps,
soft-delete fields, and versioning fields.

Do not create duplicate movie or genre entities.

Do not silently change the database schema to implement this module.

The existing database schema is authoritative unless the developer
explicitly approves a schema change.

------------------------------------------------------------------------

## 4. Movie Information

A movie may contain information required by the customer-facing movie
detail page, according to the existing database schema and API contract.

Conceptually, movie information includes:

``` text
id
title
description / synopsis
duration
release date
age rating
director
cast
trailer
poster / image references
status
created_at
updated_at
deleted_at
version
```

The exact fields must be taken from the existing `movies` entity and
`docs/database.md`.

Do not invent a new field merely because it is useful for the UI.

If the database already has a corresponding field, reuse it.

------------------------------------------------------------------------

## 5. Movie Status and Visibility

The system distinguishes movies that are:

-   Currently showing.
-   Upcoming.
-   No longer displayed / inactive according to the existing movie
    status model.

The exact enum/value names must follow the existing entity/database
implementation.

A movie that is soft-deleted or otherwise inactive must not appear in
normal public movie listings.

Administrative APIs may expose appropriate inactive/soft-deleted records
when needed for management.

Do not hard-delete a movie merely to stop it from being displayed.

Soft-delete is preferred because movie records may be referenced by
historical showtimes, bookings, tickets, and other records.

------------------------------------------------------------------------

## 6. Public Movie Access

Movie discovery is public.

The following operations are available without authentication:

``` http
GET /api/v1/movies
GET /api/v1/movies/{id}
```

Customers and administrators may also use these public endpoints.

The public movie API must return only information intended for public
consumption.

It must not expose internal database/security information.

------------------------------------------------------------------------

## 7. List Movies

### Endpoint

``` http
GET /api/v1/movies
```

### Authentication

``` text
Public
```

### Supported query concepts

The API contract supports query parameters such as:

``` text
status
genre
q
page
size
sort
```

These parameters should support the following user-facing scenarios:

``` text
View currently showing movies
View upcoming movies
Search by movie title
Filter by genre
Paginate results
Sort results
```

The exact parameter names, types, default values, and response shape
must remain consistent with `docs/api.md`.

### Response

The response is a paginated list of movie summaries.

A summary may contain fields such as:

``` text
id
title
poster
duration
ageRating
status
...
```

Only fields defined by the API contract should be returned.

------------------------------------------------------------------------

## 8. Search Movies

Customers can search for movies by title.

Conceptually:

``` http
GET /api/v1/movies?q=...
```

Search behavior should:

-   Be case-insensitive according to the database/project convention.
-   Return only publicly visible movies.
-   Work together with pagination.
-   Preserve the existing API response structure.

Do not introduce a search engine or external search infrastructure for
this requirement.

A database-backed query is sufficient for the current project.

------------------------------------------------------------------------

## 9. Filter Movies by Genre

Customers can filter movies by genre.

Conceptually:

``` http
GET /api/v1/movies?genre=...
```

The implementation must use the existing:

``` text
movies
    ↓
movie_genres
    ↓
genres
```

relationship.

Do not store duplicated genre names directly in the movie record if the
existing schema already normalizes them through `movie_genres`.

------------------------------------------------------------------------

## 10. Movie Detail

### Endpoint

``` http
GET /api/v1/movies/{id}
```

### Authentication

``` text
Public
```

### Response

The detail response provides the full public movie information and
genres according to `docs/api.md`.

The customer-facing UI may use this information to display:

``` text
Movie title
Synopsis
Duration
Genres
Director
Cast
Age rating
Trailer
Poster / images
Release information
Status
```

Only fields actually present in the existing schema/API contract should
be used.

------------------------------------------------------------------------

## 11. Cinema and Showtime Information

The movie detail page is expected to help the customer find:

``` text
Cinemas showing the movie
Dates
Showtimes
```

However, cinema and showtime data do not belong to the Movie domain.

The architecture should therefore keep the responsibility separated:

``` text
Movie
  ↓
Movie information

Cinema
  ↓
Cinema information

Showtime
  ↓
Movie + Cinema + Auditorium + Date/Time
```

The customer-facing frontend may combine the public Movie and
Showtime/Cinema APIs to build the complete movie detail experience.

Do not duplicate showtime records inside Movie.

Do not introduce a second showtime representation merely to simplify the
Movie API.

The exact Showtime API is defined separately in `docs/api.md` and will
be implemented in the Showtime module.

------------------------------------------------------------------------

## 12. Administrator Authorization

Movie management is an administrator-only operation.

Required role:

``` text
ADMIN
```

Administrative endpoints use:

``` text
/api/v1/admin/**
```

A `CUSTOMER` must not be able to create, update, or delete movies or
genres.

Expected authorization behavior:

``` text
No authentication
    ↓
401 Unauthorized

Authenticated CUSTOMER
    ↓
403 Forbidden

Authenticated ADMIN
    ↓
Allowed
```

The frontend must not be relied upon for authorization.

The backend must enforce the ADMIN role.

------------------------------------------------------------------------

## 13. Admin Movie Management

Administrators can:

``` text
Create movie
Update movie
Stop displaying / soft-delete movie
```

### Create

``` http
POST /api/v1/admin/movies
```

### Update

``` http
PUT /api/v1/admin/movies/{id}
```

### Delete / stop display

``` http
DELETE /api/v1/admin/movies/{id}
```

Authentication:

``` text
ADMIN required
```

Soft-delete is preferred over hard deletion.

The exact request and response DTOs belong to `docs/api.md` and must
match the existing project conventions.

------------------------------------------------------------------------

## 14. Create Movie Rules

When creating a movie, the backend must:

1.  Validate required fields.
2.  Validate field formats and lengths.
3.  Validate release/date information according to the existing business
    rules.
4.  Validate age-rating/status values against the existing model.
5.  Validate referenced genres.
6.  Create the movie using the existing entity.
7.  Create the required movie-genre relationships.
8.  Return a safe response DTO.
9.  Preserve database constraints.

The client must not be allowed to assign protected fields such as:

``` text
id
created_at
updated_at
deleted_at
version
```

unless the existing API explicitly requires them.

------------------------------------------------------------------------

## 15. Update Movie Rules

When updating a movie, the backend must:

1.  Verify that the movie exists and is eligible for update.
2.  Validate all supplied fields.
3.  Validate genre references.
4.  Update the existing movie entity.
5.  Synchronize the movie-genre relationship correctly.
6.  Preserve database invariants.
7.  Respect optimistic versioning if it is already implemented by the
    entity/schema.
8.  Return the updated safe response.

Do not silently replace unrelated data.

Do not bypass database constraints.

------------------------------------------------------------------------

## 16. Delete / Stop Display

Movie deletion should normally be implemented as soft-delete or an
equivalent inactive state.

The system must avoid physically deleting movies that may already be
referenced by:

``` text
showtimes
bookings
tickets
historical records
```

After a movie is no longer publicly visible:

``` text
GET /api/v1/movies
```

must not return it as a normal public movie.

The exact lifecycle behavior must follow the existing
database/business-rule definitions.

------------------------------------------------------------------------

## 17. Genre Management

Administrators can manage movie genres.

The existing database model is:

``` text
genres
    ↓
movie_genres
    ↓
movies
```

Genre management must reuse the existing `genres` entity/table.

The intended administrative operations are:

``` text
Create genre
Update genre
Delete/deactivate genre according to business rules
```

Genre operations must be ADMIN-only.

Before deleting/deactivating a genre, the implementation must respect
existing movie-genre references and database constraints.

Do not leave invalid `movie_genres` relationships.

------------------------------------------------------------------------

## 18. Movie-Genre Relationship

A movie can belong to multiple genres.

A genre can belong to multiple movies.

Therefore:

``` text
Movie N : N Genre
```

through:

``` text
movie_genres
```

The implementation must not duplicate genre information inside `movies`.

When updating a movie's genres:

``` text
Existing genre links
        ↓
Compare with requested links
        ↓
Add/remove relationships
        ↓
Persist consistent final state
```

The exact persistence strategy should follow the existing JPA mappings
and project conventions.

------------------------------------------------------------------------

## 19. TMDB Integration Boundary

CineBook has a separate TMDB import document:

``` text
docs/tmdb-import.md
```

TMDB import is not required to implement the basic Movie CRUD/API module
unless explicitly requested.

If TMDB import is implemented later:

-   Reuse `movies.tmdb_id` if present.
-   Do not create duplicate movies for the same TMDB ID.
-   Do not duplicate CineBook genres because of TMDB genre IDs.
-   Do not unexpectedly overwrite manually curated movie data.
-   Respect the existing TMDB import rules.

Do not add TMDB synchronization merely to complete ordinary Movie CRUD.

------------------------------------------------------------------------

## 20. API Authorization Matrix

  Operation                              Public   CUSTOMER   ADMIN
  ------------------------------------- -------- ---------- -------
  List public movies                       ✓         ✓         ✓
  View public movie detail                 ✓         ✓         ✓
  Search movies                            ✓         ✓         ✓
  Filter movies by genre                   ✓         ✓         ✓
  Create movie                            ---       ---        ✓
  Update movie                            ---       ---        ✓
  Soft-delete / stop displaying movie     ---       ---        ✓
  Manage genres                           ---       ---        ✓

Public movie operations should not require JWT.

Administrative operations must require a valid JWT with the `ADMIN`
role.

------------------------------------------------------------------------

## 21. Error Handling

Use the existing CineBook REST error envelope and exception hierarchy.

Typical cases include:

``` text
400 Bad Request
    Invalid movie data
    Invalid genre data
    Invalid query parameters

401 Unauthorized
    Missing/invalid authentication for an admin endpoint

403 Forbidden
    Authenticated user is not ADMIN

404 Not Found
    Movie does not exist
    Genre does not exist

409 Conflict
    Business/database uniqueness conflict
    Invalid relationship state where applicable
```

Do not expose raw database exceptions to API clients.

------------------------------------------------------------------------

## 22. Backend Layering

Follow the existing layered architecture:

``` text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Supporting components may include:

``` text
Entity
DTO
Mapper
Validation
Exception
Security
```

Controllers should remain thin.

Movie business rules belong in the Service layer.

Repositories should handle persistence/query concerns.

Do not introduce additional architectural layers without a concrete
need.

------------------------------------------------------------------------

## 23. Frontend Scope

The Movie module should include the first meaningful Admin frontend
slice.

### Admin

Implement:

``` text
Admin Movie List
Admin Movie Create
Admin Movie Edit
Admin Movie Delete/Deactivate
Genre management
```

The frontend must use the backend APIs rather than directly accessing
the database.

Admin pages must require an authenticated ADMIN user.

The frontend should handle at minimum:

``` text
Loading
Success
Validation errors
401
403
404
409
Empty states
```

### Customer

The customer-facing movie API should be implemented as part of this
module, even if the complete customer frontend is implemented later.

The public frontend can then consume:

``` text
GET /api/v1/movies
GET /api/v1/movies/{id}
```

This allows the customer movie browsing experience to be built
incrementally without coupling it to the Admin UI.

------------------------------------------------------------------------

## 24. Testing Requirements

### Unit tests

At minimum test:

``` text
Create movie
Update movie
Movie not found
Invalid movie data
Genre validation
Movie-genre relationship update
Soft-delete / inactive movie
Public movie listing
Movie search
Movie genre filtering
Movie detail
```

### Authorization tests

Verify:

``` text
Public GET /movies → allowed
Public GET /movies/{id} → allowed

CUSTOMER POST /admin/movies → 403
CUSTOMER PUT /admin/movies/{id} → 403
CUSTOMER DELETE /admin/movies/{id} → 403

ADMIN POST /admin/movies → allowed
ADMIN PUT /admin/movies/{id} → allowed
ADMIN DELETE /admin/movies/{id} → allowed
```

### API verification

Use Postman to verify:

``` text
Public movie APIs
Admin movie APIs
Admin genre APIs
Validation
404
401
403
409 where applicable
```

### Frontend verification

Verify the Admin UI in the browser:

``` text
Admin login
    ↓
Movie list
    ↓
Create movie
    ↓
Movie appears in list
    ↓
Edit movie
    ↓
Updated data appears
    ↓
Deactivate/delete movie
    ↓
Movie is no longer shown publicly
```

------------------------------------------------------------------------

## 25. Implementation Order

Implement the module incrementally.

### Phase 1 --- Inspect

Inspect:

``` text
Movie entity
Genre entity
MovieGenre mapping/entity
Movie repository
Genre repository
Existing DTO/mapper conventions
Existing exception handling
Existing security configuration
Existing frontend structure
docs/database.md
docs/api.md
docs/business-rules.md
docs/tmdb-import.md
```

### Phase 2 --- Public Backend

Implement and test:

``` text
GET /api/v1/movies
GET /api/v1/movies/{id}
```

including:

``` text
Search
Genre filter
Status filter
Pagination
Sorting
```

as supported by the existing API contract.

### Phase 3 --- Admin Backend

Implement and test:

``` text
POST   /api/v1/admin/movies
PUT    /api/v1/admin/movies/{id}
DELETE /api/v1/admin/movies/{id}
```

and the required genre-management APIs.

### Phase 4 --- Authorization

Verify:

``` text
CUSTOMER → 403
ADMIN → allowed
```

for administrative operations.

### Phase 5 --- Admin Frontend

Implement:

``` text
Movie list
Movie create form
Movie edit form
Movie delete/deactivate
Genre management
```

### Phase 6 --- Integration Verification

Verify the complete flow:

``` text
Admin login
    ↓
Admin Movie Management UI
    ↓
Movie API
    ↓
Service
    ↓
Database
    ↓
Public Movie API
    ↓
Customer-facing data
```

------------------------------------------------------------------------

## 26. Definition of Done

The Movie module is complete when:

-   Public movie listing works.
-   Public movie detail works.
-   Movie search works.
-   Genre filtering works.
-   Status filtering works where defined by the API.
-   Pagination/sorting work according to the API contract.
-   Admin can create movies.
-   Admin can update movies.
-   Admin can stop displaying / soft-delete movies.
-   Admin can manage genres.
-   CUSTOMER cannot access administrative movie/genre operations.
-   ADMIN can access administrative movie/genre operations.
-   Movie-genre relationships remain consistent.
-   Existing database constraints are respected.
-   Relevant backend tests pass.
-   Backend builds successfully.
-   Admin frontend builds successfully.
-   Admin CRUD has been browser-verified.
-   No unrelated architecture or database changes were introduced.
-   No secrets were introduced.
-   Final diff is reviewed.

------------------------------------------------------------------------

## 27. Related Documents

  Document                   Responsibility
  -------------------------- ---------------------------------------
  `AGENTS.md`                AI behavior and engineering rules
  `docs/architecture.md`     Overall architecture
  `docs/database.md`         Movie, genre, and relationship schema
  `docs/business-rules.md`   Cross-domain business rules
  `docs/api.md`              Movie public/admin API contracts
  `docs/tmdb-import.md`      TMDB import rules
  `docs/frontend.md`         Frontend conventions
  `docs/use-cases/*.md`      End-to-end use cases

Avoid duplicating detailed API schemas or database DDL here.

------------------------------------------------------------------------

## 28. Final Principle

The Movie module must provide one coherent movie domain for both sides
of the system:

``` text
                    CineBook Movie
                         │
             ┌───────────┴───────────┐
             ↓                       ↓
       Public Customer          Admin Management
             │                       │
      List / Search / Detail    Create / Update
      Genre / Status Filter     Genre Management
             │                       │
             └───────────┬───────────┘
                         ↓
                  Shared Movie Data
                         ↓
                    Database
```

Build the public read APIs and administrative CRUD as one domain, while
keeping Cinema and Showtime responsibilities in their own modules.
