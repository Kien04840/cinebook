# CineBook — Phase 1: User & Security Management Report

## 1. Executive Summary

Phase 1 establishes robust User & Security Management in CineBook, comprising:
1. **Phase 1.0 — Hardening & Phase 0 Regressions**:
   - Updated Spring Boot version string to `Spring Boot 4.0.8` in `AboutUsView.vue`.
   - Purged fallback pattern `st.minPrice || st.basePrice` across customer showtime browsers to strictly adhere to backend Pricing SSOT (`st.minPrice ?? st.basePrice`).
   - Audited VNPay cancel vs booking cancel lifecycle (safe decoupled design).
   - Audited pre-existing local database state in `AuditoriumNormalizationLiveIntegrationTest` without destructive data alterations.
2. **Phase 1.1 — Admin User Management**:
   - Added `PUT /api/v1/admin/users/{id}` API for comprehensive user editing (`fullName`, `phone`, `status`, `roles`).
   - Enforced 3 critical security invariants:
     - **Self-Disable Guard**: An admin cannot lock or disable their own account.
     - **Self-Demotion Guard**: An admin cannot remove the `ADMIN` role from their own account.
     - **Last-Active-Admin Guard**: Cannot disable or demote the sole active admin in the platform.
     - **Pessimistic Concurrency Locking**: Serialized concurrent admin edits on the `ADMIN` role record in MySQL (`@Lock(LockModeType.PESSIMISTIC_WRITE)`).
     - **Password Immunity**: Admin user edit endpoint strictly ignores and prevents password modifications.
   - Frontend: Integrated user edit modal in `AdminUsersView.vue` with self-protection guards and role badges.
3. **Phase 1.2 — Email Verification (Non-blocking V1)**:
   - Created MySQL table `email_verification_tokens` via DDL migration `V1_1__add_email_verification_tokens.sql`.
   - Built entity `EmailVerificationToken` and repository `EmailVerificationTokenRepository`.
   - Implemented token issuance (24-hour TTL, `SecureRandom` URL-safe Base64), single-use deletion, idempotent verification, and 60-second resend rate limiting with anti-enumeration protection.
   - Public REST endpoints: `POST /api/v1/auth/verify-email` and `POST /api/v1/auth/resend-verification` in `AuthController`, permitted in `SecurityConfig`.
   - Implemented email dispatch via `EmailService.sendVerificationEmail`.
   - Frontend: Dedicated `VerifyEmailView.vue` (`/verify-email`) handling loading/success/error/resend states, unverified warning banner in `ProfileView.vue` with 60-second cooldown timer, and bilingual i18n support (`vi.ts`, `en.ts`).

---

## 2. Completed Scope & Delivered Enhancements

### A. Backend Architecture & Persistence
- **Flyway Migration Script**: `src/main/resources/db/migration/V1_1__add_email_verification_tokens.sql`
- **JPA Entities**:
  - `EmailVerificationToken.java` with `@ManyToOne User user`, `token` (unique), `expiresAt`, and indexes on `user_id` and `expires_at`.
  - Added `@OneToMany(mappedBy = "user") List<EmailVerificationToken> emailVerificationTokens` in `User.java`.
