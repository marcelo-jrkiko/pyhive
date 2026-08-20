# Quick Start Guide

Get up and running with **KRS PyHive** in 5 minutes!

## Prerequisites

- A physical Android device **or** an emulator (API 30+).
- For physical devices: a USB cable, with `adb` available.
- Network connectivity between the device/emulator and your client.

## Installation

### Step 1 — Build and install the app

```bash
cd /opt/data/Marcelo/Projetos/krs.pyhive
./gradlew :app:installDebug
```

This compiles the debug APK and installs it on the connected device (and launches the activity). For an automated build + install + debug-prep flow, use the helper script:

```bash
bash scripts/deploy_and_prepare_debug.sh
```

### Step 2 — Launch the app

- Open **PyHive** from the app drawer, or
- `./gradlew :app:installDebug` auto-launches the activity.

The main screen shows the server status, port, and your API token (masked).

### Step 3 — Get your API token

The token is generated automatically on first run. On the main screen it appears masked; open **Settings** and enable **Show Full Token** to reveal the complete value, then copy it.

> You'll need this token for every API request.

## Your First API Call

Set the token and hit the health endpoint:

```bash
TOKEN="YOUR_TOKEN_HERE"

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/health
```

**Expected response (`200`):**

```json
{
  "status": "healthy",
  "timestamp": 1692345600000,
  "python_initialized": true
}
```

> **Emulator note:** `localhost` works only when the server runs on the host. On the Android emulator, use the emulator's IP (e.g. `http://10.0.2.2:8080/api/health`).

Using Python instead:

```python
import requests

token = "YOUR_TOKEN_HERE"
headers = {"Authorization": f"Bearer {token}"}

response = requests.get(
    "http://localhost:8080/api/health",
    headers=headers
)
print(response.json())
```

## Submit Your First Task

### Simple Print Task

Using cURL (**multipart/form-data** — note the `params` JSON and `script` text parts):

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F 'params={"script_name":"hello.py","timeout_seconds":300,"args":{"name":"World"}}' \
  -F 'script=print("Hello from Python!")' \
  http://localhost:8080/api/tasks
```

**Response (`202`):**

```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Task submitted successfully",
  "created_at": 1692345600000
}
```

> Note the JSON wire format is **snake_case** (`task_id`, `created_at`, …).

### Check Task Status

```bash
TASK_ID="550e8400-e29b-41d4-a716-446655440000"

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/$TASK_ID
```

**Response (after execution):**

```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "execution_time": 45,
  "result": "Hello from Python!",
  "output": "Hello from Python!",
  "error": null
}
```

## Common Tasks

### List All Tasks

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks
```

### Get Server Statistics

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/stats
```

### Submit a File Writing Task

The script runs inside its sandbox, so relative writes are allowed:

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F 'script=
with open("output.txt", "w") as f:
    f.write("Test output")
print("File created")
' \
  http://localhost:8080/api/tasks
```

### Schedule a Task

`params.scheduled_time` is a **millisecond** epoch; schedule 2 minutes from now:

```bash
SCHEDULED=$(( $(date +%s) * 1000 + 2 * 60 * 1000 ))

curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F "params={\"scheduled_time\":$SCHEDULED}" \
  -F "script=print('Delayed task')" \
  http://localhost:8080/api/tasks
```

## Troubleshooting

### Issue: "Connection refused"

**Solution:**
- Ensure the app is running
- Check device has network access
- For emulator: use 10.0.2.2 instead of localhost
- Check firewall isn't blocking port 8080

```bash
# For emulator
curl http://10.0.2.2:8080/api/health
```

### Issue: "Unauthorized: Invalid or missing bearer token"

**Solution:**
- Copy the exact token from the app UI
- Include "Bearer " prefix
- Check spelling and case

```bash
# Correct format
Authorization: Bearer abc123def456ghi...
```

### Issue: "Task stuck in RUNNING"

**Solution:**
- Check script for infinite loops
- Cancel and retry
- Increase timeout if needed

```bash
# Cancel task
curl -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/$TASK_ID/cancel
```

## Next Steps

1. **Read Full Documentation**: See [docs/README.md](../docs/README.md)
2. **Explore API Examples**: See [docs/API_EXAMPLES.md](../docs/API_EXAMPLES.md)
3. **Try Example Scripts**: See [examples/EXAMPLE_SCRIPTS.md](EXAMPLE_SCRIPTS.md)
4. **Advanced Features**: Read about [Sandboxing](#) and [Scheduling](#)

## Key Concepts

### Tasks
- Unit of work (Python script execution)
- Each has unique ID
- Progress tracked from PENDING → COMPLETED/FAILED

### Sandboxes
- Isolated file system per task
- Security feature
- Maximum 100MB per sandbox

### Bearer Token
- Authentication mechanism
- Required for all API requests
- Auto-generated on first run
- Stored securely in encrypted SharedPreferences

### Status Codes

| Status | Meaning |
|--------|---------|
| PENDING | Waiting to run |
| RUNNING | Currently executing |
| COMPLETED | Finished successfully |
| FAILED | Error during execution |
| SCHEDULED | Scheduled for future |
| CANCELLED | Cancelled by user |

## API Cheat Sheet

```bash
# Health check
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/health

# Submit task
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F 'script=print("test")' \
  http://localhost:8080/api/tasks

# Get task status
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}

# List tasks
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks

# Get statistics
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/stats

# Cancel task
curl -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}/cancel

# Delete task
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/{taskId}
```

## Performance Tips

1. **Parallel Submission**: Submit multiple tasks at once
2. **Tag Tasks**: Use tags for organizing and filtering
3. **Monitor Stats**: Check `/stats` endpoint for performance
4. **Cleanup**: Regularly delete old completed tasks
5. **Optimize Scripts**: Keep Python scripts efficient

## Getting Help

1. **Check Logs**: Enable Timber logging in Android Studio
2. **Review Documentation**: Complete docs in `/docs/`
3. **Try Examples**: Run examples from `/examples/`
4. **Check Status**: Use health endpoint to verify server state

---

Happy coding! 🚀

For detailed information, see [docs/README.md](../docs/README.md)
