#!/bin/bash
set +e

echo "============================================"
echo "  Open http://localhost:8080"
echo "============================================"

cd /app
clojure -M:server 2>&1
echo "Server exited with code $?"
sleep infinity
