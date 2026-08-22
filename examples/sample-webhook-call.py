
import os
from time import sleep
import requests

WEBHOOK_URL = "https://webhook.site/c34f0a9f-6da7-4b39-b3f9-506607480bb3"

def send_webhook(payload):
    response = requests.post(WEBHOOK_URL, json=payload)
    return response.status_code


def main(args):
    payload = {
        "event": "pyhive-request",
        "os-name": os.name,
        "received-args": args,
        "count": 1
    }

    print(f"Sending webhook with payload")
    
    for i in range(100):
        payload["count"] = i + 1
        print(f"Sending webhook with payload: {payload} / count: {i + 1}")
        send_webhook(payload)
        sleep(30)
        
    print(f"Webhook sent successfully")
