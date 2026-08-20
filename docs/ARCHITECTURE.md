# Architecture Documentation

## System Architecture Overview

Python Task Runner is built with a layered architecture that emphasizes separation of concerns, security, and scalability.

## Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│ Presentation Layer                                      │
│ ├─ MainActivity (UI Status Display)                     │
│ └─ PythonTaskRunnerApp (Lifecycle Management)          │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│ API Layer                                               │
│ ├─ PythonTaskRunnerService (HTTP Server)               │
│ ├─ REST Endpoints                                       │
│ └─ Request/Response Handling                            │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│ Authentication & Security Layer                         │
│ ├─ AuthenticationManager (Token Management)            │
│ ├─ Bearer Token Generation & Validation                │
│ └─ Encrypted SharedPreferences Storage                 │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│ Business Logic Layer                                    │
│ ├─ TaskScheduler (Task Lifecycle Management)           │
│ ├─ Task Status Tracking                                │
│ ├─ Retry Logic                                         │
│ └─ Task Statistics                                     │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│ Security & Isolation Layer                             │
│ ├─ SandboxManager (File Isolation)                     │
│ ├─ Sandbox Creation/Cleanup                            │
│ ├─ File Access Restrictions                            │
│ └─ Resource Quotas                                     │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│ Python Runtime Layer                                    │
│ ├─ PythonRuntimeManager (Python Integration)           │
│ ├─ Script Execution                                    │
│ ├─ Timeout Handling                                    │
│ └─ Output Capture                                      │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│ Android System Layer                                    │
│ ├─ File System                                         │
│ ├─ Coroutines                                          │
│ └─ Shared Preferences                                  │
└─────────────────────────────────────────────────────────┘
```

## Component Interactions

### 1. Request Flow

```
Client HTTP Request
        ↓
PythonTaskRunnerService (receives request)
        ↓
AuthenticationManager (validates token)
        ↓
Request Router (routes to appropriate handler)
        ↓
API Handler (processes request)
        ↓
TaskScheduler (manages task)
        ↓
Response Builder
        ↓
Client HTTP Response
```

### 2. Task Execution Flow

```
Submit Task Request
        ↓
TaskScheduler.submitTask()
        ↓
SandboxManager.createSandbox()
        ↓
executorService.execute() (scheduled execution)
        ↓
TaskScheduler.updateTaskStatus(RUNNING)
        ↓
PythonRuntimeManager.executePythonScript()
        ↓
Script Execution (in isolated thread)
        ↓
Capture stdout/stderr
        ↓
TaskScheduler.updateTaskStatus(COMPLETED/FAILED)
        ↓
Notify Listeners
        ↓
SandboxManager (optionally cleanup)
```

### 3. Sandbox Isolation Flow

```
PythonScript
        ↓
PythonRuntimeManager wraps script
        ↓
Adds SandboxManager.getFileAccessRestrictionModule()
        ↓
Script executes with restrictions
        ↓
File open() calls intercepted
        ↓
SandboxManager validates path
        ↓
Path in sandbox? Yes → Allow
              No  → Raise PermissionError
