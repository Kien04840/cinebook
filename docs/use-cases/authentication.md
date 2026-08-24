# CineBook Authentication & Authorization

## 1. Purpose

This document defines the authentication, user-account, authorization, and role-based access-control rules for CineBook.

It is an implementation reference for the backend AI agent.

Detailed API contracts belong to `docs/api.md`.
Detailed database structure belongs to `docs/database.md`.
General engineering rules belong to `AGENTS.md`.

This document describes **what authentication and authorization must do**. It should not prescribe unnecessary implementation details when the existing codebase already provides an established solution.

---

## 2. Scope

The Identity/Authentication domain covers:

- User registration.
- Login.
- JWT-based authentication.
- Access-token validation.
- Refresh-token management.
- Logout / refresh-token revocation.
- Password reset through email.
- Password change for authenticated users.
- Viewing and updating the authenticated user's profile.
- User roles and role-based authorization.
- Customer access to personal bookings, tickets, and payment information.
- Administrator access to administrative APIs.
- Administrative user management.
- Protection of sensitive user information.

The current system has two primary roles:

```text
CUSTOMER
ADMIN
```

Role names must remain consistent throughout the database, Java security configuration, JWT claims, and API authorization rules.

---

## 3. Authentication Model

CineBook uses:

```text
Spring Security
        +
JWT access token
        +
Database-backed refresh token
```

High-level flow:

```text
Register
   ↓
User account created
   ↓
Login
   ↓
Credentials verified
   ↓
JWT access token issued
   +
Refresh token created/stored
   ↓
Client sends:
Authorization: Bearer <access_token>
   ↓
Spring Security authenticates request
   ↓
Controller → Service
```

The access token is used for normal authenticated API requests.

The refresh token is used to obtain a new access token without requiring the user to log in again.

---

## 4. User Account

A user account is represented by the existing `users` entity/table.

Relevant fields include:

```text
id
email
password_hash
full_name
phone
avatar_url
status
email_verified
last_login_at
created_at
updated_at
deleted_at
version
```

The database defines:

- Email as unique.
- Phone as unique when present.
- User ID as the primary key.
- Soft-delete support through `deleted_at`.
- Optimistic versioning through `version`.

The implementation must reuse the existing entity and constraints.

Do not create another account table or duplicate user representation.

---

## 5. Registration

### 5.1 Purpose

An unauthenticated visitor can create a CineBook customer account using an email address and password.

Registration allows the user to subsequently:

- Log in.
- Book movie tickets.
- Initiate online payments.
- View personal booking history.
- View ticket/payment status.
- Manage personal profile information.

### 5.2 Required information

The registration request currently expects:

```text
email
password
fullName
phone (optional)
```

The exact API contract is defined in `docs/api.md`.

### 5.3 Registration rules

The backend must:

1. Validate all input.
2. Normalize the email consistently with the existing project convention.
3. Check email uniqueness.
4. Check phone uniqueness when a phone number is provided.
5. Hash the password using a secure password-hashing mechanism.
6. Never store a plaintext password.
7. Create the user with the default customer role.
8. Persist the account using the existing User/Role/UserRole model.
9. Return only safe user information.
10. Never return `password_hash`.

A normal public registration request must never allow the caller to choose:

```text
ADMIN
```

or any other privileged role.

The server determines the initial role.

### Default role

A newly registered user is:

```text
CUSTOMER
```

An administrator role must be assigned only through an authorized administrative mechanism or controlled initial-data/seed process.

---

## 6. Registration and Email Verification

The database contains:

```text
email_verified
```

but the current project requirements do not finalize a mandatory email-verification workflow.

Therefore:

- Do not invent an email-verification token system.
- Do not require email verification during registration unless the developer explicitly enables this requirement.
- Do not treat `email_verified` as proof of a workflow that has not been implemented.

The password-reset workflow does require email delivery.

### Registration Policy (Finalized)

- Registration does **not** require email verification before the account can log in or use protected features.
- Newly registered accounts are created with `ACTIVE` status and immediate access tokens are issued upon successful registration.

---

## 7. Login

### 7.1 Purpose

An existing user authenticates using:

```text
email
password
```

Endpoint:

```http
POST /api/v1/auth/login
```

Authentication:

```text
Public
```

### 7.2 Login flow

```text
Client
  ↓
email + password
  ↓
Validate input
  ↓
Find user by email
  ↓
Check account status
  ↓
Verify password hash
  ↓
Load roles
  ↓
Issue access token
  ↓
Create/store refresh token
  ↓
Update last_login_at
  ↓
Return authentication response
```

