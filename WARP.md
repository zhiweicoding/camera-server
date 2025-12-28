# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Build & Run Commands

```bash
# Build
mvn clean package

# Run locally (port 8080)
mvn spring-boot:run

# Run JAR directly
java -jar target/camera-server-1.0.0.jar

# Docker deployment (port 80)
docker-compose up -d

# View logs
tail -f logs/camera-server.log
```

## Database

Initialize MySQL database:
```bash
mysql -u root -p < src/main/resources/db/init.sql
```

Configuration in `src/main/resources/application.properties`:
- MySQL with MyBatis-Plus (not JPA)
- Uses underscore-to-camelCase mapping

## Project Architecture

**Spring Boot 2.7 + Java 8** camera backend server for Pura365 IoT cameras.

### Core Components

```
com.pura365.camera/
├── controller/
│   ├── admin/      # Admin dashboard APIs (/api/admin/*)
│   ├── app/        # Mobile app APIs (/api/app/*)
│   ├── device/     # Device management APIs (/api/device/*)
│   └── internal/   # Camera-to-server & internal APIs
├── service/        # Business logic
├── domain/         # Entity classes (MyBatis-Plus)
├── repository/     # Database mappers
├── model/          # DTOs & request/response models
├── config/         # Configuration classes
└── util/           # Utilities
```

### Key Services

| Service | Purpose |
|---------|---------|
| `MqttMessageService` | MQTT communication with cameras (pub/sub) |
| `MqttEncryptService` | AES-128-ECB encryption for MQTT messages |
| `DeviceSsidService` | Manages WiFi SSID cache for MQTT encryption keys |
| `CameraService` | Camera HTTP API handling |
| `AuthService` | JWT auth + OAuth (WeChat/Apple/Google) |
| `CloudStorageService` | Qiniu (China) / Vultr S3 (overseas) storage |
| `DeviceShareService` | Device sharing between users |

## API Structure

| Module | Base Path | Description |
|--------|-----------|-------------|
| Admin | `/api/admin/*` | Device production, vendors, billing |
| App | `/api/app/*` | User auth, device binding, payments |
| Device | `/api/device/*` | Device CRUD, network config, playback |
| Internal | Root `/` + `/api/internal/*` | Camera APIs, MQTT control, WebRTC |

Camera HTTP endpoints use root path:
- `POST /get_time` - Server timestamp
- `POST /get_info` - Device configuration
- `POST /reset_device` - Device reset
- `POST /send_msg` - Push notifications

Swagger UI: `http://localhost:8080/swagger-ui.html`

## MQTT Communication

**Topics:**
- Subscribe: `camera/pura365/+/device` (camera → server)
- Publish: `camera/pura365/{deviceId}/master` (server → camera)

**Encryption:** AES-128-ECB with WiFi SSID's MD5 (16 bytes) as key.

SSID sources (priority order):
1. From `/get_info` or `/reset_device` HTTP requests
2. Manual API: `POST /api/mqtt/device/{deviceId}/register-ssid?ssid=XXX`
3. Database `device.ssid` column

## JWT Token Format

Uses custom format (not standard JWT):
```
payload.signature  OR  header.payload.signature
```
- Payload: Base64-encoded JSON
- Secret: `secret.ipcam.pura365.app`
- Timestamp validity: 5 minutes

## WebRTC Signaling

WebRTC SDP exchange goes through MQTT:
1. App requests offer via HTTP → Server sends MQTT CODE 23
2. Camera responds with offer (CODE 151)
3. App sends answer via HTTP → Server sends MQTT CODE 24
4. ICE candidates exchanged via CODE 25

DataChannel commands (direct P2P): `left`, `right`, `up`, `down`, `mute`, `unmute`, `live`, `replay {timestamp} {channel}`, `fileindex {YYYY-mm-dd}`

## Configuration

Key properties in `application.properties`:
- `mqtt.broker.url` - MQTT broker address
- `oauth.wechat.*` - WeChat login credentials
- `storage.qiniu.*` - China cloud storage
- `storage.vultr.*` - Overseas S3 storage
- `device.health.*` - Online status check settings
