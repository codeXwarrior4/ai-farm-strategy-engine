# Database Setup — AI Farm Strategy & Decision Engine

## Prerequisites

- MySQL 8.x installed and running locally.

## One-Time Database & User Setup

Connect to MySQL as root and run:

```sql
-- Create the database
CREATE DATABASE IF NOT EXISTS farm_engine
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Create the application user (replace 'your_password' with your chosen password)
CREATE USER IF NOT EXISTS 'farm_user'@'localhost' IDENTIFIED BY 'your_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON farm_engine.* TO 'farm_user'@'localhost';
FLUSH PRIVILEGES;
```

## Schema Migrations

Schema is managed by **Flyway** inside the Spring Boot backend.  
When the backend starts for the first time it will automatically apply:

- `V1__create_users_table.sql`
- `V2__create_farmer_profiles_table.sql`

You do **not** need to run the migration SQL manually.  
Migration files are in `backend/src/main/resources/db/migration/`.

## Verifying Connectivity

After starting the backend, the health endpoint should return `200 OK`:

```
GET http://localhost:8080/api/v1/health
```

## Notes

- Never commit real database passwords. Use the `backend/.env.example` as template.
- Destructive schema commands (`DROP TABLE`, `DELETE FROM`) are never executed automatically.
- Future phases will add tables for farms, soil profiles, water availability, financial constraints, crops, strategies, and decision audit records.
