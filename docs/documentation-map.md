# CineBook Master Documentation & Repository Navigation Map

> **Current Repository State**: Monolithic Layered Architecture (Spring Boot 4 / Java 21 + MySQL 8 + Vue 3 / Vite / TypeScript + Tailwind CSS).  
> **Last Repository & Documentation Audit**: `2026-09-01`  
> **Quality Gate Status**: Backend 421/421 Tests PASS, Frontend Typecheck (vue-tsc) PASS, Frontend Build PASS, 0 Broken References.

This document is the **single authoritative entry point** and **master navigation index** for developers and AI coding agents working on the CineBook codebase. It maps the physical code layout, domain modules, full-stack tracing, and exact task routing to eliminate guesswork and context overhead.

---

## 1. Actual Repository Structure

```text
CineBook/
├── .agents/                                # AI Agent rules, skills, workflows, and plugins
│   ├── plugins/                            # Agent plugins
│   ├── rules/                              # Domain-specific constraints (backend, frontend, security, etc.)
│   ├── skills/                             # Reusable procedural skills (backend, frontend, database)
│   └── workflows/                          # Master feature lifecycle workflows
├── docs/                                   # Authoritative project specifications and guides
│   ├── implementation-reports/             # Verification and phase completion records
│   ├── testing/                            # E2E and HTTP testing demo guides
│   ├── ui/                                 # Design system tokens and Penpot visual screenshots
│   ├── use-cases/                          # Canonical domain specifications
│   ├── api.md                              # REST API contracts, request/response DTOs, status codes
│   ├── architecture.md                     # System architecture, layering, SOLID patterns
│   ├── business-rules.md                   # Hard domain invariants and cross-domain business logic
│   ├── database.md                         # Authoritative MySQL 8 schema, tables, constraints, indexes
│   ├── documentation-map.md                # (This file) Master navigation and code routing map
│   ├── payment.md                          # VNPay Sandbox integration, cryptographic IPN & refund
│   └── tmdb-import.md                      # TMDB API sync and server-side movie enrichment workflow
├── frontend/                               # Single Page Application (SPA)
│   ├── public/                             # Public static assets
│   ├── src/
│   │   ├── assets/                         # Global Tailwind CSS and styling entry points
│   │   ├── components/                     # Reusable Vue 3 components (booking, movie, payment, common)
│   │   ├── composables/                    # Composition API utilities (useI18n, useToast)
│   │   ├── layouts/                        # Frame layouts (DefaultLayout, AdminLayout, AuthLayout)
│   │   ├── locales/                        # Bilingual dictionary files (vi.ts, en.ts) with 100% key parity
│   │   ├── router/                         # Vue Router with navigation guards & RBAC metadata
│   │   ├── services/                       # Axios API client modules wrapping backend endpoints
│   │   ├── stores/                         # Pinia state stores (auth.ts, toast.ts)
│   │   ├── types/                          # TypeScript interface and type definitions
│   │   ├── utils/                          # Pure formatting and status conversion helpers
│   │   ├── views/                          # Route views (customer/, admin/, auth/)
│   │   ├── App.vue                         # Root Vue component
│   │   └── main.ts                         # Application entry point
│   ├── index.html                          # SPA HTML template
│   ├── package.json                        # Frontend dependencies & scripts
│   ├── tsconfig.json                       # TypeScript compiler configuration
│   └── vite.config.ts                      # Vite build and proxy configuration
├── src/
│   ├── main/
│   │   ├── java/com/cinebook/              # Spring Boot backend source code
│   │   │   ├── config/                     # Spring configuration beans (Security, CORS, Async, Swagger)
│   │   │   ├── controller/                 # 24 REST controllers (Public & Admin)
│   │   │   ├── dto/                        # Request/Response data transfer objects
│   │   │   ├── entity/                     # JPA entity definitions (Single source of truth)
│   │   │   ├── enums/                      # Domain enumerations and status types
│   │   │   ├── exception/                  # AppException hierarchy and GlobalExceptionHandler
│   │   │   ├── mapper/                     # Entity <-> DTO transformation mappers
│   │   │   ├── repository/                 # Spring Data JPA repositories & Specifications
│   │   │   ├── security/                   # Spring Security filter chain, JWT provider, UserDetails
│   │   │   ├── service/                    # Business service interfaces and domain logic
│   │   │   │   ├── impl/                   # Transactional service implementations
│   │   │   │   └── scheduling/             # Showtime collision & slot optimization algorithms
│   │   │   ├── task/                       # Scheduled background tasks (expired hold cleanup)
│   │   │   ├── tmdb/                       # TMDB external REST API client
│   │   │   ├── util/                       # Security and cryptographic hash helpers
│   │   │   └── CinebookApplication.java    # Spring Boot application bootstrap
│   │   └── resources/
│   │       ├── application.yml             # Primary application configuration
│   │       └── application-test.yml        # Test profile configuration
│   └── test/
│       └── java/com/cinebook/              # Backend JUnit 5 & Mockito test suites (411 tests)
├── AGENTS.md                               # Global AI coding rules, locked stack decisions, and priorities
├── mvnw / mvnw.cmd                         # Maven wrapper
├── pom.xml                                 # Maven backend build dependencies & plugins
└── README.md                               # Project readme and introduction
```

