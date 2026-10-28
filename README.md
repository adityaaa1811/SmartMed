
# SmartMed 💊
### Medication Management & Adherence Monitoring System

SmartMed is a web-based application designed to organize medication records, schedules, dose history, adherence analytics, and consent-based caregiver and doctor monitoring.

The project combines a modern React frontend with a Java Spring Boot backend and MySQL database, with an emphasis on security, data integrity, patient-controlled consent, and maintainable software architecture.

> **Disclaimer:** SmartMed is an educational software project. It does not diagnose, prescribe, guarantee medication safety, or replace qualified healthcare professionals.

---

## 📚 Project Documentation & Deliverables

All major academic and technical documents for SmartMed are available below.

| Document | Description | File |
|---|---|---|
| 📄 Project Report | Detailed project report covering the problem statement, objectives, feasibility, system design, implementation, testing, and future scope. | [View Project Report (PDF)](docs/project-documents/NEW%20SMARTMED%20project%20report.pdf) |
| 📑 IEEE Research Paper | Research-style paper describing the architecture, security model, consent-based monitoring, data integrity, implementation, and verification approach. | [Read Research Paper (PDF)](docs/project-documents/SmartMed_IEEE_Research_Paper.pdf) |
| 🎯 Project Presentation | Visual presentation covering the problem, proposed solution, objectives, architecture, functional modules, security, workflows, limitations, and future enhancements. | [View Project Presentation (PPTX)](docs/project-documents/SmartMed_Impactful_Project_Presentation.pptx) |
| 📋 Software Requirements Specification | IEEE-style SRS documenting functional requirements, non-functional requirements, interfaces, workflows, security, acceptance criteria, and traceability. | [Read SRS Document (PDF)](docs/project-documents/SmartMed_SRS_IEEE_Style.pdf) |

### Quick Access

- [Project Report](docs/project-documents/NEW%20SMARTMED%20project%20report.pdf)
- [IEEE Research Paper](docs/project-documents/SmartMed_IEEE_Research_Paper.pdf)
- [Project Presentation](docs/project-documents/SmartMed_Impactful_Project_Presentation.pptx)
- [Software Requirements Specification (SRS)](docs/project-documents/SmartMed_SRS_IEEE_Style.pdf)

> **Note:** GitHub allows visitors to open and preview PDF files and download PowerPoint presentations. These links provide access to the complete documents; GitHub Markdown does not embed a full PDF viewer or interactive PowerPoint slideshow directly inside the README.

---

## ✨ Features

| Module | Description | Status |
|---|---|---|
| Foundation | Application architecture, configuration, and project setup | ✅ Complete |
| Authentication & Users | Registration, login, JWT authentication, and user profiles | ✅ Complete |
| Medication Management | Create, view, update, and deactivate medication records | ✅ Complete |
| Scheduling & Adherence | Medication schedules, dose generation, and dose tracking | ✅ Complete |
| Adherence Analytics | Date-filtered history and adherence summaries | ✅ Complete |
| Caregiver & Doctor Monitoring | Consent-based, role-matched, read-only access | ✅ Complete |
| Medication Interaction Checker | Provider-based informational interaction-checking workflow | ✅ Complete |
| Attention Center & Notifications | In-app notifications and adherence-related attention events | ✅ Complete |
| Future Enhancements | Additional integrations, hardening, and validation | 🔄 Planned |

**Important limitation:** The current interaction-checking provider is a mock implementation. It does not contain a real clinical drug-interaction database.

---

## 🛠️ Tech Stack

| Layer | Technologies |
|---|---|
| Frontend | React 18, TypeScript, Vite 5 |
| Backend | Java 17, Spring Boot 3, Spring Web, Spring Data JPA |
| Security | Spring Security, JWT, BCrypt, validation, rate limiting |
| Database | MySQL 8 |
| Database Migrations | Flyway |
| Testing | JUnit, Spring Boot Test, H2 test profile |
| Build Tools | Maven Wrapper, npm |
| Visualization | Chart.js |
| Deployment | Vercel, Railway |

---

## 🏗️ System Architecture

SmartMed follows a three-tier architecture.

```text
┌──────────────────────────────────────┐
│          Presentation Layer           │
│       React + TypeScript + Vite       │
│    Forms, dashboards, and analytics   │
└──────────────────┬───────────────────┘
                   │ REST / JSON
                   ▼
┌──────────────────────────────────────┐
│          Application Layer            │
│          Java + Spring Boot            │
│ Controllers → Services → Repositories │
│ Authentication and authorization      │
└──────────────────┬───────────────────┘
                   │ JPA / SQL
                   ▼
┌──────────────────────────────────────┐
│           Data Layer                  │
│              MySQL 8                  │
│       Flyway-managed schema            │
└──────────────────────────────────────┘
```

### Core Design Principles

- Separation of presentation, business logic, and persistence.
- Server-side authentication and authorization.
- Patient ownership checks for medication-related operations.
- Explicit, revocable consent for care-team monitoring.
- Transaction-aware dose generation and duplicate prevention.
- Preservation of historical dose records.
- Version-controlled database migrations.
- Automated tests for important functional and security behaviours.

