# KRS PyHive — Python Task Runner for Android

> Transform your Android device into a secure, sandboxed **Python execution server** with a full REST API.

KRS PyHive embeds a **Python 3.13** runtime (via [Chaquopy](https://chaquo.com/py/)) inside an Android app and exposes it through a lightweight HTTP server. Submit Python scripts as tasks, schedule them, and run them in **isolated per-task sandboxes** — all protected by Bearer token authentication.

## 🚀 At a Glance

```
 Your Android device
        │        ## Performance Tips
        
        1. **Parallel Submission**: Submit multiple tasks at once
        ▼
   KRS PyHive app
        ├─ Python 3.13 runtime (Chaquopy)
        ├─ REST API server (port 8080 default)
        ├─ Task scheduler (up to 4 concurrent)
        ├─ Per-task file sandboxing
        ├─ ObjectBox task persistence
        └─ Bearer token authentication
        │
        ▼
 REST clients (curl, Python, Node.js…)
```

## ✨ Key Features

- **🐍 Embedded Python** — Full Python 3.13 runtime with `numpy`, `pandas`, and `requests` pre-installed.
- **🔒 Secure Sandboxing** — Each task executes in its own isolated directory with runtime-enforced file access restrictions.
- **📡 REST API** — Complete HTTP API (submit, query, cancel, reschedule, delete) with Bearer token auth.
- **⏱️ Task Scheduling** — Immediate, scheduled, and reschedulable task execution.
- **⚙️ Concurrency** — Up to 4 tasks run concurrently (configurable at build time).
- **📊 Monitoring** — Real-time task statistics, health checks, and status tracking.
- **🗄️ Persistence** — Task history stored in an embedded ObjectBox NoSQL database.
- **🛡️ Isolation** — Full per-task isolation with independent sandboxes and resource quotas.
- **📝 Logging** — Timber-based structured logging.
- **🎯 Modern Architecture** — Layered design with clear separation of concerns (API / auth / scheduler / sandbox / Python).

## ⚠️ Requirements

| Requirement | Value |
|---|---|
| Android device/emulator | API Level **30+** (Android 11+) |
| Build | Android SDK 34, JDK 17 (bytecode target), Gradle 9.x wrapper |
| Storage | 500 MB+ free (Python runtime + sandboxes) |
| RAM | 1 GB+ recommended |

## 🏗️ Project Structure

```
krs.pyhive/
├── app/                            # Android application
│   ├── src/main/
│   │   ├── kotlin/krs/pyhive/
│   │   │   ├── api/                # REST server, routing & controllers
│   │   │   ├── auth/               # Bearer token management
│   │   │   ├── models/             # Data models & DTOs (TaskModels.kt)
│   │   │   ├── python/             # Chaquopy runtime integration
│   │   │   ├── sandbox/            # Per-task file isolation
│   │   │   ├── scheduler/          # Task lifecycle & concurrency
│   │   │   ├── data/               # ObjectBox entities & repository
│   │   │   ├── preferences/        # Encrypted settings layer
│   │   │   ├── settings/           # Settings UI
│   │   │   ├── utils/              # Utilities
│   │   │   ├── MainActivity.kt     # Server status / token UI
│   │   │   └── PyHiveApp.kt        # Application entry point
│   │   ├── assets/python/          # Embedded Python worker & sandbox module
│   │   └── res/                    # Android resources
│   ├── build.gradle.kts            # App build config (AGP/Chaquopy/ObjectBox)
│   └── proguard-rules.pro
├── build.gradle.kts                # Root build file
├── settings.gradle.kts
├── gradle.properties               # JVM & Kotlin toolchain flags
├── gradle/                         # Gradle wrapper config
├── docs/                           # Documentation
│   ├── README.md                   # Full reference (API, auth, troubleshooting)
│   ├── QUICKSTART.md               # Get started in 5 minutes
│   ├── API_EXAMPLES.md             # Client examples (cURL / Python / JS)
│   ├── ARCHITECTURE.md             # System design
│   └── DEVELOPMENT.md              # Build, debug & contribute
├── examples/                       # Example scripts
│   ├── README.md
│   ├── EXAMPLE_SCRIPTS.md
│   └── sample-webhook-call.py
└── scripts/                        # Deploy & debug automation
    └── deploy_and_prepare_debug.sh
```

## 📖 Documentation

| Document | Purpose |
|----------|---------|
| [docs/README.md](docs/README.md) | Complete reference: setup, API, auth, troubleshooting |
| [docs/QUICKSTART.md](docs/QUICKSTART.md) | Get started in 5 minutes |
| [docs/API_EXAMPLES.md](docs/API_EXAMPLES.md) | cURL, Python & Node.js client examples |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design & component interactions |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Build, debug, and contribute |
| [examples/README.md](examples/README.md) | Quick example reference |
| [examples/EXAMPLE_SCRIPTS.md](examples/EXAMPLE_SCRIPTS.md) | Python script examples |

## 🚀 Quick Start

### 1. Build & install on a device

```bash
cd krs.pyhive
./gradlew :app:installDebug
```

Installation automatically launches the app on your connected device.

### 2. Get your API token

The token is generated on first run and shown on the main screen (masked by default — enable **Show Full Token** in Settings to copy it).

### 3. Submit your first task

```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F 'params={"script_name":"hello.py","timeout_seconds":30,"args":{"name":"PyHive"}}' \
  -F 'script=print("Hello from Python!")' \
  http://localhost:8080/api/tasks
```

> Task submission uses **`multipart/form-data`** with `params` (JSON), `script` (text) and optional `args` (JSON) fields — **not** `application/json`. See [docs/QUICKSTART.md](docs/QUICKSTART.md).

## 📡 REST API — Endpoints

All endpoints live under `/api` and **require Bearer auth** (`Authorization: Bearer <token>`).

| Method | Path | Purpose | Response status |
|---|---|---|---|
| `POST` | `/api/tasks` | Submit a task (multipart) | `202` |
| `GET` | `/api/tasks` | List / filter tasks | `200` |
| `GET` | `/api/tasks/{id}` | Task status & result | `200` / `404` |
| `PUT` | `/api/tasks/{id}/cancel` | Cancel a task | `200` / `409` |
| `PUT` | `/api/tasks/{id}/reschedule` | Reschedule a task | `200` / `400` / `409` |
| `DELETE` | `/api/tasks/{id}` | Delete a task | `200` / `404` |
| `GET` | `/api/stats` | Server / task statistics | `200` |
| `GET` | `/api/health` | Health check | `200` |

For the full contract (request bodies, query params, JSON shapes) see [docs/README.md](docs/README.md).

## 📱 Configuration

Preferences are stored in **encrypted** `EncryptedSharedPreferences` (`AES-256-GCM`) and edited from the in-app **Settings** screen:

| Key | Default | Range | Purpose |
|---|---|---|---|
| `pref_auto_start_server` | `true` | — | Auto-start API server on launch |
| `pref_api_port` | `8080` | 1024–65535 | HTTP server port |
| `pref_default_task_timeout_seconds` | `300` | 5–3600 | Default per-task timeout |
| `pref_cleanup_age_days` | `7` | 1–30 | Sandbox / task cleanup age |
| `pref_show_full_token` | `false` | — | Show full token in UI (vs. masked) |
| `pref_custom_api_token` | auto | — | Custom Bearer token (encrypted) |

Changing the port triggers a live server restart; toggling auto-start stops/starts the server.

## 🔒 Security

- **Bearer token auth** — every endpoint requires it; the token is a 32-byte (256-bit) Base64 value generated via `SecureRandom`.
- **Encrypted storage** — preferences and token encrypted with AES-256-GCM (androidx `security-crypto`).
- **Per-task sandboxes** — each task runs in its own directory under `{externalFilesDir}/python_sandboxes/{taskId}` with a **100 MB quota**.
- **Runtime file-access restrictions** — Python-level interception of `open`, `os.open`, `os.listdir`, etc. (template-loaded module per task).
- **Task isolation & cleanup** — sandboxes are removed in a `finally` block after each run; old tasks/sandboxes purged by age.
- **Path validation** — canonical path checks keep scripts inside their sandbox.

## 🛠️ Tech Stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.0.21 (Java 17 bytecode target) |
| Android | AGP 9, minSdk 30 / targetSdk 34, API 34 |
| Python | Chaquopy **3.13** (`numpy`, `pandas`, `requests`) |
| Database | ObjectBox 5.4.2 (embedded, type-safe) |
| HTTP server | Lightweight custom server (Kotlin/Java sockets) |
| JSON | Gson 2.10.1 |
| Logging | Timber 5.0.1 |
| Scheduling | `ScheduledExecutorService` (4 worker threads) |
| Security | androidx `security-crypto` (AES-256-GCM) |
| Async | kotlinx-coroutines 1.7.3 |

See [app/build.gradle.kts](app/build.gradle.kts) for the full dependency list.

## 🧪 Testing

Run the suites from the project root:

```bash
./gradlew :app:test
./gradlew :app:testDebugUnitTest
```

## 🎓 Todos
1. Implement per Task Memory Control
2. Implement per Task Resource Monitoring
3. Implement Task Queue 
4. Implement support to Script Project with multiple files

## 🆘 Support

1. [docs/README.md](docs/README.md) — full reference & troubleshooting
2. [docs/QUICKSTART.md](docs/QUICKSTART.md) — 5-minute guide
3. [docs/API_EXAMPLES.md](docs/API_EXAMPLES.md) — client examples
4. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — internals
5. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) — build & contribution

---

**Happy Task Running! 🚀** Start with [docs/QUICKSTART.md](docs/QUICKSTART.md).
