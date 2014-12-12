#!/usr/bin/env python

import requests
import json

# Simple python script to repeatedly upload the specified file as multipart form post

NUM_TRIALS=100

url = 'http://localhost:8080/api/shard'

files = [
    {'file': open('testdata/requirements.txt', 'rb').read()}, # 16 B
    {'file': open('testdata/perf.txt', 'rb').read()}, # 2 KB
    {'file': open('testdata/Chord.pdf', 'rb').read()}, # 195 KB
    {'file': open('testdata/gondola.jpg', 'rb').read()}, # 1.2 MB
    {'file': open('testdata/lake.jpg', 'rb').read()} # 12.2 MB
]

for index, current_file in enumerate(files):
    file_id = None
    print 'Starting test on file ' + str(index)

    # Test POST requests
    for iteration in range(NUM_TRIALS):
        print 'POST',iteration,'/',NUM_TRIALS
        response = requests.post(url, files=current_file)
        responseJson = json.loads(response.text)
        if 'id' in responseJson:
            file_id = responseJson['id']

    # Test GET requests
    for iteration in range(NUM_TRIALS):
        print 'GET',iteration,'/',NUM_TRIALS
        result = requests.get(url+'/'+file_id)

    # Extract latency numbers from DropWizard
    latencies = requests.get('http://localhost:8080/admin/metrics')
    timers = json.loads(latencies.text)['timers']
    print 'Insert p99 latency for file ' + str(index) + ': ' + str(timers['edu.stanford.cs244b.Shard.insertItem']['p99'])
    print 'Get p99 latency for file ' + str(index) + ': ' + str(timers['edu.stanford.cs244b.Shard.getItem']['p99'])
