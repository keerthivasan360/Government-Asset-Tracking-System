# AssetGuard — System Architecture & Complete Functional Documentation

> **Government & Enterprise Indoor Physical Asset Tracking System**  
> *Real-Time Hardware Bluetooth Radar · Multi-Device Proximity Geo-Fencing · Local Cryptographic Blockchain Ledger · Automated Multi-Layer Alerting*

---

## 1. Executive Summary

**AssetGuard** is an indoor asset tracking, proximity monitoring, and security audit system. It combines physical Bluetooth hardware detection with cryptographic verification.

The platform continuously monitors physical hardware devices (such as laptops, smartphones, tablets, biometric equipment, wireless peripherals, and audio devices) within designated facility zones. If an asset is taken out of Bluetooth proximity or moved into an unauthorized physical area, AssetGuard immediately fires multi-level alerts (Windows OS notification, system audio chime, dashboard banner) and seals the event permanently into an immutable, tamper-evident SHA-256 local blockchain ledger.

---

## 2. System Architecture & Tech Stack

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CLIENT / USER INTERFACE                         │
│   React 19 SPA · React Router v7 · Web Audio API · Web Notifications   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / REST + CSRF
┌───────────────────────────────────▼────────────────────────────────────┐
│                       SPRING BOOT BACKEND SERVER                       │
│                                                                        │
│  ┌───────────────────────┐  ┌──────────────────────────────────────┐   │
│  │     Spring Security   │  │             REST API                 │   │
│  │   RBAC (ADMIN/OFFICER)│  │   /api/summary  /api/assets          │   │
│  │   CSRF Token Rotation │  │   /api/blockchain  /api/bluetooth    │   │
│  └───────────────────────┘  └──────────────────┬───────────────────┘   │
│                                                │                       │
│  ┌─────────────────────────────────────────────▼────────────────────┐  │
│  │                       TrackingService                            │  │
│  │    State Transitions · Location Shifting · Geo-Fencing Rules     │  │
│  └──────────────┬──────────────────────────────┬────────────────────┘  │
│                 │                              │                       │
│  ┌──────────────▼─────────────┐  ┌─────────────▼────────────────────┐  │
│  │      BluetoothMonitor      │  │        BlockchainService         │  │
│  │  Win32 bthprops.cpl Radio  │  │   SHA-256 Cryptographic Mining   │  │
│  │  Fast 2-Second Auto-Probe  │  │   PoW Nonce & Tamper Audit       │  │
│  └──────────────┬─────────────┘  └─────────────┬────────────────────┘  │
└─────────────────┼──────────────────────────────┼───────────────────────┘
                  │                              │
┌─────────────────▼──────────────┐ ┌─────────────▼───────────────────────┐
│       WINDOWS OS HARDWARE      │ │          DATABASE LAYER             │
│  Bluetooth Support (bthserv)   │ │  H2 File Database / MySQL           │
│  Toast Notifications & Beeps   │ │  Assets, Logs, Alerts, Blocks       │
└────────────────────────────────┘ └─────────────────────────────────────┘
```

### Technology Breakdown
- **Backend Framework**: Spring Boot 3.5.7 (Java 17+ / Java 26 compatible)
- **Database**: H2 Persistent File Database (`./data/assets.mv.db`) with production MySQL connector support
- **Persistence Layer**: Spring Data JPA & Hibernate ORM 6.6
- **Security**: Spring Security 6, BCrypt Password Encoder, Rotating CSRF Protection (`X-CSRF-TOKEN`), HTTP-only Lax Session Cookies
- **Frontend Framework**: React 19.2.0, React Router v7.9.5, Vite v7.3.7
- **Styling**: Pure CSS Design System (`style.css` + `react.css`) tailored for government operations (Forest Green `#112f2b`, `#1e624f`, Terracotta `#feeeea`/`#b4594e`, Sage `#eaf0df`)
- **Native OS Integration**: Windows Native C Bluetooth API (`bthprops.cpl`), PowerShell Win32 Interop, Windows Audio & Toast Notifications

---

## 3. Detailed Functional Modules

### Module A: Real-Time Bluetooth Hardware Monitoring

#### 1. Native Windows Radio Probe (`bthprops.cpl`)
Unlike naive implementations that query Windows Device Manager (which keeps paired devices marked as "Connected" even when turned off), AssetGuard queries the **Win32 Bluetooth Radio API** (`bthprops.cpl` $\rightarrow$ `BluetoothFindFirstDevice`).
- Reads the hardware bitfield `BLUETOOTH_DEVICE_INFO.fConnected`.
- If `fConnected == true`: Physical radio link is active $\rightarrow$ **`CONNECTED`**.
- If `fConnected == false`: Device has powered off, dropped link, or moved out of range $\rightarrow$ **`DISCONNECTED`**.