- **JPA Repositories**:
  - `EmailVerificationTokenRepository.java` (`findByToken`, `findFirstByUserIdOrderByCreatedAtDesc`, `deleteByUserId`, `deleteByExpiresAtBefore`).
  - `RoleRepository.java`: Added `findByNameWithLock("ADMIN")` with `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
  - `UserRepository.java`: Added `countActiveAdmins()`.
- **Service Layer**:
  - `UserServiceImpl.java`: Implemented `adminUpdateUser(...)` and updated `updateUserStatus(...)` with self-guards, last-active-admin pessimistic lock, and synchronized `user_roles`.
  - `AuthServiceImpl.java`: Added token generation and email dispatch on registration (non-blocking), implemented `verifyEmail(token)` and `resendVerificationEmail(email)` with 60s rate limit.
  - `EmailServiceImpl.java`: Added `sendVerificationEmail(toEmail, customerName, verificationToken)`.
- **Presentation Layer**:
  - `AdminUserController.java`: Added `PUT /api/v1/admin/users/{id}`.
  - `AuthController.java`: Added `POST /api/v1/auth/verify-email` and `POST /api/v1/auth/resend-verification`.
  - `SecurityConfig.java`: Permitted public access to verify-email and resend-verification endpoints.

### B. Frontend Enhancements
- **Admin Portal (`AdminUsersView.vue`)**:
  - Edit user button and modal with form inputs for full name, phone number, status select, and role checkboxes.
  - Self-guards: Disabled status selector and Admin role checkbox if editing the currently authenticated administrator.
- **Customer Portal**:
  - `VerifyEmailView.vue` (`/verify-email`): Supports URL query token verification, states (loading, success, error/expired), and an inline resend form.
  - `ProfileView.vue` (`/profile`): Added an amber warning banner if email is not verified, 60s countdown timer on resend button, and Email Status badge in user overview card.
- **Services & Routing**:
  - `user.service.ts`: Added `adminUpdateUser(userId, payload)`.
  - `auth.service.ts`: Added `verifyEmail(token)` and `resendVerification(email)`.
  - `router/index.ts`: Registered `/verify-email`.
- **Localization**:
  - Complete parity in `frontend/src/locales/vi.ts` and `en.ts` for all new UI text and validation errors.

---

## 3. Verification & Quality Gates

| Verification Gate | Command | Result |
|---|---|---|
| **Admin User Unit Tests** | `.\mvnw.cmd test -Dtest=UserServiceTest,AdminUserControllerTest` | **19/19 PASSED (100%)** |
| **Auth & Email Unit Tests** | `.\mvnw.cmd test -Dtest=AuthServiceTest,AuthControllerTest` | **27/27 PASSED (100%)** |
| **Spring Boot Context Load** | `.\mvnw.cmd test -Dtest=CinebookApplicationTests` | **1/1 PASSED (100%)** |
| **Full Backend Test Suite** | `.\mvnw.cmd test` | **601/603 PASSED** (2 failures in `AuditoriumNormalizationLiveIntegrationTest` preserved due to local DB data) |
| **Frontend TypeScript Check & Build** | `npm run build` (`vue-tsc --noEmit && vite build`) | **PASS (0 Errors, built in 6.11s)** |

---

## 4. Modified & Created Files Summary

### Backend
- `[NEW]` `src/main/resources/db/migration/V1_1__add_email_verification_tokens.sql`
- `[NEW]` `src/main/java/com/cinebook/entity/EmailVerificationToken.java`
- `[NEW]` `src/main/java/com/cinebook/repository/EmailVerificationTokenRepository.java`
- `[NEW]` `src/main/java/com/cinebook/dto/request/AdminUpdateUserRequest.java`
- `[NEW]` `src/main/java/com/cinebook/dto/request/VerifyEmailRequest.java`
- `[NEW]` `src/main/java/com/cinebook/dto/request/ResendVerificationRequest.java`
- `[MODIFY]` `src/main/java/com/cinebook/entity/User.java`
- `[MODIFY]` `src/main/java/com/cinebook/repository/UserRepository.java`
- `[MODIFY]` `src/main/java/com/cinebook/repository/RoleRepository.java`
- `[MODIFY]` `src/main/java/com/cinebook/service/UserService.java`
- `[MODIFY]` `src/main/java/com/cinebook/service/impl/UserServiceImpl.java`
- `[MODIFY]` `src/main/java/com/cinebook/service/EmailService.java`
- `[MODIFY]` `src/main/java/com/cinebook/service/impl/EmailServiceImpl.java`
- `[MODIFY]` `src/main/java/com/cinebook/service/AuthService.java`
- `[MODIFY]` `src/main/java/com/cinebook/service/impl/AuthServiceImpl.java`
- `[MODIFY]` `src/main/java/com/cinebook/controller/AdminUserController.java`
- `[MODIFY]` `src/main/java/com/cinebook/controller/AuthController.java`
- `[MODIFY]` `src/main/java/com/cinebook/config/SecurityConfig.java`
- `[MODIFY]` `src/test/java/com/cinebook/service/UserServiceTest.java`
- `[MODIFY]` `src/test/java/com/cinebook/controller/AdminUserControllerTest.java`
- `[MODIFY]` `src/test/java/com/cinebook/service/AuthServiceTest.java`
- `[MODIFY]` `src/test/java/com/cinebook/controller/AuthControllerTest.java`

### Frontend
- `[NEW]` `frontend/src/views/auth/VerifyEmailView.vue`
- `[MODIFY]` `frontend/src/views/admin/AdminUsersView.vue`
- `[MODIFY]` `frontend/src/views/customer/ProfileView.vue`
- `[MODIFY]` `frontend/src/views/customer/ShowtimesView.vue`
- `[MODIFY]` `frontend/src/components/booking/ShowtimeBrowser.vue`
- `[MODIFY]` `frontend/src/views/static/AboutUsView.vue`
- `[MODIFY]` `frontend/src/services/user.service.ts`
- `[MODIFY]` `frontend/src/services/auth.service.ts`
- `[MODIFY]` `frontend/src/router/index.ts`
- `[MODIFY]` `frontend/src/locales/vi.ts`
- `[MODIFY]` `frontend/src/locales/en.ts`

### Documentation
- `[MODIFY]` `docs/api.md`
- `[MODIFY]` `docs/database.md`
- `[MODIFY]` `docs/business-rules.md`
- `[MODIFY]` `docs/use-cases/administration.md`
- `[MODIFY]` `docs/use-cases/authentication.md`
- `[MODIFY]` `docs/documentation-map.md`
- `[NEW]` `docs/implementation-reports/phase-1-user-security-management.md`

