# KRS PyHive — Full Reference

> KRS PyHive turns an Android device into a **Python task runner** with a REST API. It embeds a **Python 3.13** runtime (Chaquopy), schedules sandboxed task executions, and persists history in an embedded ObjectBox database.

This is the complete reference. For a fast start, see [QUICKSTART.md](QUICKSTART.md). For client code, see [API_EXAMPLES.md](API_EXAMPLES.md).

## Table of Contents

1. [Overview](#overview)
2. [Project Structure](#project-structure)
3. [Setup & First Run](#setup--first-run)
4. [Configuration](#configuration)
5. [REST API Reference](#rest-api-reference)
6. [Authentication](#authentication)
7. [Task Management](#task-management)
8. [Python Sandboxing](#python-sandboxing)
9. [Advanced Usage](#advanced-usage)
10. [Troubleshooting](#troubleshooting)
11. [Performance](#performance)

## Overview

KRS PyHive provides:

- **Embedded Python** — Python 3.13 both via Chaquopy, with `numpy`, `pandas`, and `requests` pre-installed.
- **REST API** — a lightweight HTTP server (default `:8080`) serving `/api/*` routes with Bearer-token auth.
- **Task scheduling** — immediate, scheduled, and reschedulable execution with a 4-thread concurrency pool.
- **Sandboxing** — every task runs in its own directory (`{externalFilesDir}/python_sandboxes/{taskId}`) with a **100 MB** quota.
- **Persistence** — task history stored in the embedded ObjectBox database.
- **Structured logging** — Timber-based.

### Architecture (summary)

```
REST API Layer        PythonTaskRunnerService + ApiRoutes + TaskApiController
Auth Layer            AuthenticationManager
Scheduler             TaskScheduler
Sandbox               SandboxManager
Python Runtime        PythonRuntimeManager (Chaquopy worker model)
Persistence           TaskRepository (ObjectBox)
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full design.

## Project Structure

```
app/src/main/kotlin/krs/pyhive/
├── api/
│   ├── ApiController.kt            # Route-handler interface
│   ├── ApiRoutes.kt               # Central route matching (/api/*)
│   ├── TaskApiController.kt       # Task lifecycle handlers
│   └── PythonTaskRunnerService.kt # HTTP server implementation
├── auth/
│   └── AuthenticationManager.kt    # Bearer token gen/validation (AES-256-GCM storage)
├── models/
│   └── TaskModels.kt              # PythonTask, DTOs, TaskStatus enum
├── python/
│   └── PythonRuntimeManager.kt     # Chaquopy init & worker-driven execution
├── sandbox/
│   └── SandboxManager.kt           # Per-task file isolation
├── scheduler/
│   └── TaskScheduler.kt            # Task lifecycle, concurrency, stats
├── data/
│   ├── TaskEntity.kt               # ObjectBox entity
│   ├── TaskRepository.kt           # ObjectBox queries
│   └── TaskMappings.kt
├── preferences/
│   └── AppPreferences.kt          # Encrypted-settings access layer
├── settings/
│   ├── SettingsActivity.kt
│   └── SettingsFragment.kt         # PreferencesFragment UI
├── utils/
│   └── Utils.kt
├── MainActivity.kt                 # Server status & token display
└── PyHiveApp.kt                    # App entry point / component wiring

app/src/main/assets/python/
├── task_worker.py                  # Worker executed inside Chaquopy per task
└── file_access_restrictions.py     # Sandbox path-restriction template
```

## Setup & First Run

### Prerequisites

- Android device / emulator with API **30+**.
- Build host: Android SDK **34**, JDK 17 bytecode target, **Gradle 9.x** wrapper (this repo ships `gradlew`).
- ~500 MB free storage for the Python runtime.

### Build & Install

```bash
cd krs.pyhive
./gradlew :app:installDebug
```

This builds the debug APK, installs it, and launches `krs/pyhive/.MainActivity` on your connected device. For an automated build + install + debug-prep flow, use [scripts/deploy_and_prepare_debug.sh](../../scripts/deploy_and_prepare_debug.sh).

### First Run

On launch, `PyHiveApp.onCreate()` runs:

1. Plants Timber (debug tree on `DEBUG` builds, release tree otherwise).
2. Opens encrypted preferences (`EncryptedSharedPreferences`, AES-256-GCM).
3. Initializes `AuthenticationManager` — generates a 256-bit **Bearer token** if none exists.
4. Initializes sandboxing, Python runtime (Chaquopy), ObjectBox, and the task repository.
5. Starts the API server **iff** `pref_auto_start_server` is enabled.
6. Purges old tasks & sandboxes based on `pref_cleanup_age_days`.

The main screen shows server status and (masked) API token. Enable **Show Full Token** in Settings to copy the complete token.

## Configuration

Preferences are edited in-app (Settings) and stored encrypted.

| Key | Default | Range | Effect |
|---|---|---|---|
| `pref_auto_start_server` | `true` | — | Start/stop API server on app launch |
| `pref_api_port` | `8080` | 1024–65535 | HTTP server port (restart on change) |
| `pref_default_task_timeout_seconds` | `300` | 5–3600 | Default execution timeout |
| `pref_cleanup_age_days` | `7` | 1–30 | Task/sandbox purge age |
| `pref_show_full_token` | `false` | — | Reveal full token in UI |
| `pref_custom_api_token` | auto | — | Custom bearer token (encrypted) |

## REST API Reference

**Base URL** — `http://<device-ip>:<port>/api`, e.g. `http://localhost:8080/api` (or the emulator IP like `10.0.2.2`).

**Headers** — All responses are JSON (Gson). Task submission is `multipart/form-data`.

**Auth** — `Authorization: Bearer <token>` on **all** endpoints.

> ⚠️ The JSON wire format uses **snake_case** fields (`task_id`, `created_at`, `new_scheduled_time`, …). Kotlin fields keep camelCase via `@SerializedName`.

### Endpoint Summary

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| `POST` | `/api/tasks` | Submit task | `202` | `400` |
| `GET` | `/api/tasks` | List / filter tasks | `200` | `400`,`500` |
| `GET` | `/api/tasks/{id}` | Status & result | `200` | `404`,`500` |
| `PUT` | `/api/tasks/{id}/cancel` | Cancel | `200` | `409`,`500` |
| `PUT` | `/api/tasks/{id}/reschedule` | Reschedule | `200` | `400`,`409` |
| `DELETE` | `/api/tasks/{id}` | Delete | `200` | `404`,`500` |
| `GET` | `/api/stats` | Statistics | `200` | `500` |
| `GET` | `/api/health` | Health | `200` | —|

### 1. Submit a Task — `POST /api/tasks`

Submits a Python script as a **multipart form** with these parts:

| Part | Type | Notes |
|---|---|---|
| `params` | JSON string | `script_name`, `scheduled_time` (ms epoch), `timeout_seconds`, `tags[]` |
| `script` | text | Raw Python source |
| `args` | JSON string (optional) | Object passed to `main(args)`; must be a JSON object |

**cURL:**

```bash
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -F 'params={"script_name":"hello.py","timeout_seconds":30,"tags":["demo"],"args":{"name":"PyHive"}}' \
  -F 'script=print("Hello from Python!")' \
  http://localhost:8080/api/tasks
```

**Response `202`:**

```json
{
  "task_id": "uuid-string",
  "status": "PENDING",
  "message": "Task submitted successfully",
  "created_at": 1692345600000
}
```

The `timeout_seconds` defaults to the preference default (300) when `<=0`.

### 2. Get Task Status — `GET /api/tasks/{id}`

**Response `200`:**

```json
{
  "task_id": "uuid-string",
  "status": "COMPLETED",
  "execution_time": 1234,
  "result": "Hello from Python!",
  "output": "Hello from Python!",
  "error": null,
  "progress": 0
}
```

`404` if not found.

### 3. List Tasks — `GET /api/tasks`

Query params (optional): `status`, `tag`, `limit` (default `100`).

**Response `200`:**

```json
{
  "tasks": [
    {
      "task_id": "uuid-string",
      "status": "COMPLETED",
      "execution_time": 45,
      "result": "…",
      "output": "…",
      "error": null,
      "progress": 0
    }
  ],
  "total_count": 150,
  "filtered_count": 3
}
```

### 4. Cancel — `PUT /api/tasks/{id}/cancel`

**Response `200`:** `{"message":"Task cancelled successfully","task_id":"…"}`

`409` when the task is already terminal.

### 5. Reschedule — `PUT /api/tasks/{id}/reschedule`

Body (`application/json`): `{"new_scheduled_time": 1692345600000}`

**Response `200`:** `{"message":"Task rescheduled successfully","task_id":"…","new_scheduled_time":1692345600000}`

`400` on malformed request, `409` on invalid state.

### 6. Delete — `DELETE /api/tasks/{id}`

**Response `200`:** `{"message":"Task deleted successfully","task_id":"…"}` · `404` if not found.

### 7. Statistics — `GET /api/stats`

**Response `200`:**

```json
{
  "total_tasks": 150,
  "pending_tasks": 10,
  "running_tasks": 2,
  "completed_tasks": 100,
  "failed_tasks": 20,
  "cancelled_tasks": 15,
  "scheduled_tasks": 3,
  "average_execution_time_ms": 5432
}
```

### 8. Health — `GET /api/health`

**Response `200`:**

```json
{
  "status": "healthy",
  "timestamp": 1692345600000,
  "python_initialized": true
}
```

### Error Shape

All error responses use:

```json
{
  "error_code": "400",
  "message": "Human-readable message",
  "details": null,
  "timestamp": 1692345600000
}
```

## Authentication

- Tokens are 32-byte (256-bit) Base64 values generated by `SecureRandom` in `AuthenticationManager.generateNewToken()`.
- Stored encrypted in `EncryptedSharedPreferences` and validated on every request.
- Every request must include `Authorization: Bearer <token>`.
- Use the token from the app UI, or set a custom one (via `pref_custom_api_token`).

```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/api/health
```

## Task Management

### Lifecycle

```
PENDING → RUNNING → COMPLETED
   │          │
   ▼          ▼
CANCELLED   FAILED
   │
SCHEDULED → RUNNING
```

| Status | Meaning | Can transition to |
|---|---|---|
| `PENDING` | Queued, not yet started | RUNNING, CANCELLED |
| `RUNNING` | Executing | COMPLETED, FAILED, CANCELLED |
| `SCHEDULED` | Future execution | RUNNING, CANCELLED |
| `COMPLETED` | Success (terminal) | — |
| `FAILED` | Error (terminal) | — |
| `CANCELLED` | User-cancelled (terminal) | — |

- **Timeout** — default `pref_default_task_timeout_seconds` (300); per-request override via `params.timeout_seconds` (max 3600).
- **Retry** — a task has `retry_count` / `max_retries` (default 3); when exhausted it lands in `FAILED` permanently.
- **Cleanup** — tasks older than `pref_cleanup_age_days` are purged on startup.

## Python Sandboxing

Sandbox root: `{externalFilesDir}/python_sandboxes/{taskId}` (100 MB per sandbox).

Each task:
1. The worker (`task_worker.py`) writes your script to `user_script.py` in the sandbox.
2. It `exec`s the enforcement module `file_access_restrictions.py` (template, path arguments substituted at runtime).
3. It `exec`s the user script in a fresh globals frame.
4. Redirects `stdout`/`stderr`, captures output/result, then **deletes the sandbox in a `finally` block** (regardless of success/failure).
5. Drops task-imported modules to reduce cross-task leakage.

**Enforced restrictions** (`PermissionError`):

```python
# ❌ BLOCKED — absolute path outside sandbox
open('/etc/passwd', 'r')

# ❌ BLOCKED — escaping via '..'
open('../../../etc/passwd', 'r')

# ❌ BLOCKED — symlinked path escape
open('/data/something/…')
```

**Allowed** — relative reads/writes inside the sandbox, subdirectory creation, listing.

> Both the per-task sandbox and the internal `{filesDir}/chaquopy` paths are whitelisted (Chaquopy may create requirement dirs at runtime).

## Advanced Usage

### Scheduled Execution

```bash
SCHEDULED_TIME=$(( $(date +%s) * 1000 + 5 * 60 * 1000 ))   # +5 min
curl -X POST \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "params={\"script_name\":\"scheduled.py\",\"scheduled_time\":$SCHEDULED_TIME}" \
  -F "script=print('Scheduled task executed')" \
  http://localhost:8080/api/tasks
```

### Passing Arguments

Provide `args` as a JSON object in a multipart part — the worker calls `main(args)`:

```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F 'params={"script_name":"with_args.py"}' \
  -F 'args={"items":[1,2,3],"factor":10}' \
  -F 'script=
def main(a):
    print(sum(a["items"]) * a["factor"])
'
```

### Python packages / modules

`numpy`, `pandas`, and `requests` ship with the runtime via `chaquopy { pip { install(...) } }` — import them directly. To add more, edit `app/build.gradle.kts` and rebuild.

### Monitoring a task

```bash
TASK_ID=…
TOKEN=…
while true; do
  s=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/tasks/$TASK_ID)
  st=$(jq -r '.status' <<<"$s")
  echo "$st"
  [[ "$st" == "COMPLETED" || "$st" == "FAILED" ]] && break
  sleep 2
done
```

## Troubleshooting

### "Unauthorized" / invalid token
- Format must be `Bearer <token>` (case-sensitive). Copy from Settings with **Show Full Token** toggled on.

### "Connection refused"
- Ensure the app is running and the server is up (`pref_auto_start_server`). On the emulator use the emulator IP (e.g. `http://10.0.2.2:8080/api/health`) instead of `localhost`.
- Check the port didn't change in Settings.

### Python init fails
- Check free storage (≥~500 MB) and that Chaquopy supplies are present. Look for `Failed to initialize Python` in the logs.

### Task stuck in RUNNING / timeout
- Check for infinite loops; raise `timeout_seconds`; or cancel (`PUT /api/tasks/{id}/cancel`).

### "Access denied: Cannot read '{path}'"
- Script escaped its sandbox — use relative paths and keep reads/writes under `./`.

### Build / JVM issues (developers)
- This project pins `org.gradle.java.home` to a JDK (see `gradle.properties`) and sets `android.builtInKotlin=false` + `android.newDsl=false` for kapt under Gradle 9 + AGP 9. A full JDK must be on `PATH` for local CLI builds.

## Performance

- Concurrency is capped at **4** worker threads (`TaskScheduler`).
- Each sandbox limited to **100 MB**.
- Runtime footprint: Python ~100 MB, API server minimal.
- Prefer batched submissions with `tags`, and periodically delete completed tasks.

## License

MIT — see the `LICENSE` file.

## Support

1. Check Timber logs (`adb logcat | grep python`).
2. Review endpoint error responses.
3. Consult this troubleshooting section or [DEVELOPMENT.md](DEVELOPMENT.md).
4. Open an issue in the repository.