```

## Key Components

### PythonTaskRunnerApp
- **Responsibility**: Application lifecycle management
- **Lifecycle**: 
  - onCreate: Initializes all components
  - onTerminate: Cleanup and shutdown
- **Manages**:
  - All manager singletons
  - Component initialization
  - Logging setup
  - Token generation

### AuthenticationManager
- **Responsibility**: Bearer token authentication
- **Functions**:
  - generateNewToken() - Create new 256-bit tokens
  - validateToken() - Check incoming request tokens
  - extractToken() - Parse Authorization header
- **Storage**: EncryptedSharedPreferences (AES-256)

### PythonTaskRunnerService
- **Responsibility**: HTTP REST API server
- **Architecture**: 
  - Lightweight HTTP server on port 8080
  - Non-blocking I/O using Coroutines
  - Per-connection handler threads
- **Endpoints**:
  - POST /tasks - Submit task
  - GET /tasks - List tasks
  - GET /tasks/{id} - Get task status
  - PUT /tasks/{id}/cancel - Cancel task
  - PUT /tasks/{id}/reschedule - Reschedule task
  - DELETE /tasks/{id} - Delete task
  - GET /stats - Get statistics
  - GET /health - Health check

### TaskScheduler
- **Responsibility**: Task lifecycle and scheduling
- **Features**:
  - Task registry (in-memory)
  - ScheduledExecutorService (4 concurrent threads)
  - Automatic timeout handling
  - Retry mechanism
  - Status listener notifications
- **Task States**:
  ```
  PENDING ──→ RUNNING ──→ COMPLETED
    ↓
  SCHEDULED
    ↓
  RUNNING ──→ FAILED ──→ (can retry)
  
  Any state → CANCELLED (by user)
  ```

### PythonRuntimeManager
- **Responsibility**: Python script execution
- **Architecture**:
  - Uses Chaquopy for Python 3.11 runtime
  - Sandbox script wrapping
  - Timeout enforcement via ExecutorService
  - Output capture
- **Execution Model**:
  - Each script runs in separate thread
  - Timeout monitored via Thread.join(timeout)
  - stdout/stderr captured
  - Exceptions logged and reported

### SandboxManager
- **Responsibility**: File isolation and security
- **Architecture**:
  - Per-task sandbox directory
  - ConcurrentHashMap for path tracking
  - File access restriction module
- **Security Features**:
  - Path validation
  - Canonical path checking
  - File operation interception (open, os.open, os.listdir)
  - Size quotas (100MB per sandbox)
  - Automatic cleanup (7 days old)

## Data Model

### Task Model
```kotlin
data class PythonTask(
    val taskId: String,              // Unique identifier
    val scriptContent: String,        // Python script to execute
    val scriptName: String,           // Script filename
    var status: String,               // PENDING, RUNNING, etc.
    var scheduledTime: Long?,         // When to execute (optional)
    var executionTime: Long,          // Execution duration in ms
    val createdAt: Long,              // Creation timestamp
    var startedAt: Long?,             // Execution start time
    var completedAt: Long?,           // Completion time
    var result: String,               // Script output/result
    var error: String,                // Error message if failed
    var output: String,               // Captured stdout/stderr
    val userId: String,               // Task owner
    val sandboxDir: String,           // Sandbox directory path
    val timeoutSeconds: Long,         // Execution timeout
    var retryCount: Int,              // Current retry count
    val maxRetries: Int,              // Maximum retry attempts
    val tags: List<String>            // Task tags for filtering
)
```

### API Models
```kotlin
// Parameters carried in the multipart `params` field when submitting a task
data class SubmitTaskParams(
    val scriptName: String?,
    val scheduledTime: Long?,
    val timeoutSeconds: Long,
        val tags: List<String>,
        val args: JsonElement? // Optional JSON object passed to main(args)
)

data class TaskStatusResponse(
    val taskId: String,
    val status: String,
    val executionTime: Long,
    val result: String?,
    val output: String?,
    val error: String?,
    val progress: Int
)

data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val details: String?,
    val timestamp: Long
)
```

## Thread Model

### Main Thread
- Activity UI updates
- Application initialization

### Coroutine Thread (IO Dispatcher)
- API server loop
- Client connection handling
- Request routing

### TaskScheduler ExecutorService
- Task execution (4 concurrent threads)
- Scheduled task waiting
- Timeout monitoring

### Python Execution Thread
- Isolated script execution
- Per-task thread
- Monitored for timeout

## Concurrency Model

```
Main App Thread
    ├─ API Server Loop (Coroutine)
    │   └─ Client Handler (Coroutine per connection)
    │       └─ Request Processing
    │
    └─ TaskScheduler ExecutorService
        ├─ Thread 1 (Task execution)
        ├─ Thread 2 (Task execution)
        ├─ Thread 3 (Task execution)
        └─ Thread 4 (Task execution)
            └─ Python Script Thread
                └─ Script Execution