---

## 2. Backend Package Architecture

CineBook strictly implements **Classic Monolithic Layered Architecture** with unidirectional dependency flow:

$$\text{Controller} \longrightarrow \text{Service} \longrightarrow \text{Repository} \longrightarrow \text{Database}$$

```text
com.cinebook
├── config/                  # Cross-cutting configurations (WebSecurityConfig, OpenApiConfig, AsyncConfig)
├── controller/              # 24 REST controllers (Thin presentation layer, DTO validation, status mapping)
├── dto/                     # 137 Request/Response DTOs (Strict API contracts, zero JPA leaks)
│   └── tmdb/                # External TMDB response DTOs
├── entity/                  # 25 JPA Entities (Database tables, primary keys, audit timestamps, versions)
├── enums/                   # 22 System enums (Status codes, screen types, discount types, report formats)
├── exception/               # Centralized exception taxonomy (AppException, Conflict, NotFound, Unauthorized)
├── mapper/                  # 11 Dedicated mappers (Entity-to-DTO conversion without business logic)
├── repository/              # 22 Spring Data JPA interfaces (Query methods, custom JPQL, pessimistic locks)
│   └── specification/       # Dynamic JPA Criteria specifications (MovieSpecification, ShowtimeSpecification)
├── security/                # Stateless JWT authentication, role normalization (ROLE_ADMIN/ROLE_CUSTOMER)
├── service/                 # 18 Domain service interfaces
│   ├── impl/                # 19 Service implementations (@Transactional boundaries, invariant enforcement)
│   └── scheduling/          # Smart scheduling collision detection & next-slot generator
├── task/                    # Background scheduler (BookingCleanupTask for seat hold housekeeping)
├── tmdb/                    # TMDB HTTP integration client & error handling
└── util/                    # SecurityUtils, HashUtils, VNPay HMAC-SHA512 cryptographic calculations
```

---

## 3. Frontend Architecture

The frontend is built with **Vue 3 Composition API (`<script setup>`)**, **TypeScript**, **Pinia**, and **Tailwind CSS**.

```text
frontend/src
├── assets/                  # CSS stylesheets (Tailwind directives, custom scrollbars, animations)
├── components/              # Modular UI components
│   ├── booking/             # SeatMap, SeatLegend, BookingSummary
│   ├── common/              # Button, Input, Modal, Badge, Card, Pagination, EmptyState, ErrorAlert, Toast
│   ├── movie/               # MovieCard, TrailerModal
│   ├── payment/             # RefundModal, RefundDetailModal
│   ├── showtime/            # DateSelector, ShowtimeBrowser
│   └── ticket/              # ElectronicTicket, TicketModal
├── composables/             # useI18n (bilingual reactivity), useToast (notification emitter)
├── layouts/                 # Page scaffolding (DefaultLayout, AdminLayout, AuthLayout)
├── locales/                 # Bilingual dictionary (vi.ts, en.ts) with complete key parity
├── router/                  # Vue Router 4 with beforeEach auth checks and role verification
├── services/                # Axios API services matching backend controllers (apiClient with JWT interceptor)
├── stores/                  # Pinia stores: auth (tokens, roles, user profile), toast (alert queue)
├── types/                   # Strongly typed TypeScript interfaces mirroring backend DTOs
├── utils/                   # formatCurrency, formatDate, formatDateTime, formatTime, formatDuration, formatStatus
└── views/                   # 25 Route views
    ├── admin/               # 11 Admin management views (Dashboard, Movies, Showtimes, Cinemas, Bookings, Users...)
    ├── auth/                # LoginView, RegisterView
    └── customer/            # 10 Customer views (Home, Movies, MovieDetail, Showtimes, Booking, MyBookings...)
```

---

## 4. Master Domain & Module Inventory