### 7.3 Login security rules

Do not:

- Return whether an email exists in a way that unnecessarily exposes account information.
- Return the password hash.
- Log the password.
- Log access/refresh tokens unnecessarily.
- Allow locked/deactivated/deleted accounts to authenticate.

The exact account-status behavior must follow the final status definitions in `docs/database.md` and the existing entity implementation.

---

## 8. JWT Access Token

The access token represents the authenticated user's identity and authorization context.

The token should contain enough information for Spring Security to establish:

```text
User identity
+
Granted roles
```

At minimum, the application needs to associate the authenticated request with the user's ID and role(s).

Typical conceptual claims:

```text
sub       = user ID
roles     = user's roles
iat       = issued-at time
exp       = expiration time
```

Do not expose sensitive user information in JWT claims.

Do not put:

- Password hashes.
- Password reset tokens.
- Refresh tokens.
- Sensitive personal data.
- Payment information.

inside the access token.

The exact JWT signing algorithm, secret/key configuration, and token lifetime must follow the existing security configuration.

### Token Lifetime (Finalized)

- Access token lifetime is **15 minutes** (900 seconds / 900,000 ms).

---

## 9. Refresh Tokens

Refresh tokens are persisted in the existing:

```text
refresh_tokens
```

table.

Relevant fields:

```text
id
user_id
token_hash
expires_at
revoked_at
created_at
```

Database constraints include:

- Unique token hash.
- User foreign key.
- Expiration index.

### 9.1 Security rules

The raw refresh token must not be stored in the database.

Store a secure hash:

```text
raw refresh token
       ↓
secure hash
       ↓
refresh_tokens.token_hash
```

When a refresh request arrives:

```text
raw token
   ↓
hash/verify
   ↓
find matching stored token
   ↓
check expiration
   ↓
check revoked_at
   ↓
check user/account status
   ↓
issue new access token
```

Do not accept:

- Expired refresh tokens.
- Revoked refresh tokens.
- Refresh tokens belonging to invalid/deleted/disabled accounts.

### Refresh Token Policy (Finalized)

- Refresh token lifetime is **7 days** (604,800 seconds / 604,800,000 ms).
- **Refresh-token rotation is enforced**: each refresh request revokes the old refresh token (`revoked_at = now`) and issues a new refresh token.

---

## 10. Logout

Endpoint:

```http
POST /api/v1/auth/logout
```

Authentication:

```text
Required
```

Logout should invalidate the relevant refresh-token session by setting:

```text
revoked_at
```

The exact access-token invalidation strategy depends on the JWT architecture.

Because JWT access tokens are normally stateless, logout should not require a distributed token blacklist unless explicitly required.

Prefer revoking the refresh token/session rather than introducing unnecessary infrastructure.

---

## 11. Password Change

An authenticated user may change their password.

Endpoint:

```http
PATCH /api/v1/users/me/password
```

Authentication:

```text
Required
```

Conceptual request:

```text
currentPassword
newPassword
```

Rules:

1. Authenticate the current user.
2. Verify the current password.
3. Validate the new password.
4. Hash the new password.
5. Replace the stored password hash.
6. Never store plaintext passwords.
7. Never return the password hash.

After a successful password change, consider revoking existing refresh-token sessions if required by the final security policy.

### Open decision

```text
TODO / DECISION REQUIRED:
Whether changing the password revokes all existing refresh-token sessions.
```

---

## 12. Forgot Password / Password Reset

CineBook supports password reset through email.

Endpoints:

```http
POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/confirm
```

Authentication:

```text
Public
```

### 12.1 Request reset

The user provides an email address.

Conceptual flow:

```text
Email
  ↓
Find eligible account
  ↓
Generate random reset token
  ↓
Store only token hash
  ↓
Set expiration
  ↓
Send reset link/token through email
```

The raw reset token must not be stored in the database.

The existing database uses:

```text
password_reset_tokens
```

with:

```text
id
user_id
token_hash
expires_at
used_at
created_at
```

### 12.2 Confirm reset

Conceptual flow:

```text
Reset token
     ↓
Verify token hash
     ↓
Check expiration
     ↓
Check used_at
     ↓
Find user
     ↓
Validate new password
     ↓
Hash new password
     ↓
Update password
     ↓
Mark reset token as used
```

