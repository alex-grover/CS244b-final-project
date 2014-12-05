#!/usr/bin/env python

import requests

# Simple python script to repeatedly upload the specified file as multipart form post

NUM_TRIALS=1000

url = 'http://localhost:8080/shard'
files = {'file': open('testdata/configuration.yml', 'rb')}
#files = {'file': open('testdata/icon.png', 'rb')}
#files = {'file': open('testdata/paper.pdf', 'rb')}
#files = {'file': open('testdata/photo.jpg', 'rb')}
#files = {'file': open('testdata/executable.jar', 'rb')}
for iteration in range(NUM_TRIALS):
    r = requests.post(url, files=files)