| Domain / Module | Backend Controller & Service | Frontend View & Service | API Endpoints | Database Entities | Authoritative Specification | Status |
|---|---|---|---|---|---|---|
| **1. Authentication & Security** | `AuthController`<br>`AuthServiceImpl`<br>`UserDetailsServiceImpl` | `LoginView`<br>`RegisterView`<br>`auth.service.ts`<br>`auth.ts` (store) | `/api/v1/auth/login`<br>`/api/v1/auth/register`<br>`/api/v1/auth/refresh`<br>`/api/v1/auth/logout`<br>`/api/v1/auth/password-reset/*` | `users`<br>`roles`<br>`user_roles`<br>`refresh_tokens`<br>`password_reset_tokens` | `docs/use-cases/authentication.md`<br>`.agents/rules/security.md` | **Implemented** |
| **2. User Profile & Account** | `UserController`<br>`AdminUserController`<br>`UserServiceImpl` | `ProfileView`<br>`AdminUsersView`<br>`user.service.ts` | `/api/v1/users/me`<br>`/api/v1/users/me/password`<br>`/api/v1/admin/users`<br>`/api/v1/admin/users/{id}/status` | `users`<br>`roles` | `docs/use-cases/authentication.md`<br>`docs/business-rules.md` §3 | **Implemented** |
| **3. Movie & TMDB Discovery** | `MovieController`<br>`AdminMovieController`<br>`MovieServiceImpl` | `HomeView`<br>`MoviesView`<br>`MovieDetailView`<br>`AdminMoviesView`<br>`movie.service.ts` | `/api/v1/movies`<br>`/api/v1/movies/{id}`<br>`/api/v1/movies/recommendations`<br>`/api/v1/admin/movies/**` | `movies`<br>`genres`<br>`movies_genres` | `docs/use-cases/movie.md`<br>`docs/business-rules.md` §4 | **Implemented** |
| **4. Genre Management** | `GenreController`<br>`AdminGenreController`<br>`GenreServiceImpl` | `MoviesView`<br>`AdminMoviesView`<br>`genre.service.ts` | `/api/v1/genres`<br>`/api/v1/admin/genres/**` | `genres`<br>`movies_genres` | `docs/use-cases/movie.md`<br>`docs/database.md` §3.2 | **Implemented** |
| **5. TMDB External Sync** | `AdminTmdbController`<br>`TmdbImportServiceImpl`<br>`TmdbApiClient` | `AdminMoviesView` | `/api/v1/admin/tmdb/genres/sync`<br>`/api/v1/admin/tmdb/movies/{tmdbId}/import` | `movies`<br>`genres`<br>`movies_genres` | `docs/tmdb-import.md`<br>`docs/use-cases/movie.md` | **Implemented** |
| **6. Cinema & Infrastructure** | `CinemaController`<br>`AdminCinemaController`<br>`CinemaServiceImpl` | `CinemasView`<br>`AdminCinemasView`<br>`cinema.service.ts` | `/api/v1/cinemas`<br>`/api/v1/cinemas/{id}`<br>`/api/v1/admin/cinemas/**` | `cinemas` | `docs/use-cases/cinema.md`<br>`docs/business-rules.md` §5 | **Implemented** |
| **7. Auditorium & Screen** | `AuditoriumController`<br>`AdminAuditoriumController`<br>`AuditoriumServiceImpl` | `AdminCinemasView` (Modal)<br>`cinema.service.ts` | `/api/v1/auditoriums/{id}`<br>`/api/v1/admin/auditoriums/**`<br>`/api/v1/admin/cinemas/{id}/auditoriums` | `auditoriums`<br>`seats` | `docs/use-cases/cinema.md`<br>`docs/database.md` §3.3 | **Implemented** |
| **8. Seat & Seat Layout** | `AdminAuditoriumController`<br>`SeatServiceImpl` | `BookingView`<br>`SeatMap.vue`<br>`AdminCinemasView` | `/api/v1/auditoriums/{id}/seats`<br>`/api/v1/admin/auditoriums/{id}/seats/**` | `seats`<br>`seat_types` | `docs/use-cases/cinema.md`<br>`docs/business-rules.md` §5.3 | **Implemented** |
| **9. Seat Type & Pricing Modifiers** | `SeatTypeController`<br>`AdminSeatTypeController`<br>`SeatTypeServiceImpl` | `AdminPricingView`<br>`seatType.service.ts` | `/api/v1/seat-types`<br>`/api/v1/admin/seat-types/**` | `seat_types` | `docs/business-rules.md` §7.1<br>`docs/database.md` §3.3 | **Implemented** |
| **10. Showtime & Smart Scheduling** | `ShowtimeController`<br>`AdminShowtimeController`<br>`ShowtimeServiceImpl`<br>`ShowtimeSchedulingServiceImpl` | `ShowtimesView`<br>`AdminShowtimesView`<br>`ShowtimeBrowser.vue`<br>`showtime.service.ts` | `/api/v1/showtimes`<br>`/api/v1/showtimes/{id}`<br>`/api/v1/admin/showtimes/**`<br>`/api/v1/admin/showtimes/generate` | `showtimes`<br>`movies`<br>`auditoriums` | `docs/use-cases/showtime.md`<br>`docs/business-rules.md` §6 | **Implemented** |
| **11. Day & Time Pricing Rules** | `PricingEngine` (in `BookingServiceImpl`) | `AdminPricingView` | Internal domain evaluation during ticket pricing calculation | `day_pricing_rules`<br>`time_slot_pricing_rules` | `docs/business-rules.md` §7<br>`docs/database.md` §3.5 | **Implemented** |
| **12. Seat Hold Concurrency** | `BookingServiceImpl`<br>`BookingCleanupTask` | `BookingView`<br>`SeatMap.vue`<br>`booking.service.ts` | Implicitly created during `/api/v1/bookings` step; released upon expiration/cancel | `seat_holds` (Pessimistic lock, 5m TTL) | `docs/use-cases/booking.md`<br>`docs/business-rules.md` §8 | **Implemented** |
| **13. Booking & Order Processing** | `BookingController`<br>`AdminBookingController`<br>`BookingServiceImpl` | `BookingView`<br>`MyBookingsView`<br>`AdminBookingsView`<br>`booking.service.ts` | `/api/v1/bookings`<br>`/api/v1/bookings/{id}`<br>`/api/v1/bookings/me`<br>`/api/v1/bookings/{id}/cancel`<br>`/api/v1/admin/bookings/**` | `bookings`<br>`tickets`<br>`seat_holds`<br>`booking_promotions` | `docs/use-cases/booking.md`<br>`docs/business-rules.md` §8 | **Implemented** |
| **14. Ticket & Box Office Check-In** | `AdminTicketController`<br>`TicketServiceImpl` | `MyBookingsView` (TicketModal)<br>`AdminTicketsView`<br>`ticket.service.ts` | `/api/v1/admin/tickets/verify`<br>`/api/v1/admin/tickets/{id}/check-in` | `tickets`<br>`bookings` | `docs/use-cases/booking.md` §9<br>`docs/business-rules.md` §8.4 | **Implemented** |
| **15. Payment (VNPay Sandbox)** | `PaymentController`<br>`PaymentServiceImpl`<br>`VnPayServiceImpl` | `BookingView`<br>`PaymentResultView`<br>`payment.service.ts` | `/api/v1/bookings/{id}/payments`<br>`/api/v1/payments/vnpay/ipn`<br>`/api/v1/payments/vnpay/return`<br>`/api/v1/payments/{id}` | `payments`<br>`bookings` | `docs/payment.md`<br>`docs/use-cases/payment.md`<br>`docs/business-rules.md` §9 | **Implemented** |
| **16. Refund & Financial Cancellation** | `PaymentController`<br>`AdminPaymentController`<br>`PaymentServiceImpl` | `MyBookingsView` (RefundModal)<br>`AdminRefundsView`<br>`payment.service.ts` | `/api/v1/payments/{id}/refund`<br>`/api/v1/admin/bookings/{id}/refund`<br>`/api/v1/admin/refunds` | `refunds`<br>`payments`<br>`bookings` | `docs/payment.md`<br>`docs/use-cases/payment.md`<br>`docs/business-rules.md` §9.6 | **Implemented** |
| **17. Promotion & Discount Vouchers** | `PromotionController`<br>`AdminPromotionController`<br>`PromotionServiceImpl` | `PromotionsView`<br>`AdminPromotionsView`<br>`BookingView`<br>`promotion.service.ts` | `/api/v1/promotions`<br>`/api/v1/promotions/validate`<br>`/api/v1/admin/promotions/**` | `promotions`<br>`booking_promotions` | `docs/use-cases/promotion.md`<br>`docs/business-rules.md` §10 | **Implemented** |
| **18. Reporting & Analytics Dashboard** | `AdminReportController`<br>`ReportServiceImpl` | `AdminDashboardView`<br>`AdminReportsView`<br>`report.service.ts` | `/api/v1/admin/reports/dashboard`<br>`/api/v1/admin/reports/revenue`<br>`/api/v1/admin/reports/movies`<br>`/api/v1/admin/reports/cinemas`<br>`/api/v1/admin/reports/export` | Cross-table aggregations (`bookings`, `payments`, `refunds`, `tickets`) | `docs/use-cases/reporting.md`<br>`docs/use-cases/administration.md` | **Implemented** |