For additional implementation details, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/AUTH.md](docs/AUTH.md), and [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md).

---

## 🔐 Security & Consent Model

SmartMed implements several application-level security controls:

- **JWT authentication:** Protected API routes require a valid bearer token.
- **Password protection:** Passwords are hashed using BCrypt.
- **Server-side authorization:** Permissions are determined using the authenticated account and current database state.
- **Ownership enforcement:** Patient-owned records are checked against the authenticated patient.
- **Role-based access:** Caregivers and doctors can access only permitted monitoring routes.
- **Consent management:** Patients can invite, approve through acceptance, and revoke care-team access.
- **Read-only monitoring:** Caregivers and doctors cannot change patient dose status through monitoring routes.
- **Authentication rate limiting:** Registration and login requests are rate-limited.
- **Data integrity:** Database constraints and controlled state transitions help prevent duplicate dose records and preserve history.

Care-team relationships use the following states:

`PENDING` → `ACTIVE` → `REVOKED`

A matching active relationship is required for monitoring access. Revoked relationships remain in the record for traceability.

---

## 🚀 Getting Started

### Prerequisites

Install the following tools:

- JDK 17 or later
- Maven 3.9+ (or use the included Maven Wrapper)
- Node.js 18+ and npm
- Docker, recommended for running MySQL locally

### 1. Clone the Repository

```bash
git clone https://github.com/adityaaa1811/SmartMed.git
cd SmartMed
```

### 2. Configure the Environment

Copy `.env.example` to `.env` in the repository root and configure the local database values.

Never commit `.env` or production secrets to GitHub.

### 3. Start the Database

```bash
cd database
docker compose --env-file ../.env up -d
cd ..
```

MySQL is configured for local development. Use the repository's database configuration and local credentials.

### 4. Start the Backend

```bash
cd backend

export SMARTMED_JWT_SECRET="base64:$(openssl rand -base64 32)"
export SMARTMED_DB_SSL_MODE=DISABLED
export SMARTMED_DB_USERNAME=smartmed
export SMARTMED_DB_PASSWORD='local-dev-db-change-me'

./mvnw spring-boot:run
```

The disabled database TLS setting is intended only for the documented local development setup. Do not reuse it for a public database connection.

Backend health endpoint:

[http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)

### 5. Start the Frontend

Open a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the application:

