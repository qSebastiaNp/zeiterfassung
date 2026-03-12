# Docker Image Deployment Anleitung

## Übersicht

Das Docker Image `zeiterfassung:latest` wurde erfolgreich erstellt und kann auf jeden anderen Server verschoben werden.

## Image Informationen

- **Image Name**: `zeiterfassung:latest`
- **Größe**: ~226MB (compressed)
- **Basis**: `eclipse-temurin:21-jre-alpine`
- **Port**: 8080
- **Health Check**: `/actuator/health`

## Voraussetzungen auf Zielserver

1. **Docker** installiert (Version 20.10 oder höher)
2. **Docker Compose** (optional, für vollständige Umgebung)
3. Mindestens **512MB RAM** für den Container
4. **Port 8080** muss verfügbar sein

## Methode 1: Docker Image Export/Import

### 1. Image exportieren (auf Quellserver)
```bash
docker save zeiterfassung:latest -o zeiterfassung.tar
```

### 2. Image transferieren
```bash
#scp zeiterfassung.tar user@zielserver:/path/
# oder mit anderen Methoden wie rsync, USB-Stick etc.
```

### 3. Image importieren (auf Zielserver)
```bash
docker load -i zeiterfassung.tar
```

## Methode 2: Docker Registry

### 1. Image taggen
```bash
docker tag zeiterfassung:latest deine-registry.com/zeiterfassung:latest
```

### 2. Image pushen
```bash
docker push deine-registry.com/zeiterfassung:latest
```

### 3. Image pullen (auf Zielserver)
```bash
docker pull deine-registry.com/zeiterfassung:latest
```

## Methode 3: Docker Hub (öffentlich)

### 1. Image taggen
```bash
docker tag zeiterfassung:latest deinusername/zeiterfassung:latest
```

### 2. Login und push
```bash
docker login
docker push deinusername/zeiterfassung:latest
```

## Windows-spezifische Environment Variablen

### PowerShell (Windows)
```powershell
# Environment Variable setzen
$env:ZEITERFASSUNG_IMAGE="zeiterfassung:latest"

# Docker Compose starten
docker-compose up -d

# Einmaliger Start mit Variable
$env:ZEITERFASSUNG_IMAGE="zeiterfassung:latest"; docker-compose up -d
```

### CMD (Windows)
```cmd
REM Environment Variable setzen
set ZEITERFASSUNG_IMAGE=zeiterfassung:latest

REM Docker Compose starten
docker-compose up -d

REM Einmaliger Start
set ZEITERFASSUNG_IMAGE=zeiterfassung:latest && docker-compose up -d
```

### Git Bash / WSL (Linux-kompatibel)
```bash
export ZEITERFASSUNG_IMAGE=zeiterfassung:latest
docker-compose up -d
```

## Umgebung starten

### Einfacher Start (nur Anwendung)
```bash
docker run -d \
  --name zeiterfassung \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/zeiterfassung \
  -e SPRING_DATASOURCE_USERNAME=dein-user \
  -e SPRING_DATASOURCE_PASSWORD=dein-passwort \
  zeiterfassung:latest
```

### Mit Docker Compose (empfohlen)
Erstelle `docker-compose.yml` auf Zielserver:

```yaml
version: '3.8'

services:
  zeiterfassung:
    image: zeiterfassung:latest
    ports:
      - "8080:8080"
    environment:
      # Database
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/zeiterfassung
      SPRING_DATASOURCE_USERNAME: zeiterfassung
      SPRING_DATASOURCE_PASSWORD: dein-passwort
      # Mail
      ZEITERFASSUNG_MAIL_FROM: zeiterfassung@deine-domain.de
      SPRING_MAIL_HOST: mail-server
      SPRING_MAIL_PORT: 587
      # Keycloak (optional)
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_DEFAULT_CLIENT-ID: zeiterfassung
      SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_DEFAULT_CLIENT-SECRET: dein-secret
      SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_DEFAULT_ISSUER-URI: http://keycloak:8090/realms/zeiterfassung
    depends_on:
      - postgres
    restart: unless-stopped

  postgres:
    image: postgres:16.1
    environment:
      POSTGRES_DB: zeiterfassung
      POSTGRES_USER: zeiterfassung
      POSTGRES_PASSWORD: dein-passwort
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  postgres_data:
```

Starten mit:
```bash
docker-compose up -d
```

## Wichtige Environment Variablen

### Database (erforderlich)
- `SPRING_DATASOURCE_URL`: PostgreSQL Verbindung
- `SPRING_DATASOURCE_USERNAME`: DB Benutzer
- `SPRING_DATASOURCE_PASSWORD`: DB Passwort

### Mail (optional)
- `ZEITERFASSUNG_MAIL_FROM`: Absender E-Mail
- `SPRING_MAIL_HOST`: Mail Server
- `SPRING_MAIL_PORT`: Mail Port

### OAuth2/Keycloak (optional)
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_DEFAULT_CLIENT-ID`: Client ID
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_DEFAULT_CLIENT-SECRET`: Client Secret
- `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_DEFAULT_ISSUER-URI`: Keycloak URL

## Health Check

Der Container hat einen eingebauten Health Check:
```bash
curl http://localhost:8080/actuator/health
```

## Logs anzeigen
```bash
docker logs zeiterfassung
# oder mit follow
docker logs -f zeiterfassung
```

## Troubleshooting

### 1. Database Connection Error
Stelle sicher dass die Datenbank läuft und Verbindungsdaten korrekt sind.

### 2. Port bereits belegt
Ändere den Port mapping:
```bash
docker run -p 9080:8080 zeiterfassung:latest
```

### 3. Memory Probleme
Erhöhe die Docker Memory limits:
```bash
docker run --memory=1g zeiterfassung:latest
```

## Backup & Restore

### Database Backup
```bash
docker exec postgres pg_dump -U zeiterfassung zeiterfassung > backup.sql
```

### Database Restore
```bash
docker exec -i postgres psql -U zeiterfassung zeiterfassung < backup.sql
```

## Updates

### 1. Neues Image bauen und deployen
```bash
# Build neues Image
docker build -t zeiterfassung:v2.0.0 .

# Alten Container stoppen
docker stop zeiterfassung

# Neuen Container starten
docker run -d --name zeiterfassung-new -p 8080:8080 zeiterfassung:v2.0.0

# Alten Container entfernen (wenn alles läuft)
docker rm zeiterfassung
```

### 2. Mit Docker Compose
```bash
docker-compose pull
docker-compose up -d
```

## Sicherheitshinweise

1. **Keine Passwörter in Image** - Environment Variablen verwenden
2. **Non-root User** - Container läuft als `appuser`
3. **Read-only Filesystem** (optional): `--read-only --tmpfs /tmp`
4. **Resource Limits** setzen: `--memory=1g --cpus=1.0`

## Support

Bei Problemen:
1. Logs prüfen: `docker logs zeiterfassung`
2. Health Check testen: `curl http://localhost:8080/actuator/health`
3. Network Connectivity prüfen
4. Environment Variablen überprüfen