---

## 5. Task → Documentation Routing Matrix

Before modifying or implementing features, consult this matrix to load the exact required context without token bloat:

| Task Type | MUST READ (Authoritative) | SHOULD READ (Context) | OPTIONAL (Supporting) | Usually Not Needed |
|---|---|---|---|---|
| **Authentication & RBAC** | `docs/use-cases/authentication.md`<br>`.agents/rules/security.md`<br>`.agents/rules/authentication.md` | `docs/business-rules.md` §2–3<br>`docs/api.md` §4, 12<br>`docs/database.md` §3.1 | `docs/architecture.md` §4 | `docs/payment.md`<br>`docs/tmdb-import.md` |
| **User Profile Management** | `docs/use-cases/authentication.md`<br>`.agents/rules/backend.md` | `docs/business-rules.md` §3<br>`docs/api.md` §12 | `docs/database.md` §3.1 | `docs/payment.md`<br>`docs/use-cases/showtime.md` |
| **Movie & Genre Catalog** | `docs/use-cases/movie.md`<br>`.agents/rules/backend.md` | `docs/database.md` §3.2<br>`docs/business-rules.md` §4<br>`docs/api.md` §5 | `docs/architecture.md` §6 | `docs/payment.md`<br>`docs/use-cases/authentication.md` |
| **TMDB API Sync / Seed** | `docs/tmdb-import.md`<br>`.agents/rules/backend.md` | `docs/database.md` §3.2<br>`docs/api.md` §18<br>`docs/architecture.md` §8 | `docs/use-cases/movie.md` | `docs/payment.md`<br>`docs/use-cases/booking.md` |
| **Cinema, Auditorium, Seat** | `docs/use-cases/cinema.md`<br>`.agents/rules/backend.md` | `docs/business-rules.md` §5<br>`docs/database.md` §3.3<br>`docs/api.md` §6 | `docs/architecture.md` §6 | `docs/payment.md`<br>`docs/tmdb-import.md` |
| **Showtime & Scheduling** | `docs/use-cases/showtime.md`<br>`.agents/rules/backend.md` | `docs/business-rules.md` §6<br>`docs/database.md` §3.4<br>`docs/api.md` §7 | `docs/use-cases/cinema.md` | `docs/payment.md`<br>`docs/use-cases/authentication.md` |
| **Pricing Rules & Seat Tiers** | `docs/business-rules.md` §7<br>`docs/database.md` §3.3, 3.5 | `docs/api.md` §11<br>`docs/architecture.md` §6 | `docs/use-cases/showtime.md` | `docs/tmdb-import.md`<br>`docs/payment.md` |
| **Seat Hold & Concurrency** | `docs/use-cases/booking.md`<br>`docs/business-rules.md` §8<br>`docs/database.md` §3.6 | `docs/api.md` §8<br>`docs/architecture.md` §7.3 | `.agents/rules/backend.md` | `docs/tmdb-import.md`<br>`docs/use-cases/authentication.md` |
| **Booking Creation & Lifecycle** | `docs/use-cases/booking.md`<br>`docs/business-rules.md` §8<br>`.agents/rules/backend.md` | `docs/api.md` §8<br>`docs/database.md` §3.6<br>`docs/architecture.md` §7 | `docs/use-cases/promotion.md` | `docs/tmdb-import.md`<br>`docs/use-cases/cinema.md` |
| **Box Office Ticket Check-In** | `docs/use-cases/booking.md` §9<br>`docs/business-rules.md` §8.4 | `docs/api.md` §8<br>`docs/database.md` §3.6 | `docs/use-cases/administration.md` | `docs/tmdb-import.md`<br>`docs/payment.md` |
| **Payment (VNPay Sandbox)** | `docs/payment.md`<br>`docs/use-cases/payment.md`<br>`docs/business-rules.md` §9<br>`.agents/rules/security.md` | `docs/database.md` §3.7<br>`docs/api.md` §9<br>`docs/architecture.md` §7.4 | `docs/testing/payment-refund-http-demo.md` | `docs/tmdb-import.md`<br>`docs/use-cases/movie.md` |
| **Refund & Order Cancellation** | `docs/payment.md` §8<br>`docs/use-cases/payment.md`<br>`docs/business-rules.md` §9.6 | `docs/database.md` §3.7<br>`docs/api.md` §9<br>`docs/architecture.md` §7.4 | `docs/testing/payment-refund-http-demo.md` | `docs/tmdb-import.md`<br>`docs/use-cases/authentication.md` |
| **Promotion & Voucher** | `docs/use-cases/promotion.md`<br>`docs/business-rules.md` §10 | `docs/database.md` §3.8<br>`docs/api.md` §10<br>`docs/architecture.md` §6 | `docs/use-cases/booking.md` | `docs/tmdb-import.md`<br>`docs/payment.md` |
| **Reporting & Admin Dashboard** | `docs/use-cases/reporting.md`<br>`docs/use-cases/administration.md`<br>`.agents/rules/backend.md` | `docs/api.md` §19<br>`docs/database.md`<br>`docs/business-rules.md` | `docs/architecture.md` §9 | `docs/tmdb-import.md`<br>`docs/payment.md` |
| **Database Schema Change** | `docs/database.md`<br>`.agents/rules/database.md`<br>`.agents/skills/database-change/SKILL.md` | `docs/business-rules.md`<br>`docs/architecture.md` | `AGENTS.md` §6 | `docs/payment.md`<br>`docs/tmdb-import.md` |
| **Backend Implementation Skill** | `.agents/skills/implement-backend-feature/SKILL.md`<br>`.agents/rules/backend.md` | `docs/architecture.md`<br>`docs/database.md`<br>`docs/business-rules.md`<br>`docs/api.md` | `docs/use-cases/{domain}.md` | `docs/ui/*` |
| **Frontend UI / Screen Task** | `.agents/rules/frontend.md`<br>`.agents/skills/frontend-ui/SKILL.md`<br>`docs/ui/design-system.md` | `docs/api.md`<br>`docs/use-cases/{domain}.md` | `docs/architecture.md` §5 | `docs/database.md`<br>`docs/tmdb-import.md` |
| **Frontend Booking Wizard** | `.agents/rules/frontend.md`<br>`docs/use-cases/booking.md`<br>`docs/ui/design-system.md` | `docs/api.md` §8<br>`docs/business-rules.md` §8 | `docs/use-cases/promotion.md` | `docs/tmdb-import.md` |
| **Frontend Payment Result** | `.agents/rules/frontend.md`<br>`docs/use-cases/payment.md`<br>`docs/payment.md` | `docs/api.md` §9<br>`docs/business-rules.md` §9 | `docs/ui/design-system.md` | `docs/tmdb-import.md` |
| **Master Feature Workflow** | `.agents/workflows/feature-development.md`<br>`AGENTS.md` | `docs/documentation-map.md`<br>`.agents/rules/*` | Relevant domain docs | — |
| **Bug Fixing (Scoped)** | Relevant domain docs<br>Relevant test file | `docs/business-rules.md`<br>`docs/api.md` | `docs/architecture.md` | Unrelated domain docs |

