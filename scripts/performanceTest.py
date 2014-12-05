#!/usr/bin/env python

import requests
import json

# Simple python script to repeatedly upload the specified file as multipart form post

NUM_TRIALS=1000

url = 'http://localhost:8080/shard'
#files = {'file': open('testdata/configuration.yml', 'rb')}
#files = {'file': open('testdata/icon.png', 'rb')}
files = {'file': open('testdata/paper.pdf', 'rb')}
#files = {'file': open('testdata/photo.jpg', 'rb')}
#files = {'file': open('testdata/executable.jar', 'rb')}

file_id = None

# Determine latency of POSTs
print 'Running '+str(NUM_TRIALS)+' POST requests'
for iteration in range(NUM_TRIALS):
    response = requests.post(url, files=files)
    responseJson = json.loads(response.text)
    file_id = responseJson['id']

print 'Running '+str(NUM_TRIALS)+' GET requests'
# Determine latency of GETs
for iteration in range(NUM_TRIALS):
    result = requests.get(url+'/'+file_id)

# Extract latency numbers from DropWizard
latencies = requests.get('http://localhost:8080/admin/metrics')
timers = json.loads(latencies.text)['timers']
print '"edu.stanford.cs244b.Shard.insertItem": '+str(timers['edu.stanford.cs244b.Shard.insertItem'])
print '"edu.stanford.cs244b.Shard.getItem": '+str(timers['edu.stanford.cs244b.Shard.getItem'])

