# Python Task Runner - Android Application

A sophisticated Android application that transforms your device into a Python execution server. This app embeds a Python 3.11 runtime and provides a comprehensive REST API for executing, scheduling, and managing Python scripts with secure sandboxing and task isolation.

## 🚀 Quick Overview

```
Your Device
    ↓
[Python Task Runner App]
    ├─ Python 3.11 Runtime (Chaquopy)
    ├─ REST API Server (port 8080)
    ├─ Task Scheduler
    ├─ File Sandboxing
    └─ Bearer Token Authentication
    ↓
REST API Clients (curl, Python, Node.js, etc.)
```

## ✨ Key Features

- **🐍 Embedded Python**: Full Python 3.11 runtime via Chaquopy
- **🔒 Secure Sandboxing**: File access restricted to per-task directories
- **📡 REST API**: Complete HTTP API with Bearer Token auth
- **⏱️ Task Scheduling**: Immediate and scheduled task execution
- **🔄 Concurrent Execution**: Up to 4 concurrent tasks (configurable)
- **📊 Monitoring**: Real-time task statistics and health checks
- **🛡️ Isolation**: Complete task isolation with independent sandboxes
- **📝 Comprehensive Logging**: Timber-based structured logging
- **🎯 Modern Architecture**: Layered architecture with clear separation of concerns

## 📋 Requirements

- **Android**: API Level 26+ (Android 8.0+)
- **Storage**: 500MB+ available space
- **RAM**: 1GB+ recommended
- **Build Tool**: Android Studio 2022.1+

## 🏗️ Project Structure

```
krs.pyhive/
├── app/                           # Android application
│   ├── src/main/
│   │   ├── kotlin/com/pythontaskrunner/
│   │   │   ├── api/              # REST API endpoints
│   │   │   ├── auth/             # Authentication layer
│   │   │   ├── models/           # Data models
│   │   │   ├── python/           # Python runtime
│   │   │   ├── sandbox/          # File sandboxing
│   │   │   ├── scheduler/        # Task scheduling
│   │   │   ├── utils/            # Utilities
│   │   │   ├── MainActivity.kt
│   │   │   └── PythonTaskRunnerApp.kt
│   │   └── res/                  # Resources
│   ├── build.gradle.kts
│   └── AndroidManifest.xml
├── docs/                          # Documentation
│   ├── README.md                 # Full documentation
│   ├── QUICKSTART.md             # Get started in 5 minutes
│   ├── API_EXAMPLES.md           # API usage examples
│   ├── ARCHITECTURE.md           # System architecture
│   └── DEVELOPMENT.md            # Development guide
├── examples/                      # Example scripts
│   ├── README.md
│   └── EXAMPLE_SCRIPTS.md        # Python script examples
└── build.gradle.kts              # Root build file
```

## 📖 Documentation

| Document | Purpose |
|----------|---------|
| [docs/README.md](docs/README.md) | Complete documentation, API reference, troubleshooting |
| [docs/QUICKSTART.md](docs/QUICKSTART.md) | Get started in 5 minutes |
| [docs/API_EXAMPLES.md](docs/API_EXAMPLES.md) | cURL, Python, Node.js examples |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design and architecture |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Development and contribution guide |
| [examples/README.md](examples/README.md) | Quick reference for examples |
| [examples/EXAMPLE_SCRIPTS.md](examples/EXAMPLE_SCRIPTS.md) | Python script examples |

## 🚀 Quick Start

### 1. Build and Install

```bash
cd krs.pyhive
./gradlew installDebug
```

### 2. Get Your API Token

The token appears in the app UI when it launches.

### 3. Test the API

```bash
# Health check
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/health

# Submit a task
curl -X POST \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"script_content": "print(\"Hello from Python!\")"}' \
  http://localhost:8080/api/tasks
```

See [QUICKSTART.md](docs/QUICKSTART.md) for detailed setup instructions.

## 📡 REST API

### Endpoints Overview

```
POST   /api/tasks               # Submit new task
GET    /api/tasks               # List tasks
GET    /api/tasks/{id}          # Get task status
PUT    /api/tasks/{id}/cancel   # Cancel task
PUT    /api/tasks/{id}/reschedule  # Reschedule task
DELETE /api/tasks/{id}          # Delete task
GET    /api/stats               # Server statistics
GET    /api/health              # Health check
```

### Example: Submit and Execute Task

```bash
# 1. Submit task
RESPONSE=$(curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"script_content": "print(1 + 1)"}' \
  http://localhost:8080/api/tasks)

TASK_ID=$(echo $RESPONSE | jq -r '.task_id')

# 2. Check status
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/$TASK_ID | jq '.'
```

For complete API documentation, see [docs/README.md](docs/README.md).

## 🔐 Authentication

All API requests require Bearer Token authentication:

```bash
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/health
```

Token is:
- Generated automatically on first app run
- Stored securely (AES-256 encryption)
- 256-bit random value
- Unique per device

## 🎯 Example Use Cases

### 1. Data Processing