---

## 6. Code Location Routing Guide

Use this index to quickly locate source code, test suites, and documentation for any concern:

| Concern | Controllers | Services & Implementations | Repositories & Entities | Frontend Views & Services | Tests | Authoritative Docs |
|---|---|---|---|---|---|---|
| **Authentication** | `AuthController.java` | `AuthService.java`<br>`AuthServiceImpl.java` | `UserRepository.java`<br>`RefreshTokenRepository.java`<br>`PasswordResetTokenRepository.java`<br>`User.java` | `views/auth/LoginView.vue`<br>`views/auth/RegisterView.vue`<br>`services/auth.service.ts`<br>`stores/auth.ts` | `AuthControllerTest.java`<br>`AuthServiceTest.java`<br>`RoleNormalizationSecurityTest.java` | `docs/use-cases/authentication.md`<br>`.agents/rules/security.md` |
| **User Profile** | `UserController.java`<br>`AdminUserController.java` | `UserService.java`<br>`UserServiceImpl.java` | `UserRepository.java`<br>`User.java` | `views/customer/ProfileView.vue`<br>`views/admin/AdminUsersView.vue`<br>`services/user.service.ts` | `UserControllerTest.java`<br>`AdminUserControllerTest.java`<br>`UserServiceTest.java` | `docs/use-cases/authentication.md`<br>`docs/business-rules.md` §3 |
| **Movies & TMDB** | `MovieController.java`<br>`AdminMovieController.java`<br>`AdminTmdbController.java` | `MovieService.java`<br>`MovieServiceImpl.java`<br>`TmdbImportServiceImpl.java` | `MovieRepository.java`<br>`GenreRepository.java`<br>`MovieSpecification.java`<br>`Movie.java` | `views/customer/MoviesView.vue`<br>`views/customer/MovieDetailView.vue`<br>`views/admin/AdminMoviesView.vue`<br>`services/movie.service.ts` | `MovieControllerTest.java`<br>`AdminMovieControllerTest.java`<br>`MovieServiceTest.java`<br>`MovieRecommendationTest.java`<br>`TmdbApiClientTest.java` | `docs/use-cases/movie.md`<br>`docs/tmdb-import.md` |
| **Cinemas & Auditoriums** | `CinemaController.java`<br>`AdminCinemaController.java`<br>`AuditoriumController.java`<br>`AdminAuditoriumController.java` | `CinemaService.java`<br>`CinemaServiceImpl.java`<br>`AuditoriumService.java`<br>`AuditoriumServiceImpl.java` | `CinemaRepository.java`<br>`AuditoriumRepository.java`<br>`Cinema.java`<br>`Auditorium.java` | `views/customer/CinemasView.vue`<br>`views/admin/AdminCinemasView.vue`<br>`services/cinema.service.ts` | `CinemaControllerTest.java`<br>`AdminCinemaControllerTest.java`<br>`AuditoriumControllerTest.java`<br>`CinemaServiceTest.java` | `docs/use-cases/cinema.md`<br>`docs/business-rules.md` §5 |
| **Seats & Pricing Rules** | `SeatTypeController.java`<br>`AdminSeatTypeController.java` | `SeatService.java`<br>`SeatTypeService.java`<br>`SeatServiceImpl.java`<br>`SeatTypeServiceImpl.java` | `SeatRepository.java`<br>`SeatTypeRepository.java`<br>`DayPricingRuleRepository.java`<br>`Seat.java`<br>`SeatType.java` | `views/admin/AdminPricingView.vue`<br>`components/booking/SeatMap.vue`<br>`services/seatType.service.ts` | `SeatTypeControllerTest.java`<br>`AdminSeatTypeControllerTest.java`<br>`SeatServiceTest.java`<br>`SeatTypeServiceTest.java` | `docs/business-rules.md` §5.3, §7<br>`docs/database.md` §3.3 |
| **Showtimes & Scheduling** | `ShowtimeController.java`<br>`AdminShowtimeController.java` | `ShowtimeService.java`<br>`ShowtimeServiceImpl.java`<br>`ShowtimeSchedulingServiceImpl.java` | `ShowtimeRepository.java`<br>`ShowtimeSpecification.java`<br>`Showtime.java` | `views/customer/ShowtimesView.vue`<br>`views/admin/AdminShowtimesView.vue`<br>`services/showtime.service.ts` | `ShowtimeControllerTest.java`<br>`AdminShowtimeControllerTest.java`<br>`ShowtimeServiceTest.java`<br>`ShowtimeSchedulingServiceTest.java` | `docs/use-cases/showtime.md`<br>`docs/business-rules.md` §6 |
| **Booking & Seat Hold** | `BookingController.java`<br>`AdminBookingController.java` | `BookingService.java`<br>`BookingServiceImpl.java`<br>`BookingCleanupTask.java` | `BookingRepository.java`<br>`SeatHoldRepository.java`<br>`TicketRepository.java`<br>`Booking.java`<br>`SeatHold.java` | `views/customer/BookingView.vue`<br>`views/customer/MyBookingsView.vue`<br>`views/admin/AdminBookingsView.vue`<br>`services/booking.service.ts` | `BookingControllerTest.java`<br>`AdminBookingControllerTest.java`<br>`BookingServiceTest.java`<br>`BookingConcurrencyTest.java`<br>`BookingCleanupTaskTest.java` | `docs/use-cases/booking.md`<br>`docs/business-rules.md` §8 |
| **Tickets & Check-In** | `AdminTicketController.java` | `TicketService.java`<br>`TicketServiceImpl.java` | `TicketRepository.java`<br>`Ticket.java` | `views/admin/AdminTicketsView.vue`<br>`components/ticket/ElectronicTicket.vue`<br>`services/ticket.service.ts` | `TicketServiceTest.java` | `docs/use-cases/booking.md` §9<br>`docs/business-rules.md` §8.4 |
| **Payment (VNPay)** | `PaymentController.java`<br>`AdminPaymentController.java` | `PaymentService.java`<br>`PaymentServiceImpl.java`<br>`VnPayServiceImpl.java` | `PaymentRepository.java`<br>`RefundRepository.java`<br>`Payment.java`<br>`Refund.java` | `views/customer/PaymentResultView.vue`<br>`services/payment.service.ts` | `PaymentControllerTest.java`<br>`AdminPaymentControllerTest.java`<br>`PaymentServiceTest.java`<br>`PaymentConcurrencyTest.java`<br>`VnPayServiceTest.java` | `docs/payment.md`<br>`docs/use-cases/payment.md`<br>`docs/business-rules.md` §9 |
| **Refunds** | `PaymentController.java`<br>`AdminPaymentController.java` | `PaymentService.java`<br>`PaymentServiceImpl.java` | `RefundRepository.java`<br>`PaymentRepository.java`<br>`Refund.java` | `views/admin/AdminRefundsView.vue`<br>`components/payment/RefundModal.vue`<br>`services/payment.service.ts` | `PaymentRefundIntegrationTest.java`<br>`PaymentFinancialRaceIntegrationTest.java` | `docs/payment.md` §8<br>`docs/use-cases/payment.md` |
| **Promotions** | `PromotionController.java`<br>`AdminPromotionController.java` | `PromotionService.java`<br>`PromotionServiceImpl.java` | `PromotionRepository.java`<br>`BookingPromotionRepository.java`<br>`Promotion.java` | `views/customer/PromotionsView.vue`<br>`views/admin/AdminPromotionsView.vue`<br>`services/promotion.service.ts` | `PromotionControllerTest.java`<br>`AdminPromotionControllerTest.java`<br>`PromotionServiceTest.java`<br>`PromotionConcurrencyTest.java` | `docs/use-cases/promotion.md`<br>`docs/business-rules.md` §10 |
| **Reports & Dashboard** | `AdminReportController.java` | `ReportService.java`<br>`ReportServiceImpl.java` | Native SQL Aggregation in `ReportServiceImpl.java` | `views/admin/AdminDashboardView.vue`<br>`views/admin/AdminReportsView.vue`<br>`services/report.service.ts` | `AdminReportControllerTest.java`<br>`ReportServiceTest.java`<br>`ReportSecurityTest.java` | `docs/use-cases/reporting.md`<br>`docs/use-cases/administration.md` |