[http://localhost:5173](http://localhost:5173)

The Vite development server proxies `/api` requests to the backend.

---

## ⚙️ Environment Variables

| Variable | Purpose |
|---|---|
| `SMARTMED_DB_URL` | Local-profile JDBC URL override |
| `SMARTMED_DB_USERNAME` | Database username |
| `SMARTMED_DB_PASSWORD` | Database password |
| `SMARTMED_DB_HOST` | Database hostname |
| `SMARTMED_DB_PORT` | Database port |
| `SMARTMED_DB_NAME` | Database name |
| `SMARTMED_DB_SSL_MODE` | Database TLS mode |
| `SMARTMED_CORS_ORIGINS` | Allowed frontend origins |
| `SMARTMED_TIMEZONE` | Application calendar timezone; defaults to `Asia/Kolkata` |
| `SMARTMED_JWT_SECRET` | Private JWT signing key |
| `SMARTMED_JWT_EXPIRATION_MS` | Access-token lifetime; default `86400000` |
| `SMARTMED_LOGIN_FAILURE_LIMIT` | Login failure limit |
| `SMARTMED_REGISTRATION_LIMIT` | Registration rate limit |
| `SMARTMED_FLYWAY_ENABLED` | Flyway migration control |
| `SMARTMED_JPA_DDL` | Local-profile Hibernate schema mode |
| `SMARTMED_INTERACTION_PROVIDER` | Interaction provider identifier; defaults to `mock` |
| `SMARTMED_INTERACTION_API_KEY` | Reserved for future provider integration |

Production deployments must use securely managed secrets, explicitly configured database connectivity, and verified TLS when connecting to public or external MySQL endpoints.

Flyway manages schema migrations. Hibernate validates the schema rather than automatically updating production tables.

---

## 🔌 API Overview

The backend exposes versioned REST endpoints under `/api/v1`.

| Method | Endpoint | Access |
|---|---|---|
| `GET` | `/api/v1/health` | Public |
| `POST` | `/api/v1/auth/register` | Public |
| `POST` | `/api/v1/auth/login` | Public |
| `GET` | `/api/v1/users/me` | Authenticated |
| `GET/POST` | `/api/v1/relationships` | Authenticated |
| `POST` | `/api/v1/interactions/check` | Patient |
| `GET` | `/api/v1/notifications` | Authenticated |
| `GET` | `/api/v1/caregiver/patients` | Authorized caregiver |
| `GET` | `/api/v1/doctor/patients` | Authorized doctor |

The endpoints shown are representative examples; consult the backend implementation for the complete route list and request/response contracts.

### Authentication

Registration accepts a full name, email address, and password. Public registration creates a `PATIENT` account. Login returns a JWT when credentials are valid.

Protected endpoints require a bearer token.

### Medication Interaction Checker

The interaction workflow validates medication ownership before submitting selected medication names to the configured provider.

- `SUCCESS`: Interaction records were returned by the provider.
- `NO_DATA`: No interaction records were returned. This does **not** establish that the combination is safe.
- Provider unavailable: The API reports a provider error.

The current mock provider contains no real clinical interaction data. Results are informational and do not replace advice from a doctor or pharmacist.

### Adherence Analytics

Analytics summarize recorded application events over a selected date range. History supports pagination, and analytics date ranges are bounded.

The application's 70% attention threshold is product logic, not a medically validated measure. Missing records cannot establish whether a dose was taken.

---

## 🔔 Attention Center & Notifications

SmartMed supports recipient-owned in-app notifications for selected application events, including:

- Missed-dose events
- Care-relationship changes
- Month-to-date adherence attention

Notifications are scoped to the authenticated recipient. Duplicate events are prevented using a database-backed uniqueness constraint.

Notifications are informational software events. They are not diagnoses, emergency alerts, risk predictions, or treatment recommendations.

---

## 🧪 Testing

Run backend tests using the Maven Wrapper:

```bash
cd backend
./mvnw test
```

Tests use an in-memory H2 profile where configured.

Before production use or final evaluation, verify migrations and application behaviour against MySQL 8, including relevant authorization, concurrency, and database-integrity scenarios.

---

## ☁️ Production Deployment

SmartMed supports a deployment architecture using:

- **Frontend:** Vercel
- **Backend:** Railway
- **Database:** Railway MySQL

### Frontend

Configure the Vercel project root as `frontend`.

- Build command: `npm run build`
- Output directory: `dist`
- Set `VITE_API_BASE_URL` to the HTTPS origin of the deployed backend, without an `/api` suffix.
- Set `VITE_SMARTMED_TIMEZONE` to the intended application timezone.
- Configure the backend's CORS origins to match the deployed frontend.

### Backend

Configure the Railway service root as `backend`.

- Use Java 17 and the Maven Wrapper.
- Build command: `./mvnw -B package -DskipTests`
- Start command: `java -jar target/smartmed-backend-0.1.0-SNAPSHOT.jar`
- Set `SPRING_PROFILES_ACTIVE=prod`.
- Configure the database host, port, name, username, password, SSL mode, JWT secret, CORS origins, and timezone using deployment-managed variables.

Use private networking and database TLS settings appropriate to the actual connection. Store secrets only in the deployment provider's secret configuration.

Production readiness requires separate validation of deployment configuration, database migrations, security controls, monitoring, and failure handling. A deployment configuration alone is not evidence of a successful production deployment.

---

## ⚠️ Known Limitations

SmartMed is an academic software prototype. Its limitations include:

- The medication interaction checker uses a mock provider without real clinical data.
- The application has not been clinically validated.
- Adherence analytics depend on recorded application data.
- The application has no demonstrated clinical outcome or efficacy results.
- Formal security and privacy assessments are still needed.
- Production deployment and load-test results should be independently verified.
- Authentication rate limiting is instance-local and needs shared enforcement for multi-instance deployments.

### Future Enhancements

- Integrate a validated and appropriately licensed medication interaction data provider.
- Strengthen audit logging and administrative role provisioning.
- Review token-storage and session-security approaches.
- Expand MySQL concurrency and integration testing.
- Conduct formal privacy, security, accessibility, and usability assessments.
- Improve monitoring and operational resilience.

---

## 📁 Project Structure

```text
SmartMed/
├── backend/                  # Java + Spring Boot REST API
├── database/                 # MySQL and database configuration
├── docs/
│   ├── ARCHITECTURE.md
│   ├── AUTH.md
│   ├── PROJECT_STRUCTURE.md
│   └── project-documents/
│       ├── NEW SMARTMED project report.pdf
│       ├── SmartMed_IEEE_Research_Paper.pdf
│       ├── SmartMed_Impactful_Project_Presentation.pptx
│       └── SmartMed_SRS_IEEE_Style.pdf
├── frontend/                 # React + TypeScript + Vite
├── .env.example
├── .gitignore
├── Dockerfile
└── README.md
```

The tree above illustrates the intended documentation layout. Keep the document filenames and paths consistent with the links in this README.

---

## 👥 Project Team

**Department of Computer Science & Engineering**

Arya College of Engineering & I.T., Jaipur  
Rajasthan Technical University, Kota  
Academic Session: 2026–27

| Team Member | Enrollment Number |
|---|---|
| Aditya Mishra | 24EARCS010 |
| Aditya Yadav | 24EARCS011 |
| Badal Verma | 24EARCS038 |
| Bhawna Sen | 24EARCS039 |

---

## 📌 Repository

[GitHub Repository — SmartMed](https://github.com/adityaaa1811/SmartMed)

---

## 📄 License

Add a `LICENSE` file to the repository if you intend to distribute the project under a specific open-source license.

---

**SmartMed — Organize medicines. Preserve dose history. Share care—with consent.**
