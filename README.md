# Attendance Management System

A full-stack attendance management platform with geo-fencing, sales tracking, and role-based dashboards.

## Architecture

| Component | Technology |
|-----------|------------|
| Backend | Spring Boot 4.1, Java 21, JPA, MySQL/PostgreSQL |
| Mobile | Expo SDK 51, React Native 0.74 |
| Deployment | Docker, Render (backend), EAS Build (mobile APK) |

## Roles

- **AGENT** — Check-in/out, geo-fence, sales entry
- **ADMIN** — User/mart management, geo-fence tuning, reports
- **HR** — Attendance + sales compliance dashboards
- **SALES** — Real-time sales dashboard with STOMP WebSocket

## Quick Start (Local Development)

### Prerequisites

- Java 21 (`sudo apt install openjdk-21-jdk`)
- Node.js 20 LTS (`nvm install 20`)
- MySQL 8 (or use `docker-compose up -d`)
- Maven (included via `./mvnw`)

### 1. Backend

```bash
cp .env.example .env
# Edit .env — set DB_PASSWORD and JWT_SECRET

# Start MySQL (optional — uses docker-compose)
docker-compose up -d mysql

# Build and run
./build-backend.sh
# OR manually:
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run
```

Backend runs at `http://localhost:8080`. Health check: `http://localhost:8080/actuator/health`

**Default dev users** (seeded when not in `prod` profile):

| Agent ID | Password | Role |
|----------|----------|------|
| ADMIN001 | admin | ADMIN |
| DEMO001 | password | AGENT |

### 2. Mobile

```bash
cd mobile
cp .env.example .env
# Edit .env — set EXPO_PUBLIC_API_URL and EXPO_PUBLIC_GOOGLE_MAPS_KEY

npm install
npx expo start
```

For a local APK:

```bash
./build-mobile.sh preview
```

## Environment Variables

### Backend (`.env`)

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_URL` | Yes | JDBC connection URL |
| `DB_USERNAME` | Yes | Database username |
| `DB_PASSWORD` | Yes | Database password |
| `DB_DRIVER_CLASS_NAME` | No | Default: MySQL driver |
| `JWT_SECRET` | Yes (prod) | JWT signing key (32+ chars) |
| `SPRING_PROFILES_ACTIVE` | Prod | Set to `prod` for production |
| `CORS_ALLOWED_ORIGINS` | Prod | Comma-separated allowed origins |
| `PORT` | No | Server port (default 8080) |

### Mobile (`mobile/.env`)

| Variable | Required | Description |
|----------|----------|-------------|
| `EXPO_PUBLIC_API_URL` | Yes | Backend API URL including `/api` |
| `EXPO_PUBLIC_GOOGLE_MAPS_KEY` | Yes | Google Maps API key |

## Production Deployment

See [DEPLOYMENT.md](./DEPLOYMENT.md) for the full step-by-step guide.

## API Authentication

All `/api/**` endpoints (except `/api/auth/login` and `/api/auth/exists/**`) require:

```
Authorization: Bearer <jwt-token>
```

Obtain a token via `POST /api/auth/login` with `{ "agentId": "...", "password": "..." }`.

## Project Structure

```
attendance/
├── src/main/java/          Spring Boot backend
├── src/main/resources/     application.properties, db_update.sql
├── mobile/                 Expo React Native app
├── Dockerfile              Production backend image
├── render.yaml             Render.com blueprint
├── docker-compose.yml      Local MySQL + backend
├── build-backend.sh        Backend build script
└── build-mobile.sh         Mobile build script
```

## Security Notes

- Passwords are hashed with BCrypt
- JWT secret must be set via `JWT_SECRET` in production
- `DataInitializer` (default users) is disabled in `prod` profile
- Rate limiting: 120 req/min per IP in production
- Security headers applied to all responses

## License

Private — Dawn Bread Attendance System