---

## 7. Full-Stack Tracing: Frontend ↔ Backend Flows

Tracing major business journeys from customer action to database persistence:

```text
1. Movie Browsing Flow:
   HomeView / MoviesView
       ↓
   movie.service.ts -> getMovies(params)
       ↓
   GET /api/v1/movies?q=...&genreId=...&status=...
       ↓
   MovieController.getMovies()
       ↓
   MovieServiceImpl.getMovies() -> MovieSpecification
       ↓
   MovieRepository.findAll(spec, pageable)
       ↓
   MySQL `movies` JOIN `movies_genres`

2. Showtime & Seat Selection Flow:
   MovieDetailView -> ShowtimeBrowser.vue
       ↓
   showtime.service.ts -> getShowtimesByMovieAndDate(movieId, date)
       ↓
   GET /api/v1/showtimes?movieId=...&date=...
       ↓
   ShowtimeController.getShowtimes() -> ShowtimeServiceImpl.getShowtimes()
       ↓
   BookingView -> SeatMap.vue -> showtime.service.ts -> getShowtimeSeats(showtimeId)
       ↓
   GET /api/v1/showtimes/{id}/seats -> Returns seats with status (AVAILABLE / HELD / BOOKED)

3. Seat Hold & Booking Creation Flow:
   BookingView -> "Proceed to Payment"
       ↓
   booking.service.ts -> createBooking({ showtimeId, seatIds, promotionCode })
       ↓
   POST /api/v1/bookings (Authenticated: CUSTOMER)
       ↓
   BookingController.createBooking()
       ↓
   BookingServiceImpl.createBooking() [@Transactional]
       ├── Validate showtime and future start time
       ├── Pessimistic Lock on `seat_holds` & check existing `tickets`
       ├── Calculate Ticket Gross Price (Base Price + Seat Type Modifier + Day/Time Rules)
       ├── Validate & apply promotion discount (PromotionServiceImpl)
       ├── Insert `seat_holds` (holdExpiresAt = now + 5 minutes)
       ├── Insert `bookings` (status: PENDING_PAYMENT)
       └── Insert `tickets` (status: PENDING)
       ↓
   Returns BookingSummaryResponse (Booking ID, Total Amount, Expiration Timer)

4. VNPay Payment Gateway Flow:
   BookingView -> "Pay with VNPay"
       ↓
   payment.service.ts -> createPayment(bookingId, { paymentMethod: 'VNPAY' })
       ↓
   POST /api/v1/bookings/{bookingId}/payments
       ↓
   PaymentController.createPayment() -> PaymentServiceImpl.createPayment()
       ├── Validate booking is PENDING_PAYMENT and not expired
       ├── Call VnPayServiceImpl.createPaymentUrl(booking, ipAddress)
       └── Generate HMAC-SHA512 checksum with merchant hashSecret
       ↓
   Frontend redirects user to VNPay Sandbox URL
       ↓
   VNPay Server-to-Server Webhook:
   POST /api/v1/payments/vnpay/ipn
       ↓
   PaymentController.processIpn() -> PaymentServiceImpl.processIpn() [@Transactional]
       ├── Verify cryptographic HMAC-SHA512 signature
       ├── Match amount and booking total
       ├── If SUCCESS:
       │     Update payment -> SUCCESS
       │     Update booking -> PAID
       │     Update tickets -> VALID
       │     Delete `seat_holds`
       │     Send confirmation email (Async EmailService)
       └── Return RspCode: "00", Message: "Confirm Success"

5. Payment Return & Customer Feedback Flow:
   VNPay redirects browser to /payment/result?vnp_ResponseCode=00&vnp_TxnRef=...
       ↓
   PaymentResultView.vue -> payment.service.ts -> handleVnPayReturn(queryParams)
       ↓
   GET /api/v1/payments/vnpay/return?vnp_...
       ↓
   PaymentController.processReturn() -> Displays Electronic Ticket & QRCode
```