#### 2. Multi-Device Auto-Discovery
- AssetGuard scans all Bluetooth hardware on the PC host.
- When **any** new Bluetooth device connects to the PC (smartphone, earbuds, headset, speaker, or smart accessory), the background scheduler auto-registers it into the asset registry with an automatically inferred type (`Smartphone`, `Audio / Headset`, `Peripheral`, `Wearable`).
- Provides a one-click manual synchronization endpoint: `POST /api/bluetooth/sync`.

#### 3. High-Frequency Polling & Real-Time Sync
- **Backend Scheduler**: Polls the Bluetooth radio every **2,000 milliseconds** (`tracking.interval-ms=2000`).
- **Frontend Real-Time Radar**: React polls `/api/summary` every **1,500 milliseconds**.
- **No Manual Refresh**: Changes in device connection status reflect in the user interface within 1.5 seconds without pressing F5.

---

### Module B: Moveable Places & Geo-Fencing

#### 1. Moveable Places Definition
When an asset is registered or modified, administrators assign:
- **`currentLocation`**: The current physical station of the device (e.g. `HQ Main Facility`, `Laptop Desk`).
- **`allowedLocations`**: A comma-separated list of authorized facility zones where the asset is permitted to move (e.g. `HQ Main Facility, Dispatch Office, Server Room B-4`).

#### 2. Authorized vs. Unauthorized Relocation
- **Authorized Shifting**:
  - If shifted to a zone listed in `allowedLocations`, the new location is saved, an authorized transfer is logged, and an immutable `LOCATION_SHIFT` block is mined.
- **Unauthorized Movement (Security Breach)**:
  - If shifted to a zone outside `allowedLocations` (e.g. `Restricted Perimeter`, `External Off-Site`), the system immediately triggers a **Critical Security Breach Alert**, sounds the alarm, and seals an immutable `SECURITY_BREACH_ALERT` fraud block on the blockchain.

#### 3. Interactive Movement Simulator (`/simulation`)
- Provides a visual floorplan and zone switcher.
- Allows administrators to select any asset, view its authorized zones, and simulate movement across facility stations to test alerts and cryptographic ledger generation.

---

### Module C: Local Cryptographic Blockchain Ledger

Every operation in the system is sealed in a chain of immutable blocks using standard cryptographic algorithms.

#### 1. Cryptographic Anatomy of a Block (`Block.java`)
| Field | Type | Description |
|---|---|---|
| `blockIndex` | `Long` | Sequential height in the blockchain (Genesis = 0) |
| `timestamp` | `Long` | Epoch millisecond timestamp of mining |
| `previousHash` | `String` | SHA-256 hash of the immediately preceding block (`0` for Genesis) |
| `blockHash` | `String` | SHA-256 hash of this block's combined payload, index, previous hash, and nonce |
| `transactionType` | `String` | Event category (`GENESIS`, `ASSET_REGISTERED`, `LOCATION_SHIFT`, `SECURITY_BREACH_ALERT`, `STATUS_ALERT`, `STATUS_CHANGE`, `ASSET_DELETED`) |
| `assetId` | `Long` | Unique database identifier of the affected asset |
| `assetName` | `String` | Name of the asset at the time of block generation |
| `fromLocation` | `String` | Origin zone before the event |
| `toLocation` | `String` | Destination zone after the event |
| `operator` | `String` | User or background daemon triggering the block |
| `payload` | `String` | Cryptographic description of the transaction |
| `authorized` | `Boolean` | Flag indicating compliance (`false` for security breaches & disconnect alerts) |
| `nonce` | `Long` | Proof-of-Work nonce satisfying the cryptographic target |

#### 2. Tamper-Proof Chain Audit (`/api/blockchain/verify`)
- The system includes an automated audit engine (`BlockchainService.verifyChain`).
- Recalculates the SHA-256 hash of every single block from Genesis to the tip.
- Verifies that `block.previousHash == previousBlock.blockHash`.
- If an attacker manually modifies a database record (e.g. altering an asset name or hiding an alert), the cryptographic hash becomes invalid and the dashboard immediately flags the blockchain as **COMPROMISED**.

---

### Module D: Multi-Layer Emergency Alerting System

When a disconnection or security breach occurs, AssetGuard fires across multiple layers simultaneously:

