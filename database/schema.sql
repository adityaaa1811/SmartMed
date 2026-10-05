-- SmartMed MySQL schema
-- Phase 1: foundation only. Full DDL will be introduced via Flyway/Liquibase in Phase 2+.
-- Use `spring.jpa.hibernate.ddl-auto=update` during early development, then migrate to versioned scripts.

CREATE DATABASE IF NOT EXISTS smartmed
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE smartmed;

-- Intentionally empty until authentication and domain entities land in Phase 2–4.
