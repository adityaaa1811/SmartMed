# SmartMed

**SmartMed – Medication Management & Adherence Monitoring**

Web application for medication management, scheduling, adherence analytics, and consent-based caregiver and doctor monitoring.

> **Disclaimer:** SmartMed is an educational/software project. It does not diagnose, prescribe, or replace qualified healthcare professionals.

## Features (roadmap)

| Phase | Status |
|-------|--------|
| 1 — Foundation | ✅ Complete |
| 2 — Authentication & users | ✅ Complete |
| 3 — Medication management | ✅ Complete |
| 4 — Scheduling & adherence | ✅ Complete |
| 5 — Adherence analytics | ✅ Complete |
| 6 — Consent-based caregiver & doctor monitoring | ✅ Complete |
| 7 — Medication interaction checker | ✅ Complete |
| 8 — Attention center & in-app notifications | ✅ Complete |
| 9+ | Future work |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full plan. Authentication details: [docs/AUTH.md](docs/AUTH.md).

## Tech stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3.5, Spring Web, Data JPA, Security, Validation, Actuator |
| Database | MySQL 8 with Flyway migrations |
| Frontend | React 18, Vite 5, TypeScript |
| Build | Maven (backend), npm (frontend) |

## Prerequisites

- **JDK 17+**
- **Maven 3.9+** (or use the included `./mvnw` wrapper in `backend/`)
- **Node.js 18+** and npm
- **Docker** (recommended for local MySQL)

## Quick start

### 1. Database (MySQL)

From the repository root, copy `.env.example` to `.env`, then run:

```bash
cd database
docker compose --env-file ../.env up -d
```

MySQL binds to `127.0.0.1` only. The example credentials are for local development only.

### 2. Backend

```bash
cd backend
export SMARTMED_JWT_SECRET="base64:$(openssl rand -base64 32)"
export SMARTMED_DB_SSL_MODE=DISABLED
export SMARTMED_DB_USERNAME=smartmed
export SMARTMED_DB_PASSWORD='local-dev-db-change-me'
./mvnw spring-boot:run
```

