# API Examples and Usage Guide

This document provides practical examples for using the Python Task Runner API.

## Table of Contents

1. [Basic Setup](#basic-setup)
2. [cURL Examples](#curl-examples)
3. [Python Client Examples](#python-client-examples)
4. [JavaScript/Node.js Examples](#javascriptnodejs-examples)
5. [Common Use Cases](#common-use-cases)
6. [Error Handling](#error-handling)

## Basic Setup

### Prerequisites

- Python Task Runner app running on device/emulator
- API token from app UI
- Network connectivity

### Environment Variables

```bash
export API_URL="http://localhost:8080/api"
export API_TOKEN="YOUR_TOKEN_HERE"
export DEVICE_IP="192.168.1.100"  # For remote access
```

## cURL Examples

### 1. Health Check

```bash
curl -X GET \
  -H "Authorization: Bearer $API_TOKEN" \
  "$API_URL/health"
```

**Response:**
```json
{
  "status": "healthy",
  "timestamp": 1692345600000,
    "python_initialized": true
}
```

### 2. Submit a Simple Task

```bash
curl -X POST \
  -H "Authorization: Bearer $API_TOKEN" \
    -F 'params={"script_name":"hello.py","timeout_seconds":30,"args":{"name":"PyHive"}}' \
  -F 'script=print("Hello, Python Task Runner!")' \
  "$API_URL/tasks"
```

**Response:**
```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Task submitted successfully",
  "created_at": 1692345600000
}
```

### 3. Submit Task with Scheduled Time

```bash
# Schedule task for 2 minutes from now
SCHEDULED=$(date -d "+2 minutes" +%s)000

curl -X POST \
  -H "Authorization: Bearer $API_TOKEN" \
  -F "params={\"script_name\":\"scheduled.py\",\"scheduled_time\":$SCHEDULED}" \
  -F "script=import time; print('Scheduled task at', time.ctime())" \
  "$API_URL/tasks"
```

### 4. Submit a Multipart Script with Raw Params

```bash
curl -X POST \
  -H "Authorization: Bearer $API_TOKEN" \
    -F 'params={"script_name":"data_processing.py","timeout_seconds":120,"tags":["data_processing"],"args":{"batch":1,"dry_run":true}}' \
  -F 'script=import json; print(json.dumps({"items": [1,2,3]}))' \
  "$API_URL/tasks"
```

### 5. Get Task Status

```bash
TASK_ID="550e8400-e29b-41d4-a716-446655440000"

curl -X GET \
  -H "Authorization: Bearer $API_TOKEN" \
  "$API_URL/tasks/$TASK_ID"
```

**Response (Completed):**
```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "execution_time": 1234,
  "result": "Hello, Python Task Runner!",
  "output": "Hello, Python Task Runner!",
  "error": null,
  "progress": 100
}
```

### 6. List All Tasks

```bash
curl -X GET \
  -H "Authorization: Bearer $API_TOKEN" \
  "$API_URL/tasks"
```

### 7. List Completed Tasks

```bash
curl -X GET \
  -H "Authorization: Bearer $API_TOKEN" \
  "$API_URL/tasks?status=COMPLETED"
```

### 8. List Tasks by Tag

```bash
curl -X GET \
  -H "Authorization: Bearer $API_TOKEN" \
  "$API_URL/tasks?tag=data_processing"
```

### 9. Get Server Statistics

```bash
curl -X GET \
  -H "Authorization: Bearer $API_TOKEN" \
  "$API_URL/stats"
```

**Response:**
```json
{
  "total_tasks": 42,
  "pending_tasks": 2,
  "running_tasks": 1,
  "completed_tasks": 35,
  "failed_tasks": 3,
  "cancelled_tasks": 1,
  "scheduled_tasks": 0,
  "average_execution_time_ms": 2847
}
```

### 10. Cancel a Task

```bash
TASK_ID="550e8400-e29b-41d4-a716-446655440000"

curl -X PUT \
  -H "Authorization: Bearer $API_TOKEN" \
  "$API_URL/tasks/$TASK_ID/cancel"
```

### 11. Reschedule a Task

```bash
TASK_ID="550e8400-e29b-41d4-a716-446655440000"
NEW_TIME=$(date -d "+1 hour" +%s)000

curl -X PUT \
  -H "Authorization: Bearer $API_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"new_scheduled_time\": $NEW_TIME
  }" \
  "$API_URL/tasks/$TASK_ID/reschedule"
```

### 12. Delete a Task

```bash
TASK_ID="550e8400-e29b-41d4-a716-446655440000"

curl -X DELETE \
  -H "Authorization: Bearer $API_TOKEN" \
  "$API_URL/tasks/$TASK_ID"
```

## Python Client Examples

### Simple Python Client

```python
import requests
import json
from typing import Optional, Dict, List

class PythonTaskRunnerClient:
    def __init__(self, api_url: str, token: str):
        self.api_url = api_url
        self.token = token
        self.auth_headers = {
            "Authorization": f"Bearer {token}"
        }
    
    def submit_task(self, script_content: str, **kwargs) -> Dict:
        """Submit a new Python task using multipart/form-data."""
        params = json.dumps(kwargs or {})
        files = {
            "params": (None, params, "application/json"),
            "script": (None, script_content, "text/plain")
        }
        response = requests.post(
            f"{self.api_url}/tasks",
            headers=self.auth_headers,
            files=files
        )
        return response.json()
    
    def get_task_status(self, task_id: str) -> Dict:
        """Get status of a task."""
        response = requests.get(
            f"{self.api_url}/tasks/{task_id}",
            headers=self.auth_headers
        )
        return response.json()
    
    def list_tasks(self, status: Optional[str] = None, 
                   tag: Optional[str] = None, limit: int = 100) -> Dict:
        """List tasks with optional filtering."""
        params = {"limit": limit}
        if status:
            params["status"] = status
        if tag:
            params["tag"] = tag
        
        response = requests.get(
            f"{self.api_url}/tasks",
            headers=self.auth_headers,
            params=params
        )
        return response.json()
    
    def cancel_task(self, task_id: str) -> Dict:
        """Cancel a task."""
        response = requests.put(
            f"{self.api_url}/tasks/{task_id}/cancel",
            headers=self.auth_headers
        )
        return response.json()
    
    def reschedule_task(self, task_id: str, new_time: int) -> Dict:
        """Reschedule a task."""
        payload = {"new_scheduled_time": new_time}
        response = requests.put(
            f"{self.api_url}/tasks/{task_id}/reschedule",
            headers=self.auth_headers,
            json=payload
        )
        return response.json()
    
    def delete_task(self, task_id: str) -> Dict:
        """Delete a task."""
        response = requests.delete(
            f"{self.api_url}/tasks/{task_id}",
            headers=self.auth_headers
        )
        return response.json()
    
    def get_stats(self) -> Dict:
        """Get server statistics."""
        response = requests.get(
            f"{self.api_url}/stats",
            headers=self.auth_headers
        )
        return response.json()
    
    def health_check(self) -> Dict:
        """Check server health."""
        response = requests.get(
            f"{self.api_url}/health",
            headers=self.auth_headers
        )
        return response.json()

# Usage Example
if __name__ == "__main__":
    client = PythonTaskRunnerClient(
        api_url="http://localhost:8080/api",
        token="YOUR_TOKEN_HERE"
    )
    
    # Submit a task
    result = client.submit_task(
        script_content='print("Hello from Python!")',
        script_name="test.py",
        timeout_seconds=60
    )
    print(f"Task submitted: {result['task_id']}")
    
    # Get status
    import time
    time.sleep(2)
    status = client.get_task_status(result['task_id'])
    print(f"Task status: {status['status']}")
    print(f"Output: {status.get('output', '')}")
    
    # Get statistics
    stats = client.get_stats()
    print(f"Total tasks: {stats['total_tasks']}")
```

### Advanced Python Client with Retry Logic

```python
import requests
import time
from typing import Optional

class RobustPythonTaskRunnerClient:
    def __init__(self, api_url: str, token: str, max_retries: int = 3):
        self.api_url = api_url
        self.token = token
        self.max_retries = max_retries
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": f"Bearer {token}"
        })
    
    def _make_request(self, method: str, endpoint: str, **kwargs):
        """Make HTTP request with retry logic."""
        url = f"{self.api_url}{endpoint}"
        
        for attempt in range(self.max_retries):
            try:
                response = self.session.request(method, url, **kwargs)
                response.raise_for_status()
                return response.json()
            except requests.exceptions.RequestException as e:
                if attempt == self.max_retries - 1:
                    raise
                wait_time = 2 ** attempt  # Exponential backoff
                print(f"Request failed, retrying in {wait_time}s...")
                time.sleep(wait_time)
    
    def submit_and_wait(self, script_content: str, timeout: int = 300) -> Dict:
        """Submit task and wait for completion."""
        # Submit task via multipart form
        files = {
            "params": (None, json.dumps({"timeout_seconds": timeout}), "application/json"),
            "script": (None, script_content, "text/plain")
        }
        result = self._make_request("POST", "/tasks", files=files)
        task_id = result["task_id"]
        print(f"Task {task_id} submitted")
        
        # Poll for completion
        start_time = time.time()
        poll_interval = 1
        
        while time.time() - start_time < timeout:
            status = self._make_request("GET", f"/tasks/{task_id}")
            
            if status["status"] in ["COMPLETED", "FAILED"]:
                return status
            
            print(f"Status: {status['status']}...")
            time.sleep(poll_interval)
        
        raise TimeoutError(f"Task {task_id} did not complete within timeout")

# Usage Example
if __name__ == "__main__":
    client = RobustPythonTaskRunnerClient(
        api_url="http://localhost:8080/api",
        token="YOUR_TOKEN_HERE"
    )
    
    script = """
import time
print("Processing...")
time.sleep(2)
print("Done!")
"""
    
    result = client.submit_and_wait(script)
    print(f"Final status: {result['status']}")
    print(f"Output:\\n{result.get('output', '')}")
```

## JavaScript/Node.js Examples

### Node.js Client

```javascript
const axios = require('axios');
const FormData = require('form-data');

class PythonTaskRunnerClient {
    constructor(apiUrl, token) {
        this.apiUrl = apiUrl;
        this.token = token;
        this.client = axios.create({
            baseURL: apiUrl,
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
    }

    async submitTask(scriptContent, options = {}) {
        const form = new FormData();
        form.append('params', JSON.stringify(options), {
            contentType: 'application/json'
        });
        form.append('script', scriptContent, {
            contentType: 'text/plain'
        });
        const response = await this.client.post('/tasks', form, {
            headers: form.getHeaders()
        });
        return response.data;
    }

    async getTaskStatus(taskId) {
        const response = await this.client.get(`/tasks/${taskId}`);
        return response.data;
    }

    async listTasks(filters = {}) {
        const response = await this.client.get('/tasks', {
            params: filters
        });
        return response.data;
    }

    async cancelTask(taskId) {
        const response = await this.client.put(`/tasks/${taskId}/cancel`);
        return response.data;
    }

    async deleteTask(taskId) {
        const response = await this.client.delete(`/tasks/${taskId}`);
        return response.data;
    }

    async getStats() {
        const response = await this.client.get('/stats');
        return response.data;
    }

    async healthCheck() {
        const response = await this.client.get('/health');
        return response.data;
    }
}

// Usage Example
(async () => {
    const client = new PythonTaskRunnerClient(
        'http://localhost:8080/api',
        'YOUR_TOKEN_HERE'
    );

    // Submit task
    const task = await client.submitTask(
        'print("Hello from Node.js!")'
    );
    console.log('Task ID:', task.task_id);

    // Wait and check status
    await new Promise(resolve => setTimeout(resolve, 2000));
    const status = await client.getTaskStatus(task.task_id);
    console.log('Status:', status.status);
    console.log('Output:', status.output);

    // Get stats
    const stats = await client.getStats();
    console.log('Total tasks:', stats.total_tasks);
})();
```

### Async/Await Helper

```javascript
async function submitAndWait(client, scriptContent, timeout = 300000) {
    const task = await client.submitTask(scriptContent);
    const taskId = task.task_id;
    
    const startTime = Date.now();
    const pollInterval = 1000;
    
    while (Date.now() - startTime < timeout) {
        const status = await client.getTaskStatus(taskId);
        
        if (['COMPLETED', 'FAILED'].includes(status.status)) {
            return status;
        }
        
        console.log(`Status: ${status.status}...`);
        await new Promise(resolve => setTimeout(resolve, pollInterval));
    }
    
    throw new Error(`Task ${taskId} did not complete within timeout`);
}

// Usage
(async () => {
    const result = await submitAndWait(
        client,
        'print("Test completed!")'
    );
    console.log('Result:', result);
})();
```

## Common Use Cases

### 1. Data Processing Pipeline

**Script:**
```python
import json
import csv
from io import StringIO

# Simulate input data
data = {
    "records": [
        {"id": 1, "value": 100},
        {"id": 2, "value": 200},
        {"id": 3, "value": 150}
    ]
}

# Process data
processed = []
total = 0
for record in data["records"]:
    processed.append({
        "id": record["id"],
        "value": record["value"],
        "doubled": record["value"] * 2
    })
    total += record["value"]

# Save as JSON
with open("output.json", "w") as f:
    json.dump({
        "processed": processed,
        "total": total,
        "average": total / len(processed)
    }, f, indent=2)

print("Processing complete")
print(f"Total records: {len(processed)}")
```

**Client code:**
```bash
curl -X POST \
  -H "Authorization: Bearer $API_TOKEN" \
  -F "params={\"tags\":[\"data_processing\",\"batch_1\"]}" \
  -F "script=$(cat <<'EOF'
import json
data = {"records": [{"id": 1, "value": 100}]}
with open("output.json", "w") as f:
    json.dump(data, f)
print("Done")
EOF
)" \
  "$API_URL/tasks"
```

### 2. Batch Processing with Multiple Tasks

```bash
#!/bin/bash

for i in {1..5}; do
  curl -X POST \
    -H "Authorization: Bearer $API_TOKEN" \
    -F "params={\"tags\":[\"batch_processing\",\"item_$i\"]}" \
    -F "script=print('Processing batch item $i')" \
    "$API_URL/tasks"
  
  echo "Submitted task for item $i"
done
```

### 3. Scheduled Report Generation

```python
import time
from datetime import datetime

# Simulate report generation
report = {
    "timestamp": datetime.now().isoformat(),
    "type": "daily_report",
    "metrics": {
        "cpu_usage": 45,
        "memory_usage": 62,
        "disk_usage": 78
    }
}

# Save report
with open("report.txt", "w") as f:
    f.write(f"Report generated at {report['timestamp']}\n")
    for key, value in report['metrics'].items():
        f.write(f"{key}: {value}%\n")

print("Report generated successfully")
```

### 4. Error Handling and Retry

```python
import sys
import traceback

try:
    # Your computation here
    result = 10 / 0  # Will cause error
    print(f"Result: {result}")
except ZeroDivisionError as e:
    print(f"ERROR: Division by zero - {str(e)}")
    sys.exit(1)
except Exception as e:
    print(f"UNEXPECTED ERROR: {str(e)}")
    traceback.print_exc()
    sys.exit(2)

print("Task completed successfully")
```

### 5. File-based Input/Output

```python
import os

# Read input file
input_file = "input.txt"
if os.path.exists(input_file):
    with open(input_file, "r") as f:
        lines = f.readlines()
    
    # Process lines
    processed = [line.strip().upper() for line in lines]
    
    # Write output
    with open("output.txt", "w") as f:
        f.write("\n".join(processed))
    
    print(f"Processed {len(processed)} lines")
else:
    print("Input file not found")
```

## Error Handling

### Common Errors and Solutions

**401 Unauthorized**
```bash
# Check token
curl -H "Authorization: Bearer WRONG_TOKEN" \
  $API_URL/health
# Returns: 401 Unauthorized
```

**404 Not Found**
```bash
# Check task exists
curl -H "Authorization: Bearer $API_TOKEN" \
  $API_URL/tasks/nonexistent-id
# Returns: 404 Task not found
```

**409 Conflict**
```bash
# Try to cancel running task
curl -X PUT \
  -H "Authorization: Bearer $API_TOKEN" \
  $API_URL/tasks/running-task-id/cancel
# Returns: 409 Conflict - Cannot cancel task (already running)
```

### Handling Errors in Python

```python
import requests
from requests.exceptions import RequestException

def submit_task_safe(client, script_content):
    try:
        return client.submit_task(script_content)
    except requests.exceptions.ConnectionError:
        print("ERROR: Cannot connect to API server")
        return None
    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 401:
            print("ERROR: Invalid authentication token")
        elif e.response.status_code == 400:
            print("ERROR: Invalid request format")
        else:
            print(f"ERROR: HTTP {e.response.status_code}")
        return None
    except Exception as e:
        print(f"ERROR: Unexpected error - {str(e)}")
        return None
```

### Handling Errors in JavaScript

```javascript
async function submitTaskSafe(client, scriptContent) {
    try {
        return await client.submitTask(scriptContent);
    } catch (error) {
        if (error.response?.status === 401) {
            console.error('ERROR: Invalid token');
        } else if (error.response?.status === 400) {
            console.error('ERROR: Invalid request');
        } else if (error.code === 'ECONNREFUSED') {
            console.error('ERROR: Cannot connect to server');
        } else {
            console.error('ERROR:', error.message);
        }
        return null;
    }
}
```

---

For more information, see [README.md](README.md)
