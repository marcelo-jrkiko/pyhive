# Example Python Scripts for Task Runner

This directory contains example Python scripts that can be submitted to the Python Task Runner API.

## Table of Contents

1. [Simple Examples](#simple-examples)
2. [File Operations](#file-operations)
3. [Data Processing](#data-processing)
4. [System Information](#system-information)
5. [Error Handling](#error-handling)

## Simple Examples

### hello_world.py

The simplest possible task:

```python
print("Hello from Python Task Runner!")
```

**Submit with:**
```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"script_content": "print(\"Hello from Python Task Runner!\")"}' \
  http://localhost:8080/api/tasks
```

### math_operations.py

Basic math operations:

```python
# Basic arithmetic
a = 10
b = 3

print(f"Sum: {a + b}")
print(f"Difference: {a - b}")
print(f"Product: {a * b}")
print(f"Division: {a / b}")
print(f"Power: {a ** b}")
print(f"Modulo: {a % b}")

# List operations
numbers = [1, 2, 3, 4, 5]
print(f"Sum of {numbers}: {sum(numbers)}")
print(f"Average: {sum(numbers) / len(numbers)}")
print(f"Max: {max(numbers)}, Min: {min(numbers)}")
```

### string_processing.py

String manipulation:

```python
text = "Python Task Runner"

# Basic operations
print(f"Original: {text}")
print(f"Uppercase: {text.upper()}")
print(f"Lowercase: {text.lower()}")
print(f"Reversed: {text[::-1]}")
print(f"Length: {len(text)}")

# Word operations
words = text.split()
print(f"Words: {words}")
print(f"Joined: {' | '.join(words)}")

# Formatting
name = "World"
print(f"Greeting: Hello, {name}!")
```

## File Operations

### read_write_file.py

Reading and writing files:

```python
import os

# Write file
filename = "output.txt"
with open(filename, "w") as f:
    f.write("Line 1: This is the first line\n")
    f.write("Line 2: This is the second line\n")
    f.write("Line 3: This is the third line\n")

print(f"File '{filename}' created")

# Read file
with open(filename, "r") as f:
    content = f.read()

print("File content:")
print(content)

# File info
file_size = os.path.getsize(filename)
print(f"File size: {file_size} bytes")
```

### json_operations.py

JSON file handling:

```python
import json

# Create data
data = {
    "name": "Python Task Runner",
    "version": "1.0.0",
    "features": [
        "REST API",
        "Python Execution",
        "Task Scheduling",
        "File Sandboxing"
    ],
    "stats": {
        "total_tasks": 100,
        "successful": 95,
        "failed": 5
    }
}

# Write JSON file
with open("config.json", "w") as f:
    json.dump(data, f, indent=2)

print("JSON file created: config.json")

# Read JSON file
with open("config.json", "r") as f:
    loaded_data = json.load(f)

print(f"Project: {loaded_data['name']}")
print(f"Version: {loaded_data['version']}")
print(f"Features: {', '.join(loaded_data['features'])}")
print(f"Success rate: {loaded_data['stats']['successful']}/{loaded_data['stats']['total_tasks']}")
```

### csv_operations.py

CSV file handling:

```python
import csv

# Write CSV
with open("data.csv", "w", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["Name", "Age", "City"])
    writer.writerow(["Alice", 30, "New York"])
    writer.writerow(["Bob", 25, "Los Angeles"])
    writer.writerow(["Charlie", 35, "Chicago"])

print("CSV file created: data.csv")

# Read CSV
print("\\nReading CSV:")
with open("data.csv", "r") as f:
    reader = csv.DictReader(f)
    for row in reader:
        print(f"{row['Name']} is {row['Age']} years old and lives in {row['City']}")
```

### directory_operations.py

Directory management:

```python
import os

# Create directories
os.makedirs("data/subfolder", exist_ok=True)
print("Directories created")

# List files
for root, dirs, files in os.walk("."):
    level = root.replace(".", "").count(os.sep)
    indent = " " * 2 * level
    print(f"{indent}{os.path.basename(root)}/")
    subindent = " " * 2 * (level + 1)
    for file in files:
        print(f"{subindent}{file}")

# Get directory info
if os.path.exists("data"):
    print("\\nDirectory info:")
    print(f"Path exists: True")
    print(f"Is directory: {os.path.isdir('data')}")
```

## Data Processing

### statistics.py

Statistical calculations:

```python
import statistics
import json

# Sample data
data = [23, 45, 56, 78, 34, 98, 12, 88, 65, 32, 56, 78]

# Calculate statistics
results = {
    "data": data,
    "count": len(data),
    "sum": sum(data),
    "mean": statistics.mean(data),
    "median": statistics.median(data),
    "mode": statistics.mode(data),
    "stdev": statistics.stdev(data),
    "variance": statistics.variance(data),
    "min": min(data),
    "max": max(data),
    "range": max(data) - min(data)
}

# Display results
print(json.dumps(results, indent=2))

# Save results
with open("statistics.json", "w") as f:
    json.dump(results, f, indent=2)
```

### data_transformation.py

Data transformation:

```python
import json

# Input data
raw_data = [
    {"id": 1, "name": "Alice", "score": 85},
    {"id": 2, "name": "Bob", "score": 92},
    {"id": 3, "name": "Charlie", "score": 78},
    {"id": 4, "name": "Diana", "score": 95},
    {"id": 5, "name": "Eve", "score": 88}
]

# Transform data
transformed = {
    "total_records": len(raw_data),
    "average_score": sum(item["score"] for item in raw_data) / len(raw_data),
    "high_performers": [item for item in raw_data if item["score"] >= 90],
    "sorted_by_score": sorted(raw_data, key=lambda x: x["score"], reverse=True),
    "by_name": {item["name"]: item["score"] for item in raw_data}
}

# Display results
print(json.dumps(transformed, indent=2))

# Save results
with open("transformed_data.json", "w") as f:
    json.dump(transformed, f, indent=2)
```

## System Information

### system_info.py

System and environment information:

```python
import sys
import os
import platform
from datetime import datetime

info = {
    "timestamp": datetime.now().isoformat(),
    "python": {
        "version": platform.python_version(),
        "implementation": platform.python_implementation(),
        "compiler": platform.python_compiler()
    },
    "system": {
        "platform": sys.platform,
        "system": platform.system(),
        "release": platform.release(),
        "machine": platform.machine(),
        "processor": platform.processor()
    },
    "environment": {
        "cwd": os.getcwd(),
        "path_separator": os.path.sep,
        "line_separator": repr(os.linesep)
    }
}

import json
print(json.dumps(info, indent=2))
```

### process_info.py

Process and execution information:

```python
import os
import sys
import time

print("=== Process Information ===")
print(f"Process ID (PID): {os.getpid()}")
print(f"Parent Process ID (PPID): {os.getppid()}")

print("\\n=== Python Information ===")
print(f"Python version: {sys.version}")
print(f"Python executable: {sys.executable}")
print(f"Python path: {sys.prefix}")

print("\\n=== Execution Info ===")
print(f"Start time: {time.ctime()}")
print(f"Current directory: {os.getcwd()}")
print(f"USER: {os.environ.get('USER', 'unknown')}")
print(f"HOME: {os.environ.get('HOME', 'unknown')}")

print("\\n=== Arguments ===")
print(f"sys.argv: {sys.argv}")
```

## Error Handling

### exception_handling.py

Proper exception handling:

```python
import sys
import traceback

def divide_numbers(a, b):
    """Safely divide two numbers."""
    if b == 0:
        raise ValueError("Divisor cannot be zero")
    return a / b

def process_file(filename):
    """Process a file with error handling."""
    try:
        with open(filename, "r") as f:
            data = f.read()
        return len(data)
    except FileNotFoundError:
        raise Exception(f"File not found: {filename}")

# Test exception handling
try:
    print("Test 1: Division by zero")
    result = divide_numbers(10, 0)
except ValueError as e:
    print(f"Error caught: {e}")

try:
    print("\\nTest 2: File not found")
    size = process_file("nonexistent.txt")
except Exception as e:
    print(f"Error caught: {e}")

print("\\nTest 3: Successful execution")
try:
    result = divide_numbers(10, 2)
    print(f"10 / 2 = {result}")
except Exception as e:
    print(f"Unexpected error: {e}")
    traceback.print_exc()
    sys.exit(1)

print("\\nAll tests completed")
```

### validation.py

Input validation:

```python
def validate_email(email):
    """Validate email format."""
    if "@" not in email or "." not in email.split("@")[-1]:
        raise ValueError(f"Invalid email: {email}")
    return True

def validate_age(age):
    """Validate age value."""
    try:
        age_int = int(age)
        if age_int < 0 or age_int > 150:
            raise ValueError("Age must be between 0 and 150")
        return True
    except ValueError as e:
        raise ValueError(f"Invalid age: {e}")

def validate_phone(phone):
    """Validate phone number format."""
    digits = ''.join(c for c in phone if c.isdigit())
    if len(digits) != 10:
        raise ValueError("Phone number must contain 10 digits")
    return True

# Test validation
test_cases = [
    ("email", "user@example.com"),
    ("email", "invalid_email"),
    ("age", 25),
    ("age", 200),
    ("phone", "(123) 456-7890")
]

for test_type, value in test_cases:
    try:
        if test_type == "email":
            validate_email(value)
        elif test_type == "age":
            validate_age(value)
        elif test_type == "phone":
            validate_phone(value)
        print(f"✓ Valid {test_type}: {value}")
    except ValueError as e:
        print(f"✗ Invalid {test_type}: {e}")
```

---

## Running These Examples

All scripts can be submitted using the API:

```bash
SCRIPT="print('Hello')"  # Replace with actual script

curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"script_content\": \"$SCRIPT\"}" \
  http://localhost:8080/api/tasks
```

Or with the Python client from [API_EXAMPLES.md](API_EXAMPLES.md).
