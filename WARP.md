# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Overview

`camera-server` is a Java 8 Spring Boot 2.7 service that provides the backend for a smart camera application: device management, WebRTC live streaming, MQTT integration, user auth, and payments/cloud storage.

## Build, run, and test

### Prerequisites

- JDK 8
- Apache Maven available as `mvn`
- A MySQL instance (schema is defined in `src/main/resources/db/init.sql`)

### Build

From the repository root:

- Package the JAR:
  - `mvn clean package`
- Package without running tests:
  - `mvn clean package -DskipTests`

The main artifact is `target/camera-server-1.0.0.jar`.

### Run locally

- Run via Maven (auto-rebuild on code changes when used with an IDE):
  - `mvn spring-boot:run`
- Run the built JAR directly:
  - `java -jar target/camera-server-1.0.0.jar`

By default the service listens on port `8080` (configured in `src/main/resources/application.properties`).

For local development, update `spring.datasource.*` and `mqtt.*` properties in `src/main/resources/application.properties` (or profile-specific config) to point at your own MySQL and MQTT endpoints instead of any hard-coded remote services.

### Tests

This project currently does not define test sources under `src/test`, but the standard Maven commands still apply once tests are added:

- Run the full test suite:
  - `mvn test`
- Run a single test class:
  - `mvn -Dtest=ClassName test`
- Run a single test method:
  - `mvn -Dtest=ClassName#methodName test`

### Docker

A basic runtime image is defined in `Dockerfile` and expects a prebuilt JAR at `target/camera-server-1.0.0.jar`:

1. Build the JAR:
   - `mvn clean package -DskipTests`
2. Build the image:
   - `docker build -t camera-server .`
3. Run the container (exposes port 80 inside the container):
   - `docker run -p 80:80 camera-server`

The container entrypoint is `java -jar app.jar` inside `/app`.

### API documentation and references

Once the service is running locally:

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Additional in-repo API documentation lives under `src/main/resources/docs`:

- `app-api.md` – app-facing API overview (auth, user, device endpoints and common response structure).
- `api_spec.md` – canonical API specification with base URLs, error codes, and detailed endpoint contracts.
- `WEBRTC-API.md` – WebRTC live streaming and PTZ signalling over HTTP↔MQTT, including end-to-end flows.
- `webrtc-app-integration.md` – WebRTC integration guide for app/front-end (ICE/TURN config, DataChannel commands, fileindex protocol).

These documents should be consulted before changing public HTTP or WebRTC-related behavior.

## High-level architecture

### Application entrypoint and configuration

- `src/main/java/com/pura365/camera/Application.java` is the Spring Boot entrypoint.
- Configuration components:
  - `config/WebConfig` registers `AuthInterceptor` as a global Spring MVC interceptor. It enforces JWT auth on most APIs while whitelisting device HTTP callbacks (`/get_time`, `/get_info`, `/reset_device`, `/send_msg`), MQTT/WebRTC internal APIs, static assets, and device-id tooling endpoints.
  - `config/OpenApiConfig` configures the OpenAPI/Swagger metadata and server URLs used by the generated docs.
  - `config/OAuthConfig` binds `oauth.*` properties into strongly typed WeChat/Apple/Google login configuration objects used by the auth service.

### Package and layer structure

Under `com.pura365.camera` the code follows a layered structure:

- `controller.*` – HTTP controllers (Spring MVC REST endpoints), grouped by domain:
  - `controller.app` – app-facing endpoints such as user auth (`AuthController`) and app utilities (`AppController` for version check, feedback, etc.).
  - `controller.device` – device management APIs for the app (list/add/delete devices, device details, settings, local video lists, PTZ control). These interact directly with repositories and `MqttMessageService` for operations like PTZ via MQTT.
  - `controller.admin` – administrative and vendor/production endpoints (e.g., batch device production and vendor management).
  - `controller.internal` – internal integration endpoints used by local tools and other backend components, such as WebRTC signalling (`WebRtcController`), MQTT control, and internal camera/cloud coordination.
  - Utility-like classes such as `JWTUtil` are colocated under `controller` where they support controller logic.

- `service.*` – business logic and external integration layer:
  - `AuthService` encapsulates user registration/login, password hashing, third-party OAuth flows (WeChat/Apple/Google), and JWT issuance/refresh and parsing. It is used both by controllers and `AuthInterceptor`.
  - `CameraService` implements device-initiated HTTP flows like `/get_info`, `/reset_device`, and `/send_msg`, ensuring each device is persisted and its metadata is kept up to date when devices contact the server directly.
  - `MqttMessageService` is the core MQTT bridge and WebRTC signalling hub:
    - Manages the Paho MQTT client lifecycle, connection, and subscription to `camera/pura365/+/device`.
    - Decrypts and encrypts device messages via `MqttEncryptService`, using per-device SSID information.
    - Dispatches MQTT messages by "code" into handlers that update device online status, firmware/SSID, and other state in the database.
    - Maintains in-memory WebRTC offer and candidate caches keyed by `sid` and exposes methods for controllers to retrieve and drain them.
    - Exposes `sendToDevice`, `requestDeviceInfo`, `requestWebRtcOffer`, `sendWebRtcAnswer`, and `sendWebRtcCandidate` to let HTTP controllers drive device behavior over MQTT.
    - Provides a lightweight listener mechanism to notify WebSocket or other components of WebRTC signalling events.
  - `PaymentService` coordinates cloud-storage-related payment flows: creating orders linked to devices and cloud plans, tracking status, and mocking integrations with WeChat Pay, PayPal, and Apple Pay. It works with `CloudPlan`, `PaymentOrder`, `PaymentWechat`, and `UserDevice` entities and repositories.
  - Other services (e.g., `DeviceProductionService`, `DeviceSsidService`, `MessageService`, `OAuthService`) encapsulate specific domains like device production batches, Wi‑Fi SSID caching, in-app messaging, and OAuth provider interactions.

