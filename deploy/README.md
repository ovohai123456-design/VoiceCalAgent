# VoiceCal Docker deployment

This directory deploys the Vue frontend and Spring Boot backend together. Docker Compose creates the internal network automatically. Keep `.env` on the server and do not commit it.

## 1. Build locally

From the project root:

```powershell
mvn clean package -DskipTests
Copy-Item target/VoiceCalAgent-1.0.0.jar deploy/app.jar -Force

Set-Location VoiceCal
npm ci
npm run build
Set-Location ..

if (Test-Path deploy/web/dist) { Remove-Item deploy/web/dist -Recurse -Force }
Copy-Item VoiceCal/dist deploy/web/dist -Recurse
```

Upload the entire `deploy` directory to the Linux server.

## 2. Configure once on the server

```bash
cd deploy
cp .env.example .env
vi .env
```

Set the Nacos address, database connection, SMTP credentials, DashScope API key and Amap key.

## 3. Start or update both services

```bash
cd deploy
bash deploy.sh
```

You can also run Compose directly:

```bash
docker compose up -d --build
```

The frontend listens on `${WEB_PORT:-80}` and proxies `/api` to the backend container. The backend also exposes `${APP_PORT:-8080}` for debugging.

## Useful commands

```bash
docker compose logs -f
docker compose ps
docker compose restart
docker compose down
```