A used or expired reset token must never be accepted.

A password-reset token must be single-use.

### Security behavior

The request-reset endpoint should avoid unnecessarily revealing whether an email address exists.

For example, the public response may use a generic message such as:

```text
If the account exists, a password reset email has been sent.
```

The exact response contract belongs to `docs/api.md`.

### Password Reset Policy (Finalized)

- Password reset token lifetime is **15 minutes** (900 seconds / 900,000 ms).
- Password-reset tokens are single-use only (`used_at` set upon use) and stored as SHA-256 hashes.

---

## 13. User Profile

An authenticated user can view and update their own profile.

Endpoints:

```http
GET /api/v1/users/me
PUT /api/v1/users/me
```

Authentication:

```text
Required
```

The user may manage:

```text
fullName
phone
email
avatarUrl
```

subject to validation and database uniqueness constraints.

The user must not modify privileged/system-controlled fields through the profile API, including:

```text
id
password_hash
status
roles
email_verified
created_at
updated_at
deleted_at
version
```

unless a specific administrative/security workflow explicitly permits the operation.

---

## 14. Email Change

Changing email is a sensitive account operation because email is:

- A unique identifier.
- Used for login.
- Used for password recovery.

When updating email:

1. Validate the new email.
2. Check uniqueness.
3. Update the account consistently.
4. Handle `email_verified` according to the final email-verification policy.

If email verification is enabled later, changing the email should invalidate the previous verification state and require verification of the new address.

Do not silently implement a complex verification workflow if it has not been approved.

---

## 15. Phone Number

Phone number is optional during registration but must remain unique when present because the database contains a unique constraint for phone.

When updating a phone number:

- Validate format.
- Normalize according to the project's convention.
- Check uniqueness.
- Return a business validation error rather than a raw database exception when possible.

---

## 16. Avatar

The profile supports:

```text
avatar_url
```

The authentication domain only manages the account's avatar reference.

Actual image upload/storage behavior is outside this document unless explicitly implemented.

Do not introduce a file-storage service or cloud-storage infrastructure merely for avatar support.

---

## 17. User Booking and Transaction History

Authenticated customers can access their own:

- Bookings.
- Tickets.
- Payment status.
- Transaction history.

Relevant API examples:

```http
GET /api/v1/bookings/me
GET /api/v1/bookings/{id}
GET /api/v1/payments/{id}
```

A customer must only be able to access resources belonging to that customer.

Example:

```text
Customer A
    ↓
GET /bookings/B
    ↓
Booking B belongs to Customer B
    ↓
DENY
```

Do not rely only on frontend filtering.

Ownership must be enforced by backend authorization/business logic.

Administrators may access appropriate user/booking/payment information according to their administrative permissions.

---

## 18. Authorization Model

CineBook uses role-based access control (RBAC).

Current primary roles:

```text
CUSTOMER
ADMIN
```

The role relationship is:

```text
users
   ↓
user_roles
   ↓
roles
```

A user may technically have multiple roles because the database uses a many-to-many relationship.

The normal application model currently has:

- Customer users.
- Administrator users.

Do not create additional roles unless explicitly required.

---

## 19. CUSTOMER Permissions

A `CUSTOMER` is an authenticated normal user.

A customer may:

### Public/read operations

Access public movie/cinema/showtime information according to the public API contract.

### Personal account

```text
View own profile
Update own profile
Change own password
Request password reset
Refresh own authentication session
Logout
```

### Booking

```text
Create booking / seat hold
View own bookings
View own booking details
Cancel own booking when business rules allow
Initiate payment for own booking
View own payment status
View own tickets
```

### Forbidden

A customer must NOT:

```text
Create/update/delete movies
Create/update/delete cinemas
Manage auditoriums
Manage seats
Manage showtimes
Manage pricing rules
Manage promotions
Manage users
Assign roles
Lock/unlock users
View arbitrary users' personal information
View arbitrary users' bookings
View arbitrary users' payments
Access administrative reports
```

---

## 20. ADMIN Permissions

An `ADMIN` is an authenticated administrator.

An administrator may access administrative APIs under:

```text
/api/v1/admin/**
```

The current administrative scope includes:

```text
Movies
Genres
Cinemas
Auditoriums
Seat types
Seats
Showtimes
Pricing
Promotions
Users
Reports
```

The exact endpoint contract is defined in `docs/api.md`.

An administrator may:

### Movie management

```text
Create
Update
Soft-delete
```

### Cinema management

