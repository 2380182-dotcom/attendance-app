# Deployment Guide

Step-by-step instructions for deploying the Attendance Management System to production.

## Overview

```
┌─────────────┐     HTTPS      ┌──────────────────┐     JDBC     ┌────────────┐
│  Mobile APK │ ──────────────▶│  Render Backend  │ ────────────▶│ PostgreSQL │
│  (EAS Build)│   REST + STOMP │  (Docker/Java21) │              │  (Render)  │
└─────────────┘                └──────────────────┘              └────────────┘
```

---

## Part 1: Deploy Backend to Render

### Step 1 — Prepare secrets

Generate a JWT secret:

```bash
openssl rand -base64 48
```

Save the output — you will set it as `JWT_SECRET` on Render.

### Step 2 — Push to GitHub

Ensure your repository contains:

- `Dockerfile`
- `render.yaml`
- All backend source changes

### Step 3 — Create Render Blueprint

1. Go to [render.com](https://render.com) → **New** → **Blueprint**
2. Connect your GitHub repository
3. Render reads `render.yaml` and creates:
   - PostgreSQL database (`attendance-db`)
   - Web service (`attendance-backend`)

### Step 4 — Set environment variables on Render

In the Render dashboard for `attendance-backend`, verify these env vars (most are auto-set by `render.yaml`):

| Variable | Value |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | Your generated secret |
| `CORS_ALLOWED_ORIGINS` | `*` (or your domain) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` (first deploy), then `validate` |
| `DB_URL` | Auto from database |
| `DB_USERNAME` | Auto from database |
| `DB_PASSWORD` | Auto from database |
| `DB_DRIVER_CLASS_NAME` | `org.postgresql.Driver` |

### Step 5 — Verify deployment

```bash
curl https://your-backend.onrender.com/actuator/health
# Expected: {"status":"UP"}
```

### Step 6 — Create admin user

`DataInitializer` is disabled in production. Create the first admin manually:

```bash
curl -X POST https://your-backend.onrender.com/api/auth/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <temporary-admin-token-if-needed>" \
  -d '{
    "agentId": "ADMIN001",
    "name": "System Admin",
    "email": "admin@yourcompany.com",
    "password": "YOUR_SECURE_PASSWORD",
    "role": "ADMIN",
    "department": "MANAGEMENT"
  }'
```

> **Note:** `/api/auth/register` requires an ADMIN token. For the very first user, temporarily allow open registration or insert directly into the database.

**Alternative — direct SQL insert:**

```sql
INSERT INTO agent (agent_id, name, email, password, role, department, is_active, created_at, created_by)
VALUES ('ADMIN001', 'Admin', 'admin@company.com', '$2a$10$...bcrypt-hash...', 'ADMIN', 'MANAGEMENT', true, NOW(), 'MANUAL');
```

Generate bcrypt hash:

```bash
# Using Python
python3 -c "import bcrypt; print(bcrypt.hashpw(b'YourPassword', bcrypt.gensalt()).decode())"
```

---

## Part 2: Build Mobile APK with EAS

### Step 1 — Install EAS CLI

```bash
npm install -g eas-cli
eas login
```

### Step 2 — Configure environment variables

Edit `mobile/eas.json` production profile:

```json
"env": {
  "EXPO_PUBLIC_API_URL": "https://your-backend.onrender.com/api",
  "EXPO_PUBLIC_GOOGLE_MAPS_KEY": "YOUR_GOOGLE_MAPS_KEY_HERE"
}
```

Or set secrets in EAS dashboard (recommended):

```bash
cd mobile
eas secret:create --name EXPO_PUBLIC_API_URL --value "https://your-backend.onrender.com/api"
eas secret:create --name EXPO_PUBLIC_GOOGLE_MAPS_KEY --value "YOUR_GOOGLE_MAPS_KEY_HERE"
```

### Step 3 — Google Maps API key

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Enable **Maps SDK for Android** and **Maps SDK for iOS**
3. Create an API key restricted to your app package: `com.raffaay.attendancemobile`
4. Set as `EXPO_PUBLIC_GOOGLE_MAPS_KEY`

### Step 4 — Production signing (Android)

```bash
keytool -genkeypair -v -storetype PKCS12 \
  -keystore attendance-release.keystore \
  -alias attendance -keyalg RSA -keysize 2048 -validity 10000

cd mobile
eas credentials
# Follow prompts to upload your keystore
```

### Step 5 — Build APK

```bash
cd mobile
./build-mobile.sh production
# OR:
eas build --platform android --profile production
```

Download the APK from the EAS dashboard link when the build completes.

### Step 6 — Install and configure

1. Install APK on device
2. Open app → **Server Settings** (gear icon)
3. Enter: `https://your-backend.onrender.com/api`
4. Test connection → Save
5. Login with your admin credentials

---

## Part 3: Local Development with Docker

```bash
# Start MySQL + backend
docker-compose up -d

# Backend available at http://localhost:8080
curl http://localhost:8080/actuator/health
```

---

## Part 4: Post-Deployment Checklist

- [ ] `/actuator/health` returns `UP`
- [ ] Login works from mobile app
- [ ] Check-in with GPS works
- [ ] Geo-fence auto check-in/out works (background location permission granted)
- [ ] Sales dashboard receives real-time STOMP updates
- [ ] HR dashboard loads attendance + sales data
- [ ] Excel report export downloads
- [ ] Change `SPRING_JPA_HIBERNATE_DDL_AUTO` to `validate` after first prod deploy
- [ ] Rotate `JWT_SECRET` if ever exposed
- [ ] Remove any test/demo accounts

---

## Troubleshooting

### Backend won't start on Render

- Check logs in Render dashboard
- Verify `JWT_SECRET` is set (required in prod profile)
- Verify database env vars are linked

### Mobile can't connect

- Ensure URL ends with `/api`
- For HTTPS backend, cleartext is disabled in production builds
- Check CORS: set `CORS_ALLOWED_ORIGINS=*` on backend for mobile apps

### Maps not loading

- Verify `EXPO_PUBLIC_GOOGLE_MAPS_KEY` is set in EAS build env
- Rebuild APK after setting the key (`npx expo prebuild --clean` if building locally)

### STOMP WebSocket not connecting

- Backend `/ws` endpoint must be reachable (not blocked by proxy)
- Render free tier supports WebSockets
- App falls back to 15-second polling if STOMP fails

### Java version error locally

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
java -version  # must show 21.x
```

---

## Environment Variable Reference (Production)

| Service | Variable | Example |
|---------|----------|---------|
| Backend | `SPRING_PROFILES_ACTIVE` | `prod` |
| Backend | `JWT_SECRET` | `<random-48-byte-base64>` |
| Backend | `DB_URL` | `jdbc:postgresql://...` |
| Backend | `CORS_ALLOWED_ORIGINS` | `https://your-app.onrender.com` |
| Mobile | `EXPO_PUBLIC_API_URL` | `https://your-backend.onrender.com/api` |
| Mobile | `EXPO_PUBLIC_GOOGLE_MAPS_KEY` | `AIzaSy...` |