1. **Native Windows Desktop Notification**:
   - Executes [`scripts/notify-windows.ps1`](file:///c:/Users/keert/OneDrive/Documents/ChatGPT/Java%20Project/scripts/notify-windows.ps1) directly from Java via `ProcessBuilder`.
   - Displays a Windows Action Center popup toast on your laptop screen:  
     > `🚨 CRITICAL ALERT: [Device Name]`  
     > `Bluetooth connection lost — device out of range or turned off!`
   - Operates even if the browser is minimized or running in the background.

2. **Native Windows Audio Chime**:
   - Plays the Windows System Exclamation alarm sound through hardware speakers.

3. **In-Browser Web Audio Synthesizer Alarm**:
   - Leverages `window.AudioContext` to synthesize an alarm chime (880 Hz $\rightarrow$ 587.33 Hz sawtooth ramp) through the browser.

4. **Dashboard Security Banner (`.breach-banner`)**:
   - High-visibility banner matching the government theme with an animated breathing border (`pulseBorder`).
   - Displays active incident count and exact device names.

5. **Asset Registry Visual Status**:
   - Rows for disconnected assets are highlighted in soft terracotta.
   - The status badge displays `DISCONNECTED` with a warning icon `⚠️`.

6. **Automated Incident Auto-Resolution**:
   - When a disconnected device reconnects, open alerts for that device automatically mark as `RESOLVED`, the red banner clears, and an authorized `STATUS_CHANGE (CONNECTED)` block is mined.

---

### Module E: User Roles & Access Control

| Capability | ADMIN | OFFICER |
|---|:---:|:---:|
| View Overview Dashboard & Live Metrics | ✅ | ✅ |
| View Discovered Bluetooth Hardware Hub | ✅ | ✅ |
| View Blockchain Ledger & Run Cryptographic Audit | ✅ | ✅ |
| View Tracking History Audit Logs | ✅ | ✅ |
| Register New Assets | ✅ | ❌ |
| Edit Asset Details & Permitted Zones | ✅ | ❌ |
| Delete Assets | ✅ | ❌ |
| Shift Asset Location in Movement Simulator | ✅ | ❌ |
| Synchronize PC Bluetooth Devices | ✅ | ❌ |
| Manual Connect / Disconnect Simulation | ✅ | ❌ |

---

## 4. REST API Specification

### Authentication & Session
- `GET /api/session`: Returns authentication state, active username, role, and rotating CSRF token.
- `POST /api/login`: Form-encoded login (`username`, `password`). Returns 204 or 401.
- `POST /api/logout`: Terminates session, clears security context, returns 204.

### Monitoring & Status
- `GET /api/summary`: Returns full asset list, latest 100 alerts, open alert count, tracking mode, and total block height.
- `GET /api/logs`: Returns latest 100 historical tracking transitions.
- `GET /api/facility-zones`: Returns predefined standard security zones.

### Asset Management
- `GET /api/assets/{id}`: Returns details for a single asset.
- `POST /api/assets`: Registers an asset (Requires `ADMIN` + CSRF header).
- `PUT /api/assets/{id}`: Updates an asset (Requires `ADMIN` + CSRF header).
- `DELETE /api/assets/{id}`: Removes an asset from active tracking while retaining blockchain and log records.

### Bluetooth & Geo-Fencing
- `GET /api/bluetooth/devices`: Returns live scan of all PC Bluetooth hardware devices and live connection state.
- `POST /api/bluetooth/sync`: Scans and auto-registers any unmapped PC Bluetooth devices into the ledger.
- `POST /api/assets/{id}/shift`: Body `{"targetLocation": "Zone Name"}`. Shifts asset and checks permitted zones.
- `POST /api/assets/{id}/simulate/{status}`: In demo mode, manually triggers `CONNECTED` or `DISCONNECTED`.

### Cryptographic Blockchain
- `GET /api/blockchain`: Returns full array of mined cryptographic blocks in descending index order.
- `GET /api/blockchain/verify`: Recalculates all SHA-256 hashes from Genesis to audit chain integrity.

---

## 5. Operations & Usage Guide

### Starting the Services

#### 1. Production Mode (Single Executable JAR)
```powershell
# Kill any existing Java process
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.Path -notlike '*antigravity*' } | Stop-Process -Force

# Run the backend (serves both API and pre-bundled React frontend)
java -jar target/asset-tracker-1.0.0.jar
```
Open **`http://127.0.0.1:8080`** in any web browser.

#### 2. Development Mode (Vite Hot-Reload)
```powershell
# Start frontend dev server with instant HMR
npm --prefix frontend run dev
```
Open **`http://127.0.0.1:5173`** in your browser (proxies all `/api` calls to port 8080).

### Default Credentials
- **Admin**: `admin` / `admin` (Full administrative rights)
- **Officer**: `officer` / `officer` (Read-only monitoring)

---

## 6. How to Test Bluetooth Disconnection Live

1. Open **`http://127.0.0.1:5173`** or **`http://127.0.0.1:8080`** and log in as `admin`.
2. Connect your mobile phone (with Bluetooth Tethering enabled) or any Bluetooth earbuds/headphones to your PC.
3. Observe the dashboard: the device reflects as **`CONNECTED`** with a green badge.
4. **Trigger Disconnection**:
   - Turn **OFF** Bluetooth on your phone or put earbuds in their case.
   - Within **1.5 to 2 seconds**:
     - The alarm chime sounds.
     - A native Windows Action Center notification pops up in the bottom right corner.
     - The emergency alert banner appears at the top of the dashboard.
     - The device status switches to red **`DISCONNECTED`**.
     - An immutable `STATUS_ALERT` block is mined on the blockchain.
5. **Trigger Reconnection**:
   - Turn Bluetooth back on. Within 1.5–2 seconds, the alert resolves automatically, the banner clears, and a `CONNECTED` block is recorded.