```text
Create/update/delete according to business rules
Manage auditoriums
Manage seat types
Manage seats
```

### Showtime management

```text
Create
Update
Cancel
```

### Pricing management

```text
Manage day pricing rules
Manage time-slot pricing rules
```

### Promotion management

```text
Create
Update
Deactivate/delete according to business rules
```

### User management

```text
List/search users
View appropriate user information
Lock/unlock users
Assign roles
```

Administrative user management must never expose password hashes or authentication secrets.

---

## 21. Role Assignment

Normal registration always creates:

```text
CUSTOMER
```

A public client must never send:

```json
{
  "role": "ADMIN"
}
```

and expect the server to honor it.

Role assignment is an administrative/security operation.

If an admin can assign roles through an API, the operation must:

- Require `ADMIN`.
- Validate the target user.
- Validate the requested role.
- Update the `user_roles` relationship.
- Never expose password/security secrets.

---

## 22. Administrative User Access

Admin access to another user's information must still be limited to information required for administration.

Admin responses must not expose:

```text
password_hash
refresh token
password reset token
JWT secret
payment credentials
other secrets
```

Sensitive account information must not be returned simply because the caller is an administrator.

---

## 23. Account Status and Soft Delete

The `users` table contains:

```text
status
deleted_at
```

These fields control account availability and lifecycle.

Authentication must reject users who are not eligible to log in.

Soft-deleted users must not be treated as active accounts.

Do not hard-delete users merely to implement normal account management.

The exact status values and state transitions must follow `docs/database.md` and the existing entity/database implementation.

### Open decision

```text
TODO / DECISION REQUIRED:
Finalize the exact allowed user status values and their authentication behavior.
```

---

## 24. Authorization Matrix

| Resource / Action | Public | CUSTOMER | ADMIN |
|---|:---:|:---:|:---:|
| Register | ✓ | — | — |
| Login | ✓ | — | — |
| Refresh token | ✓* | ✓* | ✓* |
| Logout | — | ✓ | ✓ |
| Password reset request | ✓ | ✓ | ✓ |
| Password reset confirm | ✓ | ✓ | ✓ |
| View public movies | ✓ | ✓ | ✓ |
| View public cinemas | ✓ | ✓ | ✓ |
| View public showtimes | ✓ | ✓ | ✓ |
| View own profile | — | ✓ | ✓ |
| Update own profile | — | ✓ | ✓ |
| Change own password | — | ✓ | ✓ |
| Create booking | — | ✓ | —/as explicitly supported |
| View own bookings | — | ✓ | ✓ |
| Cancel own booking | — | ✓ | ✓ |
| Initiate own payment | — | ✓ | ✓ where appropriate |
| View own payment | — | ✓ | ✓ |
| Manage movies | — | — | ✓ |
| Manage genres | — | — | ✓ |
| Manage cinemas | — | — | ✓ |
| Manage auditoriums | — | — | ✓ |
| Manage seats | — | — | ✓ |
| Manage showtimes | — | — | ✓ |
| Manage pricing | — | — | ✓ |
| Manage promotions | — | — | ✓ |
| Manage users | — | — | ✓ |
| Assign roles | — | — | ✓ |
| View administrative reports | — | — | ✓ |

`*` Refresh-token requests are technically unauthenticated at the HTTP layer but require a valid refresh token.

The exact endpoint-level matrix must remain synchronized with `docs/api.md`.

---

## 25. Ownership vs Role Authorization

Role checks alone are not sufficient for personal resources.

For example:

```text
GET /api/v1/bookings/{id}
```

requires:

```text
authenticated user
AND
(
    booking.user_id == authenticatedUser.id
    OR
    authenticated user has ADMIN role
)
```

The same principle applies to:

- Personal bookings.
- Tickets.
- Payments.
- Profile data.

Never implement:

```text
"is authenticated" → access any resource
```

when the resource is user-owned.

---

## 26. Spring Security Responsibilities

Spring Security is responsible for:

- Authentication.
- JWT extraction/validation.
- Security context creation.
- Role/authority checks.
- Protecting endpoints.

Business services remain responsible for:

- Resource ownership checks.
- Domain-specific authorization.
- Business rules that depend on account state.

Do not place complex business rules inside `SecurityConfig`.

Do not bypass service-level ownership validation merely because an endpoint already has `authenticated()` or `hasRole(...)`.

---

## 27. Security Boundary

The security flow should conceptually be:

