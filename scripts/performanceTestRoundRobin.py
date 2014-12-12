#!/usr/bin/env python

import requests
import json

# Simple python script to repeatedly upload the specified file as multipart form post

NUM_TRIALS=100

url = 'http://localhost:8080/api/shard'
#files = {'file': open('testdata/icon.png', 'rb')}
#files = {'file': open('testdata/paper.pdf', 'rb')}
#files = {'file': open('testdata/photo.jpg', 'rb')}

files = [{'file': open('testdata/configuration.yml', 'rb').read()},
         {'file': open('testdata/Chord.pdf', 'rb').read()},
         {'file': open('requirements.txt', 'rb').read()},
         {'file': open('performanceTest.py', 'rb').read()}]
         #{'file': open('testdata/executable.jar', 'rb')}]

file_ids = dict()

# Determine latency of POSTs
print 'Running '+str(NUM_TRIALS)+' POST requests'
for iteration in range(NUM_TRIALS):
    print 'POST',iteration,'/',NUM_TRIALS
    for current_file in files:
        response = requests.post(url, files=current_file)
        responseJson = json.loads(response.text)
        file_ids[responseJson['filename']] = responseJson['id']

print 'Running '+str(NUM_TRIALS)+' GET requests'
# Determine latency of GETs
for iteration in range(NUM_TRIALS):
    print 'GET',iteration,'/',NUM_TRIALS
    import ipdb
    ipdb.set_trace()
    for file_id in file_ids.values():
        result = requests.get(url+'/'+file_id)

# Extract latency numbers from DropWizard
latencies = requests.get('http://localhost:8080/admin/metrics')
timers = json.loads(latencies.text)['timers']
print '"edu.stanford.cs244b.Shard.insertItem": '+str(timers['edu.stanford.cs244b.Shard.insertItem'])
print '"edu.stanford.cs244b.Shard.getItem": '+str(timers['edu.stanford.cs244b.Shard.getItem'])