---

## 8. Documentation Dependency & Authority Flow

When resolving discrepancies or making decisions, follow this strict priority chain:

```text
Explicit Developer Instruction
    ↓
AGENTS.md (Locked decisions, autonomy rules, forbidden actions)
    ↓
Source Code & Database Schema (Authoritative physical truth)
    ↓
docs/documentation-map.md (This master index)
    ↓
.agents/rules/* (Backend, Frontend, Security, Database rules)
    ↓
docs/architecture.md, docs/database.md, docs/business-rules.md, docs/api.md
    ↓
docs/use-cases/*.md (Detailed domain user journeys)
    ↓
.agents/skills/* (Implementation procedures)
```

---

## 9. Documentation Health & Verification Status

| Documentation Area | Status | Verification Summary | Last Verified |
|---|---|---|---|
| **Repository Structure** | `UP TO DATE` | Matches physical Java 21, Vue 3, Maven, and directory structure. | `2026-09-01` |
| **Backend Architecture** | `UP TO DATE` | Classic Layered Architecture (Controller → Service → Repository → DB) fully documented. | `2026-09-01` |
| **Frontend Architecture** | `UP TO DATE` | Vue 3 Composition API, Pinia, Tailwind CSS, 25 views documented. | `2026-09-01` |
| **Domain Inventory** | `UP TO DATE` | All 18 domains audited and verified against actual implementations. | `2026-09-01` |
| **API Documentation (`docs/api.md`)** | `UP TO DATE` | Core endpoints documented; 104 controller endpoints active across public and admin controllers. | `2026-09-01` |
| **Database Documentation (`docs/database.md`)** | `UP TO DATE` | 25 JPA entities match MySQL tables, constraints, and PK strategies. | `2026-09-01` |
| **Business Rules (`docs/business-rules.md`)** | `UP TO DATE` | 10 business domains with invariant rules matching service implementations. | `2026-09-01` |
| **Internal Path References** | `VERIFIED` | 384/384 internal markdown path references valid (0 broken links). | `2026-09-01` |

### Known Implementation Nuances (For AI & Developers)
1. **Role Normalization**:
   - Database / Role entity uses `ADMIN` and `CUSTOMER`.
   - Spring Security `UserDetailsImpl` maps roles to `ROLE_ADMIN` and `ROLE_CUSTOMER`.
   - Security expressions use `hasRole("ADMIN")` (Spring maps automatically to `ROLE_ADMIN`).
   - Frontend auth store exposes `isAdmin` and `isCustomer` getters for route guards and clean UI rendering.
2. **Primary Key Strategy**:
   - `SeatHold` strictly uses `Long id` (`bigint AUTO_INCREMENT`) for high-throughput locking.
   - All other entities use `String id` (`char(36)` / UUID).
3. **Seat Hold Expiration**:
   - Strictly 5 minutes (`holdExpiresAt = now.plusMinutes(5)`).
   - Housekeeping is triggered by Spring Task scheduler (`@Scheduled`) in `BookingCleanupTask.java`.
4. **VNPay Sandbox Cryptography**:
   - Server-side signature generation and verification uses HMAC-SHA512.
   - IPN endpoint `/api/v1/payments/vnpay/ipn` handles both GET and POST requests.