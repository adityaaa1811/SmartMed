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
| Backend | Java 17, Spring Boot 3.3, Spring Web, Data JPA, Security, Validation |
| Database | MySQL 8 |
| Frontend | React 18, Vite 5, TypeScript |
| Build | Maven (backend), npm (frontend) |

## Prerequisites

- **JDK 17+**
- **Maven 3.9+** (or use the included `./mvnw` wrapper in `backend/`)
- **Node.js 18+** and npm
- **Docker** (recommended for local MySQL)

## Quick start

### 1. Database (MySQL)

From the repo root:

```bash
cd database
docker compose up -d
```

Default credentials match `.env.example` (`smartmed` / `smartmed`, database `smartmed`).

### 2. Backend

```bash
cd backend
export SMARTMED_JPA_DDL=update   # until Flyway migrations land in a later phase
export SMARTMED_JWT_SECRET='your-local-secret-at-least-32-characters-long'
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

Copy [.env.example](.env.example) and set values locally (never commit `.env`).

| Variable | Purpose |
|----------|---------|
| `SMARTMED_DB_URL` | JDBC URL for MySQL |
| `SMARTMED_DB_USERNAME` / `SMARTMED_DB_PASSWORD` | Database credentials |
| `SMARTMED_JPA_DDL` | Hibernate DDL mode (`update` for dev) |
| `SMARTMED_CORS_ORIGINS` | Allowed frontend origins |
| `SMARTMED_JWT_SECRET` | **Required** — JWT signing secret (min 32 characters) |
| `SMARTMED_JWT_EXPIRATION_MS` | Access token lifetime (default 86400000) |
| `SMARTMED_INTERACTION_PROVIDER` | `mock` (default) or future external provider id |
| `SMARTMED_INTERACTION_API_KEY` | Reserved for future interaction-provider integration |

## API

### `GET /api/v1/health` (public)

Returns application status and API version.

### `POST /api/v1/auth/register` (public)

Body: `fullName`, `email`, `password` (min 8), `role` (`PATIENT` | `CAREGIVER` | `DOCTOR`). Returns `201` + JWT.

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

Notifications are generated synchronously from existing application events; there is no background scheduler. Interaction notifications are deferred: the configured mock provider returns no interaction data, and provider unavailability is not persisted as a notification. Notifications are informational software events, not diagnoses, risk predictions, treatment recommendations, or emergency alerts. The notification table is managed by the existing Hibernate schema strategy; no migration tool or dependency was added.

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

## License

TBD — educational portfolio project.
