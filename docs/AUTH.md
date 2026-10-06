# Authentication and authorization

SmartMed uses stateless JWT access tokens, BCrypt password hashes, and the `PATIENT`, `CAREGIVER`, and `DOCTOR` roles. Public registration creates PATIENT accounts only. A caller-supplied `role` field is ignored; elevated roles must be assigned through a trusted administrative process.

## Configuration

| Variable | Description |
|----------|-------------|
| `SMARTMED_JWT_SECRET` | Required at startup. Use a generated secret with at least 32 bytes of key material. Production requires the `base64:` prefix and validates decoded length and obvious weak patterns. |
| `SMARTMED_JWT_EXPIRATION_MS` | Access-token lifetime in milliseconds (default `86400000`). |

Generate a fresh local secret in your shell; never paste the generated value into source control or documentation:

```bash
export SMARTMED_JWT_SECRET="base64:$(openssl rand -base64 32)"
```

Production secrets belong in the deployment provider's secret manager. The application does not log secret values.

## Flows

### Registration

1. Client sends `POST /api/v1/auth/register` with `fullName`, `email`, and `password`.
2. The server normalizes email, checks uniqueness, hashes the password, and creates a PATIENT account.
3. The server returns `201` with an access token and a user summary without password fields.

### Login

1. Client sends `POST /api/v1/auth/login` with `email` and `password`.
2. The server normalizes email and verifies the BCrypt hash.
3. Failure returns `401 INVALID_CREDENTIALS` with a generic message. Success returns `200` with a JWT and user summary.

### Authenticated requests

1. Client sends `Authorization: Bearer <accessToken>`.
2. `JwtAuthenticationFilter` validates signature and expiration, loads the account using the email claim, and requires the signed `sub` to equal that account's stable database ID.
3. Spring Security builds the principal and authorities from the currently loaded database account; the token's role claim is not used to grant access.

`GET /api/v1/users/me` returns the authenticated database-backed principal, never a client-supplied user ID.

## Access rules

| Method | Path | Access |
|--------|------|--------|
| GET | `/api/v1/health` | Public |
| POST | `/api/v1/auth/register` | Public; creates PATIENT only |
| POST | `/api/v1/auth/login` | Public |
| GET | `/api/v1/users/me` | Authenticated |
| Other `/api/**` | Authenticated, with endpoint and service role/ownership checks |

Caregiver and doctor access to patient data requires an active matching consent relationship. Patient-owned medication, schedule, dose, analytics, interaction, and notification operations verify ownership at the service/repository boundary.

## Local run and smoke test

Start local MySQL using the instructions in the repository README, generate a fresh local secret as shown above, then run:

```bash
cd backend && ./mvnw spring-boot:run
```

Public registration sends no role field:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Example Patient","email":"patient@example.test","password":"Use-a-unique-local-password-123!"}'
```

Login and use the returned bearer token:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"patient@example.test","password":"Use-a-unique-local-password-123!"}'

curl -s http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <accessToken>"
```

## Frontend token handling

- `frontend/src/api/auth.ts` contains registration, login, and current-user calls.
- `frontend/src/auth/tokenStorage.ts` stores the access token in `sessionStorage`; do not put JWT secrets in Vite variables.
