
import os
from time import sleep

def main(args):
    print("Sample Log Output")
    
    for i in range(5):
        print(f"Log message {i+1}: This is a sample log output.")
        sleep(5)
        
    print("More delayed log messages...")
    for i in range(5, 50):
        print(f"Log message {i+1}: This is another sample log output.")
        sleep(5)
        
    print("Sample log output completed.")