```text
HTTP Request
    ↓
JWT filter / Spring Security
    ↓
Authentication established
    ↓
Role-based endpoint authorization
    ↓
Controller
    ↓
Service
    ↓
Resource ownership + business authorization
    ↓
Repository
```

Both authorization layers matter:

```text
Role authorization
+
Resource ownership/business authorization
```

---

## 28. Sensitive Data Rules

Never expose:

```text
password_hash
refresh_tokens.token_hash
password_reset_tokens.token_hash
JWT signing secret
database credentials
VNPay credentials
TMDB credentials
```

Do not log:

```text
password
raw refresh token
raw password reset token
JWT secret
```

If authentication debugging is required, log only safe identifiers and sanitized metadata.

---

## 29. Authentication Error Behavior

Use appropriate HTTP errors according to the API contract.

Typical cases:

```text
400 Bad Request
    Invalid request format / validation

401 Unauthorized
    Missing or invalid authentication

403 Forbidden
    Authenticated but not authorized

404 Not Found
    Resource does not exist or is intentionally hidden

409 Conflict
    Unique-account conflict such as email/phone
```

Do not return raw stack traces or internal database errors to clients.

Use the global exception-handling mechanism defined by the project.

---

## 30. API Contract Reference

Authentication endpoints are defined in:

```text
docs/api.md
```

Current endpoints include:

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout

POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/confirm

GET   /api/v1/users/me
PUT   /api/v1/users/me
PATCH /api/v1/users/me/password
```

Administrative user-management endpoints belong under:

```text
/api/v1/admin/**
```

Do not create duplicate authentication endpoints under different paths unless explicitly required.

---

## 31. Database References

Authentication/authorization primarily uses:

```text
users
roles
user_roles
refresh_tokens
password_reset_tokens
```

The database schema is the source of truth for:

- Primary keys.
- Foreign keys.
- Unique constraints.
- Nullable fields.
- Indexes.
- Soft-delete/version fields.

See:

```text
docs/database.md
```

for the complete schema description.

---

## 32. Related Documents

| Document | Responsibility |
|---|---|
| `AGENTS.md` | AI behavior and engineering rules |
| `docs/architecture.md` | Overall architecture and security flow |
| `docs/database.md` | Identity tables, constraints, indexes |
| `docs/business-rules.md` | Cross-domain business rules |
| `docs/api.md` | Authentication and user API contracts |
| `docs/use-cases/authentication.md` | Optional detailed end-to-end use-case scenarios |
| `docs/payment.md` | Payment authentication boundary and VNPay integration |

Avoid duplicating detailed API schemas or database DDL here.

---

## 33. Implementation Guidance for AI Agent

When implementing authentication:

1. Inspect the existing `User`, `Role`, `UserRole`, `RefreshToken`, and `PasswordResetToken` entities.
2. Inspect their repositories.
3. Inspect existing security/configuration classes.
4. Inspect existing DTO, exception, and response conventions.
5. Inspect the authentication endpoints in `docs/api.md`.
6. Implement the smallest complete solution consistent with the existing architecture.
7. Keep security-sensitive operations in the appropriate Service/Security layers.
8. Never expose secrets.
9. Add focused tests for authentication and authorization behavior.
10. Build and test the backend before considering the task complete.

Do not create a second authentication architecture if one already exists.

Do not replace Spring Security with a custom security system.

Do not add unnecessary authentication libraries.

---

## 34. Open Decisions

The following items remain open for future decisions as needed:

- Exact user `status` enum values and transitions beyond `ACTIVE` / `BLOCKED`.
- Whether password change revokes all refresh sessions.
- Exact production email delivery implementation (SMTP credentials/service provider).
- Exact email-change verification workflow.
- Whether admins may create/modify another user's profile fields beyond administrative management.
- Whether an administrator may perform customer booking/payment operations through normal customer endpoints.

When implementation reaches one of these decisions, inspect the repository/docs first. Ask the developer only if the decision remains unresolved.

---

## Final Principle

Authentication answers:

> **Who is this user?**

Authorization answers:

> **What is this user allowed to do?**

Resource ownership answers:

> **Is this user allowed to access this particular record?**

CineBook must enforce all three.

```text
Authentication
      ↓
Who are you?
      ↓
Authorization
      ↓
What role do you have?
      ↓
Ownership / Business Authorization
      ↓
Can you access this specific resource?
```

Never treat authentication alone as sufficient protection for user-owned or administrator-only data.
