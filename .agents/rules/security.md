---
name: cinebook-security
description: Mandatory security, authentication, and credentials protection rules.
---

# CineBook Security Rules

Canonical Auth Reference: `docs/use-cases/authentication.md`
Canonical API Security Reference: `docs/api.md`

## 1. Secrets & Credentials Protection

- **Never hard-code secrets or commit credentials** (`TMDB_API_KEY`, `JWT_SECRET`, database passwords, VNPay merchant keys).
- Use environment variables (`${VAR_NAME:default}`) for all credentials.
- Do not log sensitive parameters: passwords, token values, payment hash secrets.

## 2. Authentication & Authorization

- Authentication uses stateless JWT access tokens (15-minute expiration) with database-backed refresh tokens (7-day expiration, SHA-256 hashed, rotated upon use).
- Password reset tokens expire in 15 minutes, are single-use, and stored as SHA-256 hashes.
- Enforce role-based access control (RBAC):
  - Public: Movie listings, showtimes, cinemas, auth endpoints.
  - `CUSTOMER`: Booking creation, personal profile/booking history, payment initiation.
  - `ADMIN`: All management endpoints under `/api/v1/admin/**`.
- Never trust client-supplied user IDs; extract authenticated user identity from the `SecurityContext`.

## 3. External Gateways

- **VNPay**: Sandbox mode only. Payment verification must occur on the backend via cryptographic signature check; never trust frontend success signals alone.
- **TMDB**: API calls must run server-side only; never expose TMDB API keys to the client.