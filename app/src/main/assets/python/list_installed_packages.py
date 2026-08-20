import importlib.metadata
import json

def get_installed_packages():
    # Extracts the names of all explicitly installed distributions
    dists = importlib.metadata.distributions()
    return json.dumps([ { 'name': d.metadata['Name'], 'version': d.metadata['Version'] } for d in dists], indent=2)
