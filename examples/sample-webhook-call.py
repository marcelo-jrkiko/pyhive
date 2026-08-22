
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
        "received-args": args
    }

    print(f"Sending webhook with payload")
    send_webhook(payload)
    sleep(10)
    print(f"Webhook sent successfully")
