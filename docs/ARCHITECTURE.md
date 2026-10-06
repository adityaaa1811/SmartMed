# SmartMed architecture

SmartMed is a medication management and adherence monitoring application. It is an educational software project, not a medical device, and does not diagnose, prescribe, or replace a clinician or pharmacist.

## Runtime topology

```text
React + Vite + TypeScript (Vercel)
              │ HTTPS JSON API / JWT
              ▼
Spring Boot 3 / Java 17 (Railway)
              │ JDBC / Flyway
              ▼
MySQL 8 (Railway)
```

For local development, the React dev server proxies `/api` to Spring Boot and Docker Compose runs MySQL bound to loopback. Production deployment is intended for Vercel, Railway backend, and Railway MySQL; see the README for environment and TLS requirements. Repository documentation does not claim that a Railway deployment has been tested.

## Backend layers

| Package | Responsibility |
|---------|----------------|
| `controller` | HTTP mapping and request/response DTOs |
| `service` | Business rules, transactions, ownership, role, and relationship authorization |
| `repository` | Spring Data JPA access, projections, locking, and bounded queries |
| `entity` | JPA persistence model |
| `dto` | Validated request and response contracts |
| `security` | JWT authentication and bounded login/registration throttling |
| `config` | Spring Security, CORS, properties, and application clock |
| `exception` | Centralized API error mapping |

Sensitive operations derive the principal from a validated JWT and constrain repository reads and writes by owner or an active matching care relationship. Controllers do not accept a caller-selected patient ID for patient-owned routes.

## Current data model and persistence

Flyway migrations under `backend/src/main/resources/db/migration` are authoritative. The current schema is created in order by V1 (initial tables, keys, and indexes), V2 (medication active flag), V3 (cancelled-dose state and constraint), and V4 (restrict physical medication/schedule deletes so dose history cannot cascade away). Hibernate validates the migrated schema and does not create or update production tables. `database/schema.sql` is a legacy pointer, not a deployable schema.

Core entities are `User`, `Medication`, `MedicationSchedule`, `DoseRecord`, `CareRelationship`, and `Notification`. Medication deactivation is soft: medication and schedules become inactive, historical doses remain, and pending doses are marked `CANCELLED`. Completed dose states are not rewritten. Cancelled doses are excluded from adherence analytics.

Dose generation runs under a per-patient transaction lock, loads the day's existing dose rows in one query, and uses a database uniqueness constraint on `(schedule_id, scheduled_date, scheduled_time)` as a final idempotency guard. Dose transitions are conditional updates from `PENDING`, protecting terminal states during concurrent requests.

## Identity and consent

Public registration always creates PATIENT accounts. CAREGIVER and DOCTOR accounts are provisioned outside public registration. JWT `sub` contains the stable user ID; the email claim is used to load the current account, then checked against that subject. Authorities are read from the database account.

Patients grant or revoke caregiver/doctor access through `CareRelationship`. Monitoring reads require an active relationship with the expected role and remain read-only. Relationship-list queries fetch both users with entity graphs.

## Dates and timezone

Backend calendar calculations use the injected `Clock`, configured by `SMARTMED_TIMEZONE` (default `Asia/Kolkata`). The frontend uses the matching `VITE_SMARTMED_TIMEZONE` for date presets. Date-only values stay as calendar dates and are not converted through UTC instants.

## API and user-facing behavior

- API base path: `/api/v1`.
- Success envelope: `{ "success": true, "message": null, "data": ... }`.
- Errors use the shared API error envelope and HTTP status.
- Adherence history is date-filtered and paginated (default 50 rows, maximum 100 per page).
- Analytics ranges are capped at 366 calendar days.
- In-app notifications are recipient-scoped and paginated.
- Interaction checks use the configured `DrugInteractionProvider`; the default mock provider returns no clinical interaction data.

## CORS, health, and deployment configuration

CORS uses the explicit `SMARTMED_CORS_ORIGINS` allowlist and is enabled in Spring Security. Production database connection fields and `SMARTMED_DB_SSL_MODE` are explicit environment configuration; no production SSL mode is silently selected. Use Railway private networking only when both services share the same project environment, or use certificate-verified TLS for an external database. Deployment setup is documented in README.md.

`/api/v1/health` is a simple process response. Actuator liveness is independent of database availability; readiness includes the database health indicator. Actuator health details are hidden.

## Phases

Phases 1–8 are implemented: foundation, authentication, medication management, scheduling/adherence, analytics, consent-based monitoring, interaction-check abstraction, and persistent in-app notifications. External notification delivery, clinical interaction data, and other future work are not configured.