API health: [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The dev server proxies `/api` to the backend.

## Environment variables

Copy [.env.example](.env.example) to `.env` for local database values, then export the backend variables in your shell. Never commit `.env`. Flyway creates or upgrades the schema; Hibernate validates it and does not auto-update tables.

| Variable | Purpose |
|----------|---------|
| `SMARTMED_DB_URL` | Local-profile JDBC URL override |
| `SMARTMED_DB_USERNAME` / `SMARTMED_DB_PASSWORD` | Database credentials |
| `SMARTMED_DB_HOST` / `SMARTMED_DB_PORT` / `SMARTMED_DB_NAME` | Production database address and name (Railway `MYSQL*` variables are accepted as fallbacks) |
| `SMARTMED_DB_SSL_MODE` | Required in production. Use `DISABLED` only over Railway private networking; use `VERIFY_IDENTITY` for public/external MySQL. |
| JVM truststore settings | Optional external-MySQL CA configuration through `JAVA_TOOL_OPTIONS` (`javax.net.ssl.trustStore`, `trustStoreType`, and `trustStorePassword`). |
| `SMARTMED_JPA_DDL` | Local-profile Hibernate schema mode (`validate` by default; production is fixed to `validate`) |
| `SMARTMED_FLYWAY_ENABLED` | Migration control (`true` by default; tests disable Flyway for H2 create-drop) |
| `SMARTMED_CORS_ORIGINS` | Explicit comma-separated frontend origins |
| `SMARTMED_TIMEZONE` | Calendar timezone (defaults to `Asia/Kolkata`) |
| `SMARTMED_JWT_SECRET` | Required, private JWT signing key (32+ bytes; production requires `base64:` encoded key material) |
| `SMARTMED_LOGIN_FAILURE_LIMIT` / `SMARTMED_REGISTRATION_LIMIT` | Per-IP auth limits; defaults 10 failures and 8 registrations per 15 minutes |
| `SMARTMED_JWT_EXPIRATION_MS` | Access token lifetime (default 86400000) |
| `SMARTMED_INTERACTION_PROVIDER` | `mock` (default) or future external provider id |
| `SMARTMED_INTERACTION_API_KEY` | Reserved for future interaction-provider integration |

## API

### `GET /api/v1/health` (public)

Reports that the application process is alive. Actuator `/actuator/health/liveness` is the liveness probe; `/actuator/health/readiness` additionally checks the database. Actuator exposes health only and hides component details.

### `POST /api/v1/auth/register` (public)

Body: `fullName`, `email`, `password` (min 8). Public registration always creates a `PATIENT`; elevated roles must be assigned administratively. Returns `201` + JWT.

Adherence history accepts optional `from`/`to` ISO dates and `page`/`pageSize` (default page `0`, page size `50`, maximum `100`). Analytics accepts optional date ranges up to 366 days.

### `POST /api/v1/auth/login` (public)

Body: `email`, `password`. Returns `200` + JWT or `401` `INVALID_CREDENTIALS`.

### `GET /api/v1/users/me` (Bearer token)

Returns the authenticated user profile (no password fields).

## Phase 6: consent-based care team monitoring

Patients create invitations by exact email lookup with POST /api/v1/relationships and a relationshipType of CAREGIVER or DOCTOR. The patient owns consent. A related user can accept (PENDING → ACTIVE) or decline (PENDING → REVOKED); patients can revoke active access (ACTIVE → REVOKED). Revoked relationships remain in the record and a new invitation can be created later.

GET /api/v1/relationships returns only the authenticated patient's relationships or the authenticated caregiver/doctor's related requests. Caregivers can read connected patient summaries, saved doses for today, and analytics under /api/v1/caregiver/patients. Doctors can read connected patient summaries, patient overviews, saved doses, and analytics under /api/v1/doctor/patients. Analytics endpoints accept optional ISO dates in from and to.

Monitoring routes require an active relationship of the matching role. Caregiver and doctor views are read-only; they do not create dose records or change dose status. Responses contain only monitoring fields and never include passwords, password hashes, or JWTs.

## Phase 7: medication interaction checker

`POST /api/v1/interactions/check` is available to authenticated PATIENT users only. The request contains `medicationIds`; the server derives the patient from the JWT, verifies every medication belongs to that patient, and sends only the selected medication names to the injected `DrugInteractionProvider`. Mixed or foreign IDs fail the whole request.

The provider is on-demand and results are not stored. `SUCCESS` means provider interaction records were returned; `NO_DATA` means the provider returned no records, which does not establish that no interactions exist. Provider failures return HTTP 503 with `INTERACTION_PROVIDER_UNAVAILABLE` and `PROVIDER_UNAVAILABLE` response data. The UI states these outcomes separately and displays: “Interaction results are informational and do not replace advice from a doctor or pharmacist.”

The existing provider abstraction remains the integration point. The configured `mock` provider returns an empty result and contains no real clinical interaction data. No external medical API or interaction database is configured.

## Phase 8: attention center and in-app notifications

SmartMed stores recipient-owned in-app notifications for missed-dose events, care-relationship changes, and month-to-date adherence attention. `GET /api/v1/notifications` returns a page (default 20, maximum 50) and supports `unreadOnly=true`; `/unread-count`, `/{id}/read`, `/{id}/unread`, and `/read-all` are scoped to the authenticated recipient. Duplicate events are prevented by a recipient/type/event key backed by a database uniqueness constraint.

Missed-dose notifications are created with the dose state transition. Monitoring notifications go only to caregivers/doctors with an ACTIVE matching relationship at that time. Adherence attention uses the existing analytics calculation for the current calendar month; it requires at least three recorded doses and a result below the 70% SmartMed product threshold. This threshold is an application attention rule, not a medically validated measure. Pending-dose notifications are deferred because SmartMed has no configured timing rule that establishes when a pending dose needs attention.

Notifications are generated synchronously from existing application events; there is no background scheduler. Interaction notifications are deferred: the configured mock provider returns no interaction data, and provider unavailability is not persisted as a notification. Notifications are informational software events, not diagnoses, risk predictions, treatment recommendations, or emergency alerts. Flyway owns the database schema; Hibernate uses `validate` in production.

Success envelope:

```json
{ "success": true, "data": { } }
```

Errors:

```json
{
  "success": false,
  "error": { "code": "INVALID_CREDENTIALS", "message": "Invalid email or password" }
}
```

See [docs/AUTH.md](docs/AUTH.md) for flows and curl examples.

## Project layout

See [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md).

## Tests

```bash
cd backend && ./mvnw test
```

Tests use an in-memory H2 profile (`application-test.properties`).

## Repository

[https://github.com/adityaaa1811/SmartMed](https://github.com/adityaaa1811/SmartMed)

## Production Deployment

SmartMed supports a Vercel frontend, Railway Spring Boot backend, and Railway MySQL database. Configure deployment values in the provider dashboards; do not commit secrets or deployment-specific URLs.

### Frontend (Vercel)

- Set the project root to `frontend`; use `npm run build` and `dist` as the output directory.
- Set `VITE_API_BASE_URL` to the HTTPS origin of the deployed Railway backend, with no `/api` suffix. Set `VITE_SMARTMED_TIMEZONE` to the same IANA timezone as `SMARTMED_TIMEZONE`. Leave the API URL unset locally to use the Vite `/api` proxy.
- Set the backend CORS origin to the exact Vercel production origin (and preview origins only if needed).

### Backend (Railway)

- Set the service root directory to `backend`; use Java 17 and the included Maven wrapper. Build with `./mvnw -B package -DskipTests`; start with `java -jar target/smartmed-backend-0.1.0-SNAPSHOT.jar`.
- Set `SPRING_PROFILES_ACTIVE=prod`. Railway supplies `PORT`.
- Configure `SMARTMED_DB_HOST`, `SMARTMED_DB_PORT`, `SMARTMED_DB_NAME`, `SMARTMED_DB_USERNAME`, and `SMARTMED_DB_PASSWORD`; Railway's `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, and `MYSQLPASSWORD` names are accepted as fallbacks. These values are required rather than replaced with production defaults.
- Choose the database connection mode explicitly with `SMARTMED_DB_SSL_MODE`. If the backend and MySQL are in the same Railway project environment and the backend connects to the database's private `.railway.internal` address, Railway says service traffic is encrypted by its WireGuard private network. In that specific setup, set `SMARTMED_DB_SSL_MODE=DISABLED`; this disables MySQL-protocol TLS and relies on Railway's encrypted private transport, not certificate identity verification. Do not use this mode for a public database connection. [Railway private networking documentation](https://docs.railway.com/networking/private-networking)
- If that private MySQL account uses MySQL 8's `caching_sha2_password` authentication, also set `SMARTMED_DB_ALLOW_PUBLIC_KEY_RETRIEVAL=true`. Use this only over Railway's private WireGuard network; Connector/J's public-key retrieval is off by default because an untrusted network could substitute the key. For a public/external connection keep it `false` and use verified TLS. [Connector/J authentication requirements](https://dev.mysql.com/doc/relnotes/connector-j/en/news-8-0-9.html)
- For a public/external MySQL endpoint, set `SMARTMED_DB_SSL_MODE=VERIFY_IDENTITY` and configure the database CA in the JVM truststore. Mount a deployment-managed truststore and set `JAVA_TOOL_OPTIONS` with `-Djavax.net.ssl.trustStore=file:/...`, `-Djavax.net.ssl.trustStoreType=PKCS12` (or its actual type), and `-Djavax.net.ssl.trustStorePassword=...`; keep the file and password out of Git. Connector/J uses the system truststore when no driver-specific truststore is configured. `VERIFY_IDENTITY` checks certificate trust and hostname identity. [MySQL Connector/J TLS configuration](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)
- Set `SMARTMED_JWT_SECRET` to a freshly generated `base64:` key with at least 32 cryptographically random bytes (generate with `openssl rand -base64 32`). Store it only as a Railway secret. Also set `SMARTMED_CORS_ORIGINS` to the exact frontend origin and `SMARTMED_TIMEZONE` to the deployment's calendar timezone.
- Flyway applies database migrations at startup; Flyway clean is disabled. Hibernate is configured to `validate`, never create or update production tables. `/actuator/health/liveness` is the process probe; `/actuator/health/readiness` checks the database. `/api/v1/health` remains the simple application endpoint.

The login and registration limiter is in-memory and bounded, suitable for a single backend instance. If the service is scaled across multiple instances, add shared edge or gateway rate limiting before relying on the application limiter across replicas.

### Local development

Copy `.env.example` to `.env`, run `cd database && docker compose --env-file ../.env up -d`, then export `SMARTMED_DB_USERNAME=smartmed`, `SMARTMED_DB_PASSWORD` to the local example value, `SMARTMED_DB_SSL_MODE=DISABLED`, and a locally generated `base64:` JWT key before starting the backend. The disabled local MySQL TLS setting is only for loopback Docker development. Flyway initializes the local schema and Hibernate validates it. Start the frontend with `cd frontend && npm install && npm run dev`; local API calls use the Vite proxy.
