# Python Task Runner API - Android Application

A comprehensive Android application that acts as a Python Task Runner REST API server. This application embeds a Python 3.11 runtime and provides a secure, scalable platform for executing Python scripts with sandbox isolation, task scheduling, and REST API access.

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Features](#features)
4. [Project Structure](#project-structure)
5. [Setup Instructions](#setup-instructions)
6. [REST API Endpoints](#rest-api-endpoints)
7. [Authentication](#authentication)
8. [Task Management](#task-management)
9. [Python Sandboxing](#python-sandboxing)
10. [Advanced Usage](#advanced-usage)
11. [Troubleshooting](#troubleshooting)

## Overview

The Python Task Runner is an Android application that transforms your device into a Python execution server. It provides:

- **Embedded Python Runtime**: Python 3.11 runtime via Chaquopy
- **REST API**: Full HTTP/REST API with Bearer Token authentication
- **Task Scheduling**: Concurrent and scheduled task execution
- **File Sandboxing**: Secure file access restrictions per task
- **Task Isolation**: Complete isolation between different tasks
- **Comprehensive Logging**: Detailed logging and error handling

## Architecture

### Layered Architecture

```
┌─────────────────────────────────────────┐
│        REST API Layer                   │
│    (PythonTaskRunnerService)            │
├─────────────────────────────────────────┤
│        Authentication Layer             │
│    (AuthenticationManager)              │
├─────────────────────────────────────────┤
│        Task Scheduler                   │
│    (TaskScheduler)                      │
├─────────────────────────────────────────┤
│   Python Runtime + Sandboxing           │
│   (PythonRuntimeManager + SandboxManager)│
├─────────────────────────────────────────┤
│   Android System (File System, etc.)    │
└─────────────────────────────────────────┘
```

### Components

1. **PythonTaskRunnerService**: Lightweight HTTP server handling incoming REST requests
2. **AuthenticationManager**: Bearer token generation and validation
3. **TaskScheduler**: Manages task lifecycle, scheduling, and execution
4. **PythonRuntimeManager**: Executes Python scripts with timeout support
5. **SandboxManager**: Enforces file access restrictions per task
6. **Task Models**: Data classes representing tasks and API responses

## Features

✅ **Embedded Python Runtime**
- Python 3.11 via Chaquopy
- Pre-compiled standard library
- Support for pip packages

✅ **Bearer Token Authentication**
- Secure token generation
- Encrypted token storage
- Per-request validation

✅ **Task Execution**
- Immediate or scheduled execution
- Concurrent task processing
- Task result capture (stdout, stderr)
- Automatic timeout handling

✅ **Task Management**
- Submit, query, cancel, reschedule, and delete tasks
- Task status tracking
- Retry mechanism for failed tasks
- Task tagging and filtering

✅ **File Sandboxing**
- Per-task isolated directory
- File access restrictions enforced at Python level
- Disk space quotas per sandbox
- Automatic cleanup of old sandboxes

✅ **Advanced Features**
- Task statistics and monitoring
- Health check endpoint
- Comprehensive error handling
- Timber-based structured logging

## Project Structure

```
app/
├── src/main/
│   ├── kotlin/com/pythontaskrunner/
│   │   ├── api/
│   │   │   └── PythonTaskRunnerService.kt      # REST API Server
│   │   ├── auth/
│   │   │   └── AuthenticationManager.kt        # Token Management
│   │   ├── models/
│   │   │   └── TaskModels.kt                   # Data Classes
│   │   ├── python/
│   │   │   └── PythonRuntimeManager.kt         # Python Integration
│   │   ├── sandbox/
│   │   │   └── SandboxManager.kt               # File Isolation
│   │   ├── scheduler/
│   │   │   └── TaskScheduler.kt                # Task Management
│   │   ├── utils/
│   │   │   └── Utils.kt                        # Utility Functions
│   │   ├── MainActivity.kt                      # Main UI Activity
│   │   └── PythonTaskRunnerApp.kt              # Application Class
│   └── res/
│       ├── layout/
│       │   └── activity_main.xml
│       └── values/
│           ├── colors.xml
│           ├── strings.xml
│           └── styles.xml
├── build.gradle.kts
├── proguard-rules.pro
└── AndroidManifest.xml

build.gradle.kts
settings.gradle.kts
```

## Setup Instructions

### Prerequisites

- Android Studio 2022.1 or later
- Android SDK 26+ (API Level 26+)
- 500MB+ free space for Python runtime and sandboxes
- Kotlin 1.9.10+

### Building the Project

1. **Clone or extract the project**
   ```bash
   cd krs.pyhive
   ```

2. **Open in Android Studio**
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

3. **Configure Build Settings**
   - Edit `app/build.gradle.kts` if needed
   - Ensure compileSdk = 34 and targetSdk = 34

4. **Build the APK**
   ```bash
   ./gradlew build
   ```

5. **Run on Device/Emulator**
   ```bash
   ./gradlew installDebug
   ```

### First Run Setup

When the app launches for the first time:

1. **API Token Generation**: A secure Bearer token is automatically generated
2. **Python Initialization**: Python 3.11 runtime is initialized
3. **Directory Creation**: Sandbox directories are created in app-specific storage
4. **API Server**: REST API server starts on port 8080

Check the main activity for:
- API token (for authentication)
- Server status
- Initial task statistics

## REST API Endpoints

### Base URL
```
http://localhost:8080/api
```

### Common Headers
```
Authorization: Bearer <token>
Content-Type: application/json
```

> **Note:** Submitting a task uses **multipart/form-data** with two fields:
> - `params` — a JSON string with the task parameters (`script_name`, `scheduled_time`, `timeout_seconds`, `tags`, optional `args` JSON object)
> - `script` — the raw Python script content
>
> The `Content-Type` header for submission must be `multipart/form-data; boundary=...` (cURL `-F` sets this automatically).

### Endpoints

#### 1. Submit a New Task
**POST** `/tasks`

Submit a Python script for execution using a multipart form.

**Request Body (multipart/form-data):**
- `params` (`application/json` part): task parameters
- `script` (`text/plain` part): the Python script content

**Using cURL:**
```bash
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -F 'params={"script_name":"hello_world.py","scheduled_time":null,"timeout_seconds":300,"tags":["test","hello"],"args":{"name":"PyHive"}}' \
  -F 'script=print("Hello from Python!")' \
  http://localhost:8080/api/tasks
```

**Response (202 Accepted):**
```json
{
  "task_id": "uuid-string",
  "status": "PENDING",
  "message": "Task submitted successfully",
  "created_at": 1692345600000
}
```

#### 2. Get Task Status
**GET** `/tasks/{taskId}`

Get the current status and results of a task.

**Response (200 OK):**
```json
{
  "task_id": "uuid-string",
  "status": "COMPLETED",
  "execution_time": 1234,
  "result": "Hello from Python!",
  "output": "Hello from Python!",
  "error": null,
  "progress": 100
}
```

#### 3. List All Tasks
**GET** `/tasks`

List all tasks with optional filtering.

**Query Parameters:**
- `status`: Filter by status (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED, SCHEDULED)
- `tag`: Filter by tag
- `limit`: Maximum number of tasks to return (default: 100)

**Response (200 OK):**
```json
{
  "tasks": [
    {
      "task_id": "uuid-string",
      "status": "COMPLETED",
      "execution_time": 1234,
      "result": "output",
      "output": "output",
      "error": null
    }
  ],
  "total_count": 150,
  "filtered_count": 10
}
```

**Examples:**
```bash
# Get all pending tasks
GET /tasks?status=PENDING

# Get tasks with specific tag
GET /tasks?tag=batch_job

# Limit results
GET /tasks?limit=50
```

#### 4. Cancel a Task
**PUT** `/tasks/{taskId}/cancel`

Cancel a pending or scheduled task.

**Response (200 OK):**
```json
{
  "message": "Task cancelled successfully",
  "task_id": "uuid-string"
}
```

#### 5. Reschedule a Task
**PUT** `/tasks/{taskId}/reschedule`

Reschedule a pending or scheduled task.

**Request Body:**
```json
{
  "new_scheduled_time": 1692345600000
}
```

**Response (200 OK):**
```json
{
  "message": "Task rescheduled successfully",
  "task_id": "uuid-string",
  "new_scheduled_time": 1692345600000
}
```

#### 6. Delete a Task
**DELETE** `/tasks/{taskId}`

Delete a task and its sandbox.

**Response (200 OK):**
```json
{
  "message": "Task deleted successfully",
  "task_id": "uuid-string"
}
```

#### 7. Get Server Statistics
**GET** `/stats`

Get overall server statistics.

**Response (200 OK):**
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

#### 8. Health Check
**GET** `/health`

Check server health and status.

**Response (200 OK):**
```json
{
  "status": "healthy",
  "timestamp": 1692345600000,
  "python_initialized": true
}
```

## Authentication

### Bearer Token Mechanism

The API uses Bearer tokens for authentication. All requests must include a valid token.

### Token Management

#### Getting the Token

1. **From Main Activity**: The token is displayed in the UI (first 20 characters)
2. **From SharedPreferences**: Retrieved programmatically
3. **From Logs**: Check Timber logs for "Generated initial API token"

#### Using the Token

Include the token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer YOUR_TOKEN_HERE" \
     http://localhost:8080/api/health
```

#### Token Security

- Tokens are generated using `SecureRandom` (256-bit)
- Stored in `EncryptedSharedPreferences`
- Base64 encoded for transmission
- Validated on every request

## Task Management

### Task Lifecycle

```
PENDING → RUNNING → COMPLETED
   ↓         ↓
SCHEDULED  FAILED
   ↓         ↓
(execution) (can retry)
   ↓
CANCELLED (by user)
```

### Task Status Meanings

| Status | Meaning | Transitions |
|--------|---------|-------------|
| PENDING | Waiting for execution | → RUNNING, CANCELLED |
| RUNNING | Currently executing | → COMPLETED, FAILED |
| COMPLETED | Successfully finished | → (terminal) |
| FAILED | Execution error | → Retry or delete |
| CANCELLED | Cancelled by user | → (terminal) |
| SCHEDULED | Scheduled for future | → RUNNING, CANCELLED |

### Retry Mechanism

- Failed tasks can be retried automatically (up to maxRetries)
- Each task has `retryCount` and `maxRetries` fields
- After all retries exhausted, task enters FAILED state permanently

### Task Timeout

- Default timeout: 300 seconds (5 minutes)
- Maximum timeout: 3600 seconds (1 hour)
- When timeout occurs: Task fails with "Script execution exceeded timeout" error

## Python Sandboxing

### Sandbox Architecture

Each task gets an isolated directory:
```
/app_storage/python_sandboxes/
├── task_id_1/
│   ├── task_script.py
│   ├── output.txt
│   └── ... (user-created files)
├── task_id_2/
│   └── ...
```

### File Access Restrictions

Python scripts can ONLY access files within their sandbox directory. Any attempt to access files outside results in `PermissionError`.

#### Example - Allowed Operations

```python
# Write to sandbox
with open('output.txt', 'w') as f:
    f.write('Hello')

# Create subdirectories
import os
os.makedirs('subdir/nested')

# List directory
files = os.listdir('.')

# Read files in sandbox
with open('output.txt', 'r') as f:
    content = f.read()
```

#### Example - Forbidden Operations

```python
# Read files outside sandbox - ❌ BLOCKED
with open('/system/build.prop', 'r') as f:
    pass

# Access parent directory - ❌ BLOCKED
with open('../../../etc/passwd', 'r') as f:
    pass

# Absolute paths outside sandbox - ❌ BLOCKED
with open('/data/app/data', 'r') as f:
    pass
```

### Sandbox Size Quotas

- Maximum per-sandbox: 100 MB
- Enforced at write time
- Automatic cleanup of old sandboxes (7 days old by default)

### Sandbox Cleanup

- On task deletion: Sandbox immediately deleted
- Automatic cleanup: Sandboxes > 7 days old
- On restart: Cleanup triggered on app start

## Advanced Usage

### Submitting Complex Python Scripts

#### Data Processing Example

```python
import json
import os

# Input data
data = {
    'items': [1, 2, 3, 4, 5],
    'multiplier': 3
}

# Process data
results = [x * data['multiplier'] for x in data['items']]

# Save results
output = {
    'original': data['items'],
    'processed': results,
    'sum': sum(results)
}

# Write to file
with open('results.json', 'w') as f:
    json.dump(output, f, indent=2)

# Print summary
print(f"Processed {len(results)} items")
print(f"Sum: {sum(results)}")
```

### Scheduled Task Execution

**Submit a task to run in 5 minutes:**

```bash
# Calculate scheduled time (5 minutes from now)
SCHEDULED_TIME=$(($(date +%s000) + 5*60*1000))

curl -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "params={\"script_name\":\"scheduled.py\",\"scheduled_time\":$SCHEDULED_TIME}" \
  -F "script=print('Scheduled task executed')"
```

### Batch Task Processing

**Submit multiple tasks:**

```bash
#!/bin/bash
TOKEN="YOUR_TOKEN"

for i in {1..10}; do
  curl -X POST http://localhost:8080/api/tasks \
    -H "Authorization: Bearer $TOKEN" \
    -F "params={\"script_name\":\"batch_$i.py\",\"tags\":[\"batch\",\"iteration_$i\"]}" \
    -F "script=print('Task $i')"
done
```

### Monitoring with Scripts

```bash
#!/bin/bash
TOKEN="YOUR_TOKEN"
TASK_ID="$1"

while true; do
  STATUS=$(curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/tasks/$TASK_ID | jq -r '.status')
  
  echo "Status: $STATUS"
  
  if [[ "$STATUS" == "COMPLETED" || "$STATUS" == "FAILED" ]]; then
    break
  fi
  
  sleep 2
done
```

### Adding Custom Python Modules

To include additional Python packages, modify the script to install dependencies:

```python
import subprocess
import sys

# Install packages (if needed)
packages = ['numpy', 'pandas']
for package in packages:
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', package])

# Now use installed packages
import numpy as np
result = np.array([1, 2, 3]) * 2
print(result)
```

## Troubleshooting

### Common Issues

#### 1. "Unauthorized: Invalid or missing bearer token"

**Solution:**
- Check token format: `Bearer <token>`
- Verify token from app UI
- Token is case-sensitive

#### 2. "Python initialization failed"

**Solution:**
- Ensure minimum Android SDK 26
- Check available storage (≥500MB)
- Clear app data and restart
- Check Timber logs for details

#### 3. Task execution timeout

**Solution:**
- Increase `timeout_seconds` parameter (max: 3600)
- Optimize Python script performance
- Break into smaller tasks

#### 4. "Access denied: Cannot read '{path}'. Only files in {sandbox} are accessible"

**Solution:**
- Ensure all file operations are within sandbox directory
- Use relative paths in scripts
- Create subdirectories inside sandbox if needed

#### 5. "Failed to create sandbox directory"

**Solution:**
- Check app has write permissions
- Verify storage available
- Check AndroidManifest permissions

#### 6. Task stuck in RUNNING state

**Solution:**
- Check Timber logs for errors
- Use timeout parameter to prevent infinite loops
- Cancel task and try again

### Logging

Enable detailed logging with Timber:

```kotlin
// In MainActivity or any Activity
import timber.log.Timber

Timber.d("Debug message")
Timber.i("Info message")
Timber.w("Warning message")
Timber.e("Error message")
```

View logs in Android Studio:
```bash
./gradlew logcat
```

Or use adb:
```bash
adb logcat | grep "python"
```

### Debugging Python Scripts

Add debug output to scripts:

```python
import sys
import traceback

try:
    # Your code here
    result = 1 / 0  # Example error
except Exception as e:
    print(f"ERROR: {str(e)}", file=sys.stderr)
    traceback.print_exc()
    sys.exit(1)
```

## Performance Considerations

### Optimization Tips

1. **Batch Processing**: Submit related tasks together with tags
2. **Resource Management**: Monitor sandbox sizes
3. **Concurrent Limits**: Default 4 concurrent tasks (adjustable)
4. **Cleanup**: Regular cleanup of completed tasks
5. **Script Optimization**: Use efficient Python code

### Monitoring

Check statistics endpoint regularly:
```bash
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/api/stats
```

### Resource Usage

- Per-task memory: ~50-200MB (varies with script)
- Per-sandbox disk: Up to 100MB
- Python runtime: ~100MB
- API server: <10MB

## Security Considerations

1. **Token Security**: Keep tokens confidential
2. **File Isolation**: Sandboxing prevents unauthorized file access
3. **Process Isolation**: Each task runs independently
4. **Timeout Protection**: Prevents denial of service
5. **Error Handling**: Errors don't leak sensitive info
6. **Encryption**: Tokens stored encrypted in SharedPreferences

## License

MIT License - See LICENSE file for details

## Support

For issues and questions:
1. Check Timber logs
2. Review endpoint response errors
3. Consult troubleshooting section
4. Check task status and output