```python
import json

data = {"items": [1, 2, 3, 4, 5]}
results = [x * 2 for x in data["items"]]

with open("output.json", "w") as f:
    json.dump({"original": data["items"], "doubled": results}, f)

print("Processing complete")
```

### 2. Batch Processing

```bash
for i in {1..10}; do
  curl -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"script_content\": \"print('Item $i')\"}" \
    http://localhost:8080/api/tasks
done
```

### 3. Scheduled Tasks

```bash
# Schedule for 1 hour from now
FUTURE=$(( $(date +%s) * 1000 + 3600000 ))

curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"script_content\": \"print('Scheduled!')\",
    \"scheduled_time\": $FUTURE
  }" \
  http://localhost:8080/api/tasks
```

See [examples/README.md](examples/README.md) for more examples.

## 🏗️ Architecture

The application follows a layered architecture:

```
┌─────────────────────────────────────────┐
│        REST API Layer                   │
│    (PythonTaskRunnerService)            │
├─────────────────────────────────────────┤
│        Authentication Layer             │
│    (AuthenticationManager)              │
├─────────────────────────────────────────┤
│        Business Logic Layer             │
│    (TaskScheduler)                      │
├─────────────────────────────────────────┤
│    Security & Isolation Layer           │
│   (SandboxManager)                      │
├─────────────────────────────────────────┤
│    Python Runtime Layer                 │
│   (PythonRuntimeManager)                │
└─────────────────────────────────────────┘
```

For detailed architecture, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## 🔒 Security Features

- **Token Authentication**: Bearer token-based API access
- **File Sandboxing**: Each task isolated to own directory
- **Path Validation**: Runtime file access restrictions
- **Task Isolation**: Independent execution environments
- **Resource Quotas**: Per-sandbox disk space limits
- **Encrypted Storage**: Tokens stored with AES-256
- **Error Isolation**: Failures don't affect other tasks

## 📊 Task Management

### Task States

```
PENDING  ──→ RUNNING  ──→ COMPLETED
  ↓
SCHEDULED
  ↓
RUNNING  ──→ FAILED  ──→ (can retry)
  ↓
CANCELLED (user initiated)
```

### Features

- **Status Tracking**: Real-time task status
- **Result Capture**: stdout and stderr captured
- **Timeout Handling**: Automatic timeout enforcement
- **Retry Logic**: Automatic retry for failed tasks
- **Task Filtering**: Filter by status, tag, or date
- **Cancellation**: Cancel pending/scheduled tasks
- **Rescheduling**: Reschedule tasks for later execution

## 🛠️ Dependencies

Key dependencies:

- **Chaquopy** 3.11 - Python runtime
- **Retrofit2** 2.10.0 - HTTP client
- **OkHttp3** 4.11.0 - HTTP library
- **Gson** 2.10.1 - JSON serialization
- **Timber** 5.0.1 - Logging
- **Hilt** 2.48 - Dependency injection
- **Room** 2.5.2 - Local database (optional)
- **Kotlinx Coroutines** 1.7.3 - Async operations

See [app/build.gradle.kts](app/build.gradle.kts) for complete dependencies.

## 🧪 Testing

### Run Tests

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew connectedAndroidTest

# With coverage
./gradlew testDebugUnitTestCoverage
```

## 📱 Building

### Debug Build

```bash
./gradlew installDebug
```

### Release Build

```bash
./gradlew bundleRelease
```

### Custom Port

Edit [PythonTaskRunnerApp.kt](app/src/main/kotlin/com/pythontaskrunner/PythonTaskRunnerApp.kt):

```kotlin
apiService = PythonTaskRunnerService(
    ...,
    port = 9000  // Change port
)
```

## 📝 Logging

Enable Timber logging to monitor execution:

```bash
# View logs
adb logcat | grep "pythontaskrunner"

# Filter by level
adb logcat | grep -i "error\|exception"
```

## 🤝 Contributing

Contributions welcome! See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for:
- Development environment setup
- Adding new features
- Testing guidelines
- Code style conventions

## 📄 License

MIT License - See LICENSE file

## 🆘 Support

For issues and questions:

1. **Check Docs**: Review [docs/README.md](docs/README.md)
2. **Quickstart**: See [docs/QUICKSTART.md](docs/QUICKSTART.md)
3. **Examples**: Check [examples/EXAMPLE_SCRIPTS.md](examples/EXAMPLE_SCRIPTS.md)
4. **Architecture**: Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
5. **Troubleshooting**: See troubleshooting section in [docs/README.md](docs/README.md)

## 🎯 Roadmap

Future features under consideration:

- [ ] Database persistence (Room)
- [ ] OAuth2 authentication
- [ ] Advanced scheduling (cron)
- [ ] WebSocket support for real-time updates
- [ ] Custom Python package installation
- [ ] Resource metrics and monitoring
- [ ] Multi-user support
- [ ] Task dependencies
- [ ] Plugin system

## 📞 Contact

For questions and suggestions, refer to the documentation or open an issue.

---

**Happy Task Running!** 🚀

Start with [QUICKSTART.md](docs/QUICKSTART.md) to get up and running in 5 minutes!
