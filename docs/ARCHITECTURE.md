# SmartMed Architecture

SmartMed is a production-style web application for medication adherence, scheduling, analytics, and **informational** drug interaction checking. It is not a medical device and does not replace clinicians.

## High-level topology

```text
┌─────────────────┐     REST (JSON)      ┌──────────────────────────────┐
│  React + Vite   │ ◄──────────────────► │  Spring Boot 3 (Java 17)     │
│  (frontend/)    │     JWT (Phase 2+)   │  Layered architecture        │
└─────────────────┘                      └──────────────┬───────────────┘
                                                          │
                                                          ▼
                                               ┌──────────────────────┐
                                               │  MySQL 8             │
                                               └──────────────────────┘

Phase 7: DrugInteractionProvider ──► configured provider (mock by default)
Future:  NotificationService     ──► Email/SMS adapters (env-configured)
```

## Backend layers

| Layer | Responsibility |
|--------|----------------|
| `controller` | HTTP mapping, status codes, DTO in/out only |
| `service` | Business rules, transactions, authorization checks |
| `repository` | JPA data access |
| `model` | JPA entities (not exposed on the wire) |
| `dto` | API request/response shapes |
| `config` | Security, CORS, properties |
| `exception` | Centralized error mapping |

**Rules:** No business logic in controllers. No SQL in controllers. Never trust IDs from the client without verifying ownership/role.

## API conventions

- Base path: `/api/v1`
- Success envelope: `{ "success": true, "message": null, "data": { ... } }`
- Errors: `ErrorResponse` with HTTP status, message, path, optional field errors
- Public endpoints: `/api/v1/health`, `/api/v1/public/**` (auth routes in Phase 2)

## Authentication (Phase 2 plan)

- **Stateless JWT** (access token) issued after login; refresh strategy TBD (refresh token table or short-lived access only for MVP).
- **BCrypt** password hashing (`PasswordEncoder` bean already registered).
- **Role enum:** `PATIENT`, `CAREGIVER`, `DOCTOR` on core `User` entity.
- **Spring Security** filter chain: JWT authentication filter before `UsernamePasswordAuthenticationFilter`.
- **Method-level security** (`@PreAuthorize`) on sensitive service entry points.
- Profile tables: `PatientProfile`, `CaregiverProfile`, `DoctorProfile` linked 1:1 to `User`.

## Database design (planned)

Normalized relational model in MySQL 8 (utf8mb4).

### Core identity

- **users** — email (unique), password_hash, role, enabled, timestamps
- **patient_profiles** — user_id FK, display fields
- **caregiver_profiles** — user_id FK
- **doctor_profiles** — user_id FK, clinic metadata

### Medication domain

- **medications** — patient_id FK, name, generic_name, dosage, unit, frequency, instructions, active flag, date range
- **medication_schedule_times** — medication_id FK, time_of_day (supports multiple times per day)
- **scheduled_doses** — materialized dose instances (date, scheduled_at, status: UPCOMING/TAKEN/MISSED/SKIPPED)
- **dose_events** / **adherence_logs** — immutable adherence events for analytics (backend is source of truth)

### Relationships & access

- **caregiver_connections** — patient_id, caregiver_id, status (PENDING/ACTIVE/REVOKED), consent timestamps
- **doctor_patient_connections** — patient_id, doctor_id, status, permissions bitmask or JSON for scoped access

### Safety & ops

- **interaction_checks** — audit of checks run (provider id, request hash, result snapshot, mock flag)
- **notifications** — type, payload, read state, recipient user_id
- **audit_logs** — sensitive actions (optional Phase 11)

Indexes: FK columns, `(patient_id, scheduled_at)` on doses, `(recipient_id, created_at)` on notifications.

Migrations: introduce **Flyway** in Phase 2 with versioned scripts under `backend/src/main/resources/db/migration`.

## Drug interaction provider abstraction (Phase 7)

```text
DrugInteractionProvider (interface)
    ├── MockDrugInteractionProvider   (@ConditionalOnProperty provider=mock) — no fabricated clinical data
    └── ExternalDrugInteractionProvider (future) — calls real API using SMARTMED_INTERACTION_API_KEY
```

`DrugInteractionResult` includes `fromMockProvider` so the UI can show disclaimers. Real providers must map vendor severity to `InteractionSeverity` without inventing interactions.

## Frontend structure

```text
frontend/
  src/
    api/          — fetch client, typed endpoints
    components/   — shared UI
    pages/        — route-level views
    styles/       — design tokens (SmartMed palette)
    hooks/
    types/
```

Vite dev server proxies `/api` → `http://localhost:8080`. Production build served separately (nginx or Spring static — TBD).

## Security checklist (ongoing)

- Env-based secrets; `.env` gitignored
- CORS allowlist via `SMARTMED_CORS_ORIGINS`
- Input validation (`jakarta.validation`) on DTOs
- Authorization on every patient-scoped resource
- No stack traces in API responses (global handler)
- Healthcare disclaimer in UI for interactions

## Phased delivery

| Phase | Scope |
|-------|--------|
| 1 | Foundation (this document), health API, frontend shell, MySQL docker |
| 2 | Auth, users, roles, JWT |
| 3 | Medication CRUD |
| 4 | Scheduling & dose logging |
| 5 | Adherence analytics |
| 6 | Consent-based caregiver and doctor monitoring |
| 7 | On-demand medication interaction checker |
| 8+ | Future features (notifications, reports, and additional hardening) |