```

## Security Architecture

### Token Security
```
Token Generation
    ↓ (SecureRandom 256-bit)
Base64 Encoded Token
    ↓ (AES-256 encryption)
EncryptedSharedPreferences
    ↓ (per-request validation)
Request Authorization
```

### Sandbox Security
```
Python Script
    ↓ (wrapped with restrictions)
File Access Interception
    ↓ (path validation)
Canonical Path Check
    ↓ (is path in sandbox?)
Yes → Allow File Operation
No  → Raise PermissionError
```

### Process Isolation
```
Task 1 ──────────────────┐
                         ├─ Independent execution
Task 2 ──────────────────┤   Independent sandboxes
                         ├─ Independent timeouts
Task 3 ──────────────────┤
                         └─ Failure doesn't affect others
Task 4
```

## Error Handling Strategy

### Three-Tier Error Handling

**Tier 1: Script Level**
- Python exceptions caught
- Stderr redirected to error field
- Stack trace included

**Tier 2: Execution Level**
- Timeout exceptions caught
- Resource limit exceptions caught
- Task marked FAILED with error message

**Tier 3: API Level**
- HTTP error status codes
- ErrorResponse JSON format
- Descriptive error messages

```
Script Error
    ↓
ExecutionResult.error = "error message"
    ↓
Task.status = FAILED
Task.error = error message
    ↓
API Response (200 OK, but status=FAILED)
```

## Performance Characteristics

### Task Submission
- Time: O(1) - constant time
- Operation: In-memory registry update
- Latency: <10ms

### Task Lookup
- Time: O(1) - hash map lookup
- Operation: Registry query
- Latency: <1ms

### Task Listing
- Time: O(n) - linear scan
- Operation: Filter registry
- Latency: Depends on task count

### Python Execution
- Time: O(script complexity)
- Operation: Chaquopy runtime execution
- Latency: Depends on script
- Limits: Timeout-bounded (default 5min)

### Sandbox Creation
- Time: O(1) - directory creation
- Operation: File system operation
- Latency: 10-50ms

## Scalability Considerations

### Task Concurrency
- **Current**: 4 concurrent tasks
- **Configurable**: Change `MAX_CONCURRENT_TASKS` in TaskScheduler
- **Memory**: ~200MB per task (varies)
- **Limit**: Device RAM

### Task Storage
- **Current**: In-memory only
- **Limitation**: Tasks lost on app restart
- **Future**: Consider Room Database persistence

### Sandbox Storage
- **Max per sandbox**: 100MB (configurable)
- **Cleanup**: Automatic after 7 days
- **Device limit**: Total available storage

### API Throughput
- **Connections**: Limited by OS socket limits
- **Requests/sec**: ~50-100 (rough estimate)
- **Bottleneck**: Python runtime, not API

## Extending the Architecture

### Adding New Endpoints

1. Add route pattern to `routeRequest()` method
2. Create handler method `handleNewEndpoint()`
3. Implement business logic
4. Return JSON response via `sendJsonResponse()`

### Adding Task Persistence

1. Create Room database schema
2. Add TaskDao interface
3. Update TaskScheduler to persist tasks
4. Implement recovery on app restart

### Adding Authentication Methods

1. Extend AuthenticationManager
2. Support OAuth2, API keys, etc.
3. Update request validation logic
4. Add token refresh capability

### Adding Resource Quotas

1. Create ResourceQuotaManager
2. Track per-task resource usage
3. Enforce limits during execution
4. Report quota violations

## Monitoring and Observability

### Logging
- Timber for structured logging
- Different log levels (D, I, W, E)
- Filterable by tag

### Metrics
- Task statistics endpoint
- Execution time tracking
- Error rate monitoring
- Sandbox size monitoring

### Health Checks
- `/health` endpoint
- Python runtime status
- API server status
- Task scheduler health

---

For implementation details, see specific component documentation.
