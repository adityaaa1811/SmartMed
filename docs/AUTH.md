# Authentication (Phase 2)

## Overview

SmartMed uses **stateless JWT authentication** with **BCrypt** password hashing and **role-based** authorities (`ROLE_PATIENT`, `ROLE_CAREGIVER`, `ROLE_DOCTOR`).

## Required configuration

| Variable | Description |
|----------|-------------|
| `SMARTMED_JWT_SECRET` | **Required** at startup. Minimum 32 characters (256-bit HS256 key material). |
| `SMARTMED_JWT_EXPIRATION_MS` | Access token lifetime in milliseconds (default `86400000`). |

The backend fails fast on startup if `SMARTMED_JWT_SECRET` is missing or too short.

## Flows

### Registration

1. Client `POST /api/v1/auth/register` with `fullName`, `email`, `password`, `role`.
2. Server normalizes email to lowercase, checks uniqueness, hashes password, saves `users` row.
3. Server returns `201` with JWT and `UserResponse` (no password fields).

### Login

1. Client `POST /api/v1/auth/login` with `email`, `password`.
2. Server normalizes email, verifies BCrypt hash.
3. On failure: `401` with `INVALID_CREDENTIALS` and generic message (no email enumeration).
4. On success: `200` with JWT and user summary.

### Authenticated requests

1. Client sends `Authorization: Bearer <accessToken>`.
2. `JwtAuthenticationFilter` validates signature and expiry, loads user by email claim.
3. Spring Security sets the authentication principal (`SmartMedUserDetails`).

### Current user

`GET /api/v1/users/me` returns the authenticated user from the JWT principal (via database load), not from a client-supplied user id.

## Public vs protected routes

| Method | Path | Access |
|--------|------|--------|
| GET | `/api/v1/health` | Public |
| POST | `/api/v1/auth/register` | Public |
| POST | `/api/v1/auth/login` | Public |
| GET | `/api/v1/users/me` | Authenticated |
| * | Other `/api/**` | Authenticated (future phases) |

## Method security

`@EnableMethodSecurity` is enabled. Future endpoints may use:

```java
@PreAuthorize("hasRole('PATIENT')")
```

## Error format

```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid email or password"
  }
}
```

Validation errors include `fieldErrors` under `error`.

## Local testing

```bash
export SMARTMED_JWT_SECRET='your-local-secret-at-least-32-characters-long'
export SMARTMED_JPA_DDL=update
cd backend && ./mvnw spring-boot:run
```

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Aditya Mishra","email":"aditya.test@example.com","password":"SmartMed@123","role":"PATIENT"}'

curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"aditya.test@example.com","password":"SmartMed@123"}'

curl -s http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <accessToken>"
```

## Frontend (minimal)

- `frontend/src/api/auth.ts` — register/login/current user API calls
- `frontend/src/auth/tokenStorage.ts` — sessionStorage token helper (no secrets in Vite env)