- `domain.*` – MyBatis-Plus entity classes mapping to database tables defined in `src/main/resources/db/init.sql`:
  - Core device entities (`Device`, `NetworkConfig`, `DeviceStatusHistory`, etc.), user and binding entities (`User`, `UserDevice`, `UserAuth`, `UserToken`), WebRTC session records (`WebRtcSession`), app message/version entities (`AppMessage`, `AppVersion`), cloud plans/subscriptions (`CloudPlan`, `CloudSubscription`, `CloudVideo`), and payment-related entities.

- `repository.*` – MyBatis-Plus `BaseMapper` interfaces for each domain entity (e.g., `DeviceRepository`, `UserRepository`, `CloudPlanRepository`, `PaymentOrderRepository`). Controllers and services build queries using `QueryWrapper` / `LambdaQueryWrapper` rather than custom XML mappers in most cases.

- `model.*` – DTOs and view models decoupling HTTP/MQTT payloads from persistence models:
  - `ApiResponse` provides the common `{ code, message, data }` response wrapper used throughout controllers.
  - Device HTTP DTOs like `GetInfoRequest`, `GetInfoResponse`, `ResetDeviceRequest`, and `SendMsgRequest` represent payloads sent by camera firmware when calling HTTP endpoints.
  - `model.mqtt.*` defines message structures for parsed MQTT payloads (`MqttBaseMessage`, `MqttCode10Message`, `MqttDeviceInfoMessage`, `WebRtcMessage`, etc.), which `MqttMessageService` uses to route and handle codes.
  - `model.payment.*` contains request/response types for payment flows (`CreateOrderRequest`, `OrderVO`, `WechatPayVO`, `PaypalPayVO`, `ApplePayVO`, etc.).

- `util.*` – shared utilities:
  - `TimeValidator` centralizes timestamp validation and generation of current timestamps used in MQTT/WebRTC messages.
  - `MqttEncryptService` / `MqttEncryptUtil` handle the symmetric encryption/decryption and hex logging utilities for MQTT payloads.
  - `PairConfigUtil` contains helpers related to device pairing and configuration flows.

### HTTP API surface and auth behavior

- The external app-facing API is primarily implemented by controllers under `controller.app` and `controller.device`, and the contracts are documented in `app-api.md` and `api_spec.md`.
- `AuthInterceptor` is registered globally to enforce JWT-based auth via the `Authorization: Bearer <token>` header. It:
  - Extracts the user ID from the access token via `AuthService` and stores it as `currentUserId` on the `HttpServletRequest`.
  - Whitelists login endpoints under `/api/app/auth/**`, device-origin HTTP endpoints (`/get_time`, `/get_info`, `/reset_device`, `/send_msg`), MQTT/WebRTC and internal debug endpoints, static content, and health/error endpoints.
- Controllers that require authenticated users typically accept `@RequestAttribute("currentUserId") Long currentUserId` parameters rather than re-parsing tokens.

### WebRTC and MQTT integration

- WebRTC live streaming and PTZ control are orchestrated across HTTP controllers, `MqttMessageService`, and device firmware:
  - App/clients call HTTP endpoints (e.g., `/api/mqtt/device/{deviceId}/webrtc/offer`, `/api/internal/webrtc/offer/{sid}`, `/api/internal/webrtc/candidates/{sid}`) documented in `WEBRTC-API.md` and `webrtc-app-integration.md`.
  - Controllers translate these HTTP requests into MQTT messages (codes 23/24/25 for offer/answer/candidate) using `MqttMessageService`.
  - Incoming MQTT messages are decrypted, parsed into `WebRtcMessage` objects, cached in `webrtcOfferCache`/`webrtcCandidateCache`, and optionally forwarded via WebSocket listeners.
  - Internal controllers under `controller.internal` expose lightweight polling endpoints to retrieve offers and candidates, designed to be consumed by the WebRTC demo page and mobile apps.

### Persistence and database

- The relational schema is defined in `src/main/resources/db/init.sql` and includes tables for:
  - Devices and connectivity (device metadata, Wi‑Fi/MQTT/AI configuration).
  - Network configuration attempts and device status history.
  - Users and user–device bindings.
  - WebRTC sessions.
  - App messages and version metadata.
  - Cloud storage plans/subscriptions and payment records.
- Entities in `domain.*` and mappers in `repository.*` are expected to stay consistent with this schema; use `init.sql` as the authoritative reference when adding or modifying persistent fields.

### Static assets and tools

- `src/main/resources/static` hosts front-end assets used for local debugging and tools:
  - `webrtc-demo.html` (plus CSS/JS/image assets) is a self-contained WebRTC demo page that exercises the signalling and streaming flows.
  - `device-id-tool.html` is a utility page for device/serial-number related workflows.
- Images and auxiliary assets under `src/main/resources/docs/pics` and nested `docs` subdirectories serve the Markdown documentation and do not affect backend behavior.
