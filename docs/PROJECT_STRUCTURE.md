# Project structure

```text
SmartMed/
├── backend/                       # Java 17, Spring Boot, Maven
│   └── src/
│       ├── main/java/com/smartmed/
│       │   ├── config/            # Security, CORS, typed properties, Clock
│       │   ├── controller/        # REST controllers
│       │   ├── dto/               # Request, response, and error DTOs
│       │   ├── entity/            # JPA entities and enums
│       │   ├── exception/         # Domain exceptions and API advice
│       │   ├── repository/        # Spring Data repositories and projections
│       │   ├── security/          # JWT and auth throttling
│       │   └── service/           # Transactional business logic
│       ├── main/resources/
│       │   ├── application*.properties
│       │   └── db/migration/      # Authoritative Flyway schema history
│       └── test/java/             # Integration, security, and migration tests
├── frontend/                      # React + Vite + TypeScript
│   └── src/
│       ├── api/                   # Typed API clients
│       ├── auth/                  # Browser token handling
│       ├── components/            # Dashboards and reusable UI
│       ├── pages/                 # Main application page
│       ├── styles/                # Global CSS and design tokens
│       └── utils/                 # Calendar/date utilities
├── database/
│   └── docker-compose.yml         # Local-only MySQL, loopback-bound
├── docs/
├── .env.example                   # Local examples; contains no usable secrets
└── README.md
```

`database/schema.sql` remains only as a legacy pointer to Flyway; do not use it to initialize or upgrade a database.
