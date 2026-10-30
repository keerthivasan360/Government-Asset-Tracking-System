# AssetGuard — Government Asset Tracking System

React frontend (Vite + React Router), Spring Boot REST backend, Spring Security, JPA, and H2/MySQL. All pages are React components; JSP and Jasper have been removed. The production JAR serves the React build and API together on port 8080.

## Build and run

Requirements: Node.js 22.12+ or 24+, npm, Maven and Java 17–25 (recommended for Spring Boot 3.5). This workspace has also been verified with Java 26.

From the project folder in PowerShell:

```powershell
./scripts/build.ps1
java -jar target/asset-tracker-1.0.0.jar
```

The build script installs locked frontend dependencies, runs React tests, builds React, then runs backend tests and packages the JAR. Stop a running application before a clean build on Windows. To build manually:

```powershell
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
mvn clean package
```

Always rebuild React before packaging frontend changes; Maven copies `frontend/dist` into the JAR. Open http://localhost:8080. Existing users and asset data in `data/` are retained when upgrading from the JSP version. Existing passwords remain valid.

On a fresh database, optionally set `ADMIN_PASSWORD` and `OFFICER_PASSWORD` environment variables before first startup. Otherwise unique initial passwords are printed once in the startup console. Sign in as `admin` or `officer`. BCrypt password hashes are stored in `users`. These environment variables initialize new users only; they do not reset existing passwords.

## React development

