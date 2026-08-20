# Development Guide

This guide covers how to develop, test, and extend the Python Task Runner.

## Table of Contents

1. [Development Environment Setup](#development-environment-setup)
2. [Project Structure](#project-structure)
3. [Building and Running](#building-and-running)
4. [Debugging](#debugging)
5. [Adding New Features](#adding-new-features)
6. [Testing](#testing)
7. [Deployment](#deployment)
8. [Common Development Tasks](#common-development-tasks)

## Development Environment Setup

### Prerequisites

```bash
# Minimum requirements
- Android Studio 2022.1+
- Android SDK 26+ (API Level 26+)
- Kotlin 1.9.10+
- Gradle 8.0+
```

### Setup Steps

1. **Install Android Studio**
   ```bash
   # Download from developer.android.com
   # Run installer and follow prompts
   ```

2. **Install Android SDK**
   ```bash
   # Open Android Studio
   # Tools → SDK Manager
   # Install SDK 26+ (API Levels 26, 34)
   # Install Build Tools 34.0.0
   ```

3. **Clone/Extract Project**
   ```bash
   git clone <repository-url> krs.pyhive
   cd krs.pyhive
   ```

4. **Sync Gradle**
   ```bash
   # Android Studio: File → Sync Now
   # Or terminal:
   ./gradlew sync
   ```

5. **Create Local SDK**
   ```bash
   # Android Studio: Tools → SDK Manager
   # Note: SDK location is shown at top
   # Or use environment variable:
   export ANDROID_SDK_ROOT=$HOME/Android/Sdk
   ```

## Project Structure

```
krs.pyhive/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/pythontaskrunner/
│   │   │   │   ├── api/
│   │   │   │   │   └── PythonTaskRunnerService.kt
│   │   │   │   ├── auth/
│   │   │   │   │   └── AuthenticationManager.kt
│   │   │   │   ├── models/
│   │   │   │   │   └── TaskModels.kt
│   │   │   │   ├── python/
│   │   │   │   │   └── PythonRuntimeManager.kt
│   │   │   │   ├── sandbox/
│   │   │   │   │   └── SandboxManager.kt
│   │   │   │   ├── scheduler/
│   │   │   │   │   └── TaskScheduler.kt
│   │   │   │   ├── utils/
│   │   │   │   │   └── Utils.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── PythonTaskRunnerApp.kt
│   │   │   └── res/
│   │   │       ├── layout/
│   │   │       └── values/
│   │   └── test/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
├── docs/
│   ├── README.md
│   ├── QUICKSTART.md
│   ├── API_EXAMPLES.md
│   ├── ARCHITECTURE.md
│   └── DEVELOPMENT.md
└── examples/
    ├── EXAMPLE_SCRIPTS.md
    └── (Python script examples)
```

## Building and Running

### Build Variants

```bash
# Debug build (faster, more logging)
./gradlew assemble Debug

# Release build (optimized, smaller)
./gradlew assembleRelease

# Install on device
./gradlew installDebug

# Run on device (build + install + launch)
./gradlew runDebug
```

### Build with Specific Configuration

```bash
# Clean build
./gradlew clean build

# Build with asserts enabled
./gradlew build -Pdebug=true

# Skip tests
./gradlew build -x test
```

### APK Generation

```bash
# Generate debug APK
./gradlew assemble Debug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Generate release APK (unsigned)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk

# Sign release APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore my-key-store app-release-unsigned.apk my-key-alias

# Align APK
zipalign -v 4 app-release-unsigned.apk app-release.apk
```

## Debugging

### Using Android Studio Debugger

1. **Set Breakpoints**
   - Click left margin of code line
   - Appears as red dot

2. **Start Debugging**
   ```bash
   ./gradlew installDebug
   ```
   - Run → Debug 'app'
   - Or: Shift + F9

3. **Debug Actions**
   - F6: Step over
   - F7: Step into
   - Shift + F8: Step out
   - F9: Resume
   - Ctrl + F8: Toggle breakpoint

### Logcat Filtering

```bash
# Show all logs
adb logcat

# Filter by tag
adb logcat | grep "python"

# Filter by log level
adb logcat *:W  # Warnings and errors only

# Save to file
adb logcat > logcat.txt

# Clear logcat
adb logcat -c
```

### Timber Logging

```kotlin
// In code
Timber.d("Debug message: $value")
Timber.i("Info: User submitted task")
Timber.w("Warning: High memory usage")
Timber.e("Error: Failed to initialize Python")

// View in logcat
adb logcat | grep "python\|TaskRunner"
```

### Profiling

```bash
# CPU Profiling
- Android Studio → Run → Profile 'app'
- Select CPU Profiler
- Record and analyze

# Memory Profiling
- Android Studio → Run → Profile 'app'
- Select Memory Profiler
- Monitor allocations

# Network Profiling
- Check Network Profiler tab
- Monitor API requests
```

## Adding New Features

### Adding a New API Endpoint

1. **Define Route**
   ```kotlin
    // In ApiRoutes.kt
    private val newEndpointRoute = Regex("^/api/new-endpoint$")

    // In ApiRoutes.Match
    object NewEndpoint : Match()

    // In ApiRoutes.match()
    "GET" -> if (newEndpointRoute.matches(path)) Match.NewEndpoint else null

    // In PythonTaskRunnerService.routeRequest()
    is ApiRoutes.Match.NewEndpoint -> handleNewEndpoint(writer)
   ```

2. **Create Handler**
   ```kotlin
   private suspend fun handleNewEndpoint(writer: BufferedWriter) {
       try {
           val result = // ... implementation
           sendJsonResponse(writer, 200, result)
       } catch (e: Exception) {
           sendErrorResponse(writer, 500, e.message)
       }
   }
   ```

3. **Add Response Model**
   ```kotlin
   data class NewEndpointResponse(
       val field1: String,
       val field2: Int
   )
   ```

4. **Test**
   ```bash
   curl http://localhost:8080/api/new-endpoint
   ```

### Adding Task Persistence (Room Database)

1. **Create Entity**
   ```kotlin
   @Entity(tableName = "tasks")
   data class TaskEntity(
       @PrimaryKey val taskId: String,
       val scriptContent: String,
       // ... other fields
   )
   ```

2. **Create DAO**
   ```kotlin
   @Dao
   interface TaskDao {
       @Insert
       suspend fun insert(task: TaskEntity)
       
       @Query("SELECT * FROM tasks WHERE taskId = :id")
       suspend fun getById(id: String): TaskEntity?
   }
   ```

3. **Create Database**
   ```kotlin
   @Database(entities = [TaskEntity::class], version = 1)
   abstract class TaskDatabase : RoomDatabase() {
       abstract fun taskDao(): TaskDao
   }
   ```

4. **Update TaskScheduler**
   ```kotlin
   // Add DAO injection
   class TaskScheduler(..., private val taskDao: TaskDao) {
       override fun submitTask(task: PythonTask): String {
           taskRegistry[task.taskId] = task
           viewModelScope.launch {
               taskDao.insert(task.toEntity())
           }
           // ...
       }
   }
   ```

### Adding Custom Python Packages

1. **Chaquopy Configuration** (in build.gradle.kts)
   ```kotlin
   android {
       defaultConfig {
           python {
               pip {
                   install "numpy==1.24.0"
                   install "pandas==1.5.0"
               }
           }
       }
   }
   ```

2. **Use in Script**
   ```python
   import numpy as np
   import pandas as pd
   
   # Use packages
   ```

### Adding Authentication Methods

1. **Create New Manager**
   ```kotlin
   class OAuth2Manager(private val context: Context) {
       fun authenticate(token: String): Boolean {
           // OAuth2 implementation
       }
   }
   ```

2. **Update Authentication**
   ```kotlin
   // In PythonTaskRunnerService
   val authHeader = headers[AUTH_HEADER.lowercase()]
   when {
       authManager.validateToken(authHeader) -> handleRequest()
       oauth2Manager.validateToken(authHeader) -> handleRequest()
       else -> sendErrorResponse(writer, 401, "Unauthorized")
   }
   ```

## Testing

### Unit Tests

Create `app/src/test/kotlin/com/pythontaskrunner/`:

```kotlin
class AuthenticationManagerTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var authManager: AuthenticationManager
    
    @Before
    fun setup() {
        prefs = mock()
        authManager = AuthenticationManager(prefs)
    }
    
    @Test
    fun testTokenGeneration() {
        val token = authManager.generateNewToken()
        assertNotNull(token)
        assertTrue(token.length > 0)
    }
    
    @Test
    fun testTokenValidation() {
        val token = authManager.generateNewToken()
        val header = "Bearer $token"
        assertTrue(authManager.validateToken(header))
    }
}
```

### Integration Tests

Create `app/src/androidTest/kotlin/com/pythontaskrunner/`:

```kotlin
@RunWith(AndroidJUnit4::class)
class PythonTaskRunnerServiceTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    @Test
    fun testHealthEndpoint() {
        // Test API endpoint
        val client = HttpClient()
        val response = client.get("http://localhost:8080/api/health")
        assertEquals(200, response.statusCode)
    }
}
```

### Run Tests

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew connectedAndroidTest

# Specific test class
./gradlew test --tests "AuthenticationManagerTest"

# With coverage
./gradlew testDebugUnitTestCoverage
```

## Deployment

### Pre-Release Checklist

- [ ] Code review completed
- [ ] All tests passing
- [ ] No lint warnings
- [ ] Version number updated
- [ ] Release notes prepared
- [ ] Documentation updated
- [ ] Performance tested

### Version Management

Edit `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 2  // Increment for each release
    versionName = "1.1.0"  // Semantic versioning
}
```

### Release Build

```bash
# Clean
./gradlew clean

# Build release APK
./gradlew bundleRelease

# Or for Google Play
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

### Sign Release

```bash
# Create keystore (one time)
keytool -genkey -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-key-alias

# Sign APK
jarsigner -verbose -sigalg SHA256withRSA \
  -digestalg SHA-256 -keystore my-release-key.jks \
  app-release-unsigned.apk my-key-alias

# Verify signature
jarsigner -verify -verbose -certs app-release-unsigned.apk

# Align
zipalign -v 4 app-release-unsigned.apk app-release-final.apk
```

## Common Development Tasks

### Clear App Data

```bash
# Remove app and all data
adb uninstall krs.pyhive

# Or keep app but clear data
adb shell pm clear krs.pyhive
```

### Check App Storage

```bash
# View app directories
adb shell ls -la /data/data/krs.pyhive/

# View external storage
adb shell ls -la /sdcard/Android/data/krs.pyhive/
```

### View Shared Preferences

```bash
# Access via adb
adb shell
cd /data/data/krs.pyhive/shared_prefs
cat python_task_runner_prefs.xml
```

### Monitor Resources

```bash
# CPU and memory
adb shell dumpsys meminfo | grep pythontaskrunner

# Network
adb shell netstat | grep 8080

# Full dump
adb bugreport
```

### Generate API Documentation

```bash
# Generate Dokka documentation
./gradlew dokkaHtml

# Output: app/build/dokka/html/
# Open in browser: file:///<path>/index.html
```

### Code Style and Lint

```bash
# Run lint
./gradlew lint

# Run with Ktlint
./gradlew ktlintCheck

# Format with Ktlint
./gradlew ktlintFormat

# Check code coverage
./gradlew testDebugUnitTestCoverage
```

## Troubleshooting Development

### Build Fails

```bash
# Clean Gradle cache
./gradlew clean

# Rebuild with verbose output
./gradlew build --info

# Check Gradle version
./gradlew --version
```

### Python Import Errors

```bash
# In Chaquopy, check available packages
# Edit build.gradle.kts and add package

defaultConfig {
    python {
        pip {
            install "package-name"
        }
    }
}
```

### API Port Already in Use

```bash
# Find process using port 8080
lsof -i :8080

# Or in code, change port:
PythonTaskRunnerService(..., port = 8081)
```

### Emulator Connection Issues

```bash
# Restart adb
adb kill-server
adb start-server

# List devices
adb devices

# Connect to emulator
adb connect emulator-5554
```

## Performance Optimization

### Memory Optimization

```kotlin
// Use weak references for large objects
private val cache: WeakHashMap<String, ByteArray> = WeakHashMap()

// Limit concurrent tasks
const val MAX_CONCURRENT_TASKS = 4

// Cleanup old tasks regularly
taskScheduler.cleanupOldTasks()
```

### Battery Optimization

```kotlin
// Avoid frequent network polling
// Use scheduled intervals instead of continuous loops

// Batch API requests when possible
// Reduce logging in production
```

### Network Optimization

```kotlin
// Implement request batching
// Use gzip compression
// Limit payload sizes
const val MAX_PAYLOAD_SIZE = 10 * 1024 * 1024  // 10MB
```

---

For more information, see related documentation files.
