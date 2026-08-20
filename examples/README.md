# Examples Directory

This directory contains example Python scripts and use cases for the Python Task Runner.

## Contents

- **EXAMPLE_SCRIPTS.md** - Comprehensive collection of example Python scripts

## Quick Start Examples

### 1. Submit a Simple Script

```bash
TOKEN="YOUR_API_TOKEN"

curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F 'script=print("Hello World")' \
  http://localhost:8080/api/tasks
```

### 2. Process Data

```python
import json

data = {
    "items": [1, 2, 3, 4, 5],
    "operations": ["double", "square"]
}

results = {
    "doubled": [x * 2 for x in data["items"]],
    "squared": [x ** 2 for x in data["items"]]
}

with open("results.json", "w") as f:
    json.dump(results, f, indent=2)

print("Processing complete")
```

### 3. Read and Write Files

```python
# Write file
with open("output.txt", "w") as f:
    f.write("Line 1\n")
    f.write("Line 2\n")
    f.write("Line 3\n")

# Read file
with open("output.txt", "r") as f:
    lines = f.readlines()

print(f"Wrote and read {len(lines)} lines")
```

## Use Case Examples

### Batch Processing

```bash
#!/bin/bash
# Process multiple items in parallel

for i in {1..10}; do
  curl -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -F "params={\"tags\":[\"batch\",\"item_$i\"]}" \
    -F "script=print('Processing item $i')" \
    http://localhost:8080/api/tasks &
done

wait
echo "All batch tasks submitted"
```

### Scheduled Report

```bash
# Schedule task for specific time
TOMORROW=$(date -d "tomorrow 06:00" +%s)000

curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F "params={\"scheduled_time\":$TOMORROW,\"tags\":[\"daily_report\"]}" \
  -F "script=print('Daily report generated')" \
  http://localhost:8080/api/tasks
```

### Monitoring

```bash
#!/bin/bash
# Monitor task status

TASK_ID=$1
TOKEN="YOUR_API_TOKEN"

while true; do
  STATUS=$(curl -s \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/tasks/$TASK_ID | jq -r '.status')
  
  echo "Status: $STATUS"
  
  if [[ "$STATUS" == "COMPLETED" || "$STATUS" == "FAILED" ]]; then
    break
  fi
  
  sleep 2
done

# Get final result
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/$TASK_ID | jq '.'
```

### Error Handling

```python
import sys
import traceback

def safe_operation():
    try:
        # Your code here
        result = 10 / 2
        return result
    except ZeroDivisionError:
        print("Error: Division by zero")
        return None
    except Exception as e:
        print(f"Unexpected error: {e}")
        traceback.print_exc()
        sys.exit(1)

result = safe_operation()
if result is not None:
    print(f"Result: {result}")
```

## Testing Examples

### Test Health

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/health | jq .
```

### Test Simple Task

```bash
RESPONSE=$(curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -F 'script=print(1+1)' \
  http://localhost:8080/api/tasks)

TASK_ID=$(echo $RESPONSE | jq -r '.task_id')
echo "Task ID: $TASK_ID"

# Wait for execution
sleep 2

# Get result
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/tasks/$TASK_ID | jq '.output'
```

### Test Statistics

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/stats | jq '.total_tasks'
```

## Advanced Examples

See [EXAMPLE_SCRIPTS.md](EXAMPLE_SCRIPTS.md) for:
- File operations (read, write, JSON, CSV)
- Data processing (statistics, transformation)
- System information
- Error handling and validation

## Integration Examples

### Python Client

```python
import requests
import json

class TaskClient:
    def __init__(self, token):
        self.headers = {"Authorization": f"Bearer {token}"}
        self.base_url = "http://localhost:8080/api"
    
    def submit(self, script):
        params = json.dumps({})  # optional task params
        resp = requests.post(
            f"{self.base_url}/tasks",
            headers=self.headers,
            files={
                "params": (None, params, "application/json"),
                "script": (None, script, "text/plain")
            }
        )
        return resp.json()
    
    def status(self, task_id):
        resp = requests.get(
            f"{self.base_url}/tasks/{task_id}",
            headers=self.headers
        )
        return resp.json()

# Usage
client = TaskClient("YOUR_TOKEN")
result = client.submit("print('test')")
print(result)
```

### cURL Wrapper Script

```bash
#!/bin/bash
# ptask - Python Task Runner CLI

TOKEN="${PTASK_TOKEN:-}"
API="${PTASK_API:-http://localhost:8080/api}"

case "$1" in
  submit)
    curl -X POST \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"script_content\": \"$2\"}" \
      $API/tasks
    ;;
  status)
    curl -H "Authorization: Bearer $TOKEN" \
      $API/tasks/$2
    ;;
  list)
    curl -H "Authorization: Bearer $TOKEN" \
      $API/tasks
    ;;
  stats)
    curl -H "Authorization: Bearer $TOKEN" \
      $API/stats
    ;;
esac
```

## Running Examples

1. **Ensure app is running**
   ```bash
   ./gradlew installDebug
   ```

2. **Set environment variables**
   ```bash
   export TOKEN="YOUR_API_TOKEN"
   export API="http://localhost:8080/api"
   ```

3. **Run examples**
   ```bash
   # Simple test
   curl -H "Authorization: Bearer $TOKEN" \
     $API/health
   
   # Submit task
   curl -X POST -H "Authorization: Bearer $TOKEN" \
     -d '{"script_content": "print(\"test\")"}' \
     $API/tasks
   ```

## Troubleshooting Examples

**Connection Refused**
- Ensure Python Task Runner app is running
- Check port 8080 is accessible
- For emulator: use 10.0.2.2 instead of localhost

**Unauthorized**
- Verify TOKEN is correct
- Check Bearer token format: `Bearer <token>`
- Ensure token is not expired/changed

**Task Failed**
- Check script for syntax errors
- Review error message in task status
- Check sandbox directory for file issues

**Timeout**
- Increase timeout_seconds parameter
- Optimize Python script performance
- Check for infinite loops

---

For more details, see:
- [API_EXAMPLES.md](../docs/API_EXAMPLES.md)
- [README.md](../docs/README.md)
- [QUICKSTART.md](../docs/QUICKSTART.md)
