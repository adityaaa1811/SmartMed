# Project Structure

```text
SmartMed/
├── backend/                 # Spring Boot (Java 17, Maven)
│   ├── pom.xml
│   └── src/main/java/com/smartmed/
│       ├── config/          # Security, CORS, properties
│       ├── controller/      # REST controllers
│       ├── dto/             # API DTOs (request/response/error)
│       ├── exception/       # Exceptions + @RestControllerAdvice
│       ├── model/           # JPA entities (Phase 2+)
│       ├── repository/      # Spring Data JPA
│       ├── service/         # Business logic (+ interaction providers)
│       └── util/
│   └── src/main/resources/
│       └── application.properties
├── frontend/                # React + Vite + TypeScript
│   ├── src/
│   │   ├── api/
│   │   ├── components/
│   │   ├── pages/
│   │   └── styles/
│   ├── index.html
│   ├── package.json
│   └── vite.config.ts
├── database/
│   ├── docker-compose.yml   # Local MySQL
│   └── schema.sql           # Reference / bootstrap notes
├── docs/
│   ├── ARCHITECTURE.md
│   └── PROJECT_STRUCTURE.md
├── .env.example
└── README.md
```

Legacy empty folders under `frontend/css`, `frontend/js`, and `frontend/images` may be removed once assets live under `frontend/src` and `frontend/public`.