Start the Spring Boot backend on port 8080, then in a second terminal:

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open the Vite URL (normally http://127.0.0.1:5173). Vite proxies `/api` to Spring Boot so session cookies and CSRF protection work on the same browser origin. React updates automatically while editing. Production needs only the packaged JAR; a separate Node server is unnecessary.

## Features and demo

1. Sign in as admin and register a phone with a unique serial number and Bluetooth MAC in `AA:BB:CC:DD:EE:FF` format.
2. Click **Connect**, then **Disconnect**. The observation and timestamp are logged, and disconnection creates an open alert.
3. Click **Connect** again. The alert resolves and the reconnect is logged.
4. Visit **Tracking history** to inspect the latest 100 changes.
5. Sign in as officer to demonstrate read-only access. The API enforces admin permissions for creation, updates, deletion and simulation.

The React dashboard and tracking history poll every five seconds without reloading the page or clearing the search. Failed refreshes display an error and retain the last received data. Forms preserve inputs on validation errors. Initial status is UNKNOWN. Repeated identical observations do not duplicate logs or alerts. An initial DISCONNECTED observation also creates an alert. Deleting an asset retains historical snapshots and closes outstanding alerts. A registered Bluetooth address cannot be changed; create a new asset record for a different device.

## API and security

| Endpoint | Purpose |
| --- | --- |
| `GET /api/session` | Current user, role and session CSRF token; also available before login |
| `POST /api/login` | Username/password form login; returns 204 or 401 |
| `POST /api/logout` | Sign out; returns 204 |
| `GET /api/summary` | Assets, recent alerts, open alert count and monitor mode |
| `GET /api/logs` | Latest 100 tracking events |
| `GET /api/assets/{id}` | Asset details |
| `POST /api/assets` | Register an asset using JSON (admin) |
| `PUT /api/assets/{id}` | Edit an asset using JSON (admin) |
| `DELETE /api/assets/{id}` | Delete an asset (admin) |
| `POST /api/assets/{id}/simulate/{status}` | Demo CONNECTED/DISCONNECTED observation (admin) |
| `POST /api/assets/{id}/shift` | Shift an asset to another facility zone (triggers alert if zone is unauthorized) |
| `GET /api/blockchain` | Query the full immutable local blockchain ledger and integrity status |
| `GET /api/blockchain/verify` | Re-audit and cryptographically verify all SHA-256 block hashes |
| `GET /api/facility-zones` | Retrieve standard facility locations and security zones |

## Local Blockchain & Asset Movement Geo-Fencing

1. **Moveable Places / Permitted Zones**: When registering or editing an asset, administrators define its `currentLocation` (e.g. `HQ Main Facility`) and `allowedLocations` (comma-separated permitted zones, e.g. `HQ Main Facility, Dispatch Office, Server Room B-4`).
2. **Interactive Movement Simulator (`/simulation`)**: Select any asset and shift it across facility zones.
   - **Authorized Move**: Shifting to a permitted zone updates the station, logs the event, and mines an authorized `LOCATION_SHIFT` block.
   - **Unauthorized Movement (Breach)**: Shifting to a restricted zone triggers a **CRITICAL SECURITY BREACH ALERT** and mines an immutable `SECURITY_BREACH_ALERT` fraud block on the blockchain.
3. **Local Cryptographic Blockchain Ledger (`/blockchain`)**:
   - Each operation (Genesis, registration, relocation, status change, security breach) is sealed in an immutable block with a **SHA-256 hash**, **previous block hash**, and **proof-of-work nonce**.
   - Built-in on-demand **Cryptographic Audit** recalculates hashes across the entire chain from Genesis to verify tamper-proof integrity.

The frontend fetches a fresh CSRF token from `/api/session` before each mutation and sends it in the returned header name. This handles token rotation after login/logout. API calls require authentication except session and login. Static React routes are public shells with no embedded private data. Unauthorized API requests return 401 and React returns to the login page. Roles are enforced server-side. Input DTOs prevent callers from changing observed status or creation IDs.

The app binds to localhost. For deployment beyond this laptop, configure HTTPS, appropriate cookie security and database access controls. Keep the `data/` folder private and backed up.

## MySQL

Create a database and dedicated application user with permissions on that database, then:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/government_assets'
$env:DB_USERNAME='asset_app'
$env:DB_PASSWORD='your-database-password'
java -jar target/asset-tracker-1.0.0.jar --spring.profiles.active=mysql
```

Hibernate creates `users`, `assets`, `tracking_logs` and `alerts`. `ddl-auto=update` is convenient for this project; use versioned migrations for a production deployment. H2 is the default local database; MySQL requires a running database and has not been hardware/environment verified here.

## Bluetooth modes and limitations

The application supports three tracking modes configured in `application.properties` via `tracking.mode=${TRACKING_MODE:windows}`:

1. **Windows Mode (`TRACKING_MODE=windows`, default)**:
   - Polls Windows Bluetooth stack via PowerShell `Get-PnpDevice -Class Bluetooth` every 5 seconds.
   - Derives device hardware ID from the asset's Bluetooth MAC address (e.g. `7C:F0:E5:4B:73:2C` → InstanceId match on `7CF0E54B732C`).
   - If device status is `OK`, registers status as `CONNECTED`.
   - If the mobile phone moves out of range or Bluetooth is turned off, status transitions to `DISCONNECTED`, triggering:
     - An immediate alert: `"Connection lost — out of range or unavailable"`
     - A mined `STATUS_ALERT` cryptographic block on the local blockchain ledger
     - A desktop / browser notification popup (`AssetGuard — Bluetooth Alert`) via the Web Notifications API
   - When the phone reconnects, the alert is automatically resolved and a `CONNECTED` block is mined.

2. **Linux Mode (`TRACKING_MODE=linux`)**:
   - Queries `bluetoothctl info <MAC>` every 5 seconds. Requires BlueZ and an active Bluetooth profile.

3. **Demo Mode (`TRACKING_MODE=demo`)**:
   - Disables automatic OS background polling so you can use the Movement Simulator and manual Connect/Disconnect buttons to simulate behaviors without physical hardware.

### Testing Mobile Phone Bluetooth Disconnection on Windows:
1. Ensure your mobile phone (e.g., `keerthi's oneplus`) is paired with your Windows laptop.
2. The phone is registered in AssetGuard with its MAC address (`7C:F0:E5:4B:73:2C`).
3. Open `http://127.0.0.1:8080/` in your browser and sign in (`admin` / `admin`).
4. Allow browser notifications when prompted.
5. Turn off Bluetooth on your phone (or walk out of range): within 5-10 seconds, the system logs the disconnection, fires a notification popup, displays the red alert on the dashboard, and seals the event on the blockchain.


```sh
TRACKING_MODE=linux java -jar target/asset-tracker-1.0.0.jar
```

The monitor checks each registered device every five seconds, with fixed delay after each batch. Probe timeouts, missing tools and unreadable states are logged and preserve the last observation, which can become stale. Only valid connected/disconnected observations change status. No physical Bluetooth hardware has been validated here.

**Windows Bluetooth detection is not implemented.** Pairing/PnP presence alone does not reliably establish phone range. Validate a Bluetooth profile API or phone-side heartbeat with the actual phone before adding an adapter in `BluetoothMonitor`. A dropped connection may mean unavailable, powered off, disconnected profile or potentially out of range; it is not proof that an asset is missing. Range depends on hardware and surroundings. This is an indoor proximity demonstration, not a GPS tracker.

## Structure

- `frontend/src`: React pages, routing, API client, styles and component tests
- `frontend/vite.config.js`: React bundling and development API proxy
- `src/main/java/gov/assets`: entities, security, REST API, tracking service and scheduler
- `src/main/resources`: backend configuration and MySQL profile
- `src/test/java/gov/assets`: backend integration tests
- `scripts/build.ps1`: complete Windows build and test workflow

Email/SMS, maps, GPS, department-level permissions and maintenance workflows remain future enhancements.
