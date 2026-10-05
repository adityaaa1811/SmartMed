# SmartMed

**SmartMed – AI-Powered Medication Adherence & Interaction Checker**

Web application for medication management, scheduling, adherence analytics, caregiver and clinician monitoring, and **informational** drug interaction checking.

> **Disclaimer:** SmartMed is an educational/software project. It does not diagnose, prescribe, or replace qualified healthcare professionals.

## Features (roadmap)

| Phase | Status |
|-------|--------|
| 1 — Foundation | ✅ Complete |
| 2 — Authentication & users | ✅ Current |
| 3 — Medication management | Planned |
| 4 — Scheduling & adherence | Planned |
| 5 — Analytics | Planned |
| 6 — Drug interactions | Planned |
| 7–12 | Caregiver, doctor, notifications, reports, hardening, polish |

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
| `SMARTMED_INTERACTION_API_KEY` | External interaction API key (Phase 6) |

## API

### `GET /api/v1/health` (public)

Returns application status and API version.

### `POST /api/v1/auth/register` (public)

Body: `fullName`, `email`, `password` (min 8), `role` (`PATIENT` | `CAREGIVER` | `DOCTOR`). Returns `201` + JWT.

### `POST /api/v1/auth/login` (public)

Body: `email`, `password`. Returns `200` + JWT or `401` `INVALID_CREDENTIALS`.

### `GET /api/v1/users/me` (Bearer token)

Returns the authenticated user profile (no password fields).

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
