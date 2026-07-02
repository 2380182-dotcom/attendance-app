#!/bin/bash

# Port to tunnel (default: 8080)
PORT=8080

echo "========================================================="
echo "   Attendance System - Local Backend & Public Tunnel   "
echo "========================================================="
echo ""
echo "This script starts the local Spring Boot backend application"
echo "and sets up a public HTTPS tunnel using localtunnel."
echo "This allows the mobile app (APK) to connect to your backend"
echo "from ANY internet connection (Wi-Fi, cellular, hotspots)!"
echo ""

# Terminate any background processes spawned by this script on exit
cleanup() {
    echo ""
    echo "Shutting down backend and tunnel..."
    if [ ! -z "$BACKEND_PID" ]; then
        kill $BACKEND_PID 2>/dev/null
    fi
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# 1. Start Spring Boot Backend
if [ -f "./mvnw" ]; then
    echo "1. Starting Spring Boot Backend in background (port $PORT)..."
    ./mvnw spring-boot:run > backend.log 2>&1 &
    BACKEND_PID=$!
    echo "   Backend started (PID: $BACKEND_PID). Logs: backend.log"
else
    echo "Error: ./mvnw not found! Please run this script from the project root."
    exit 1
fi

echo "   Waiting 12 seconds for backend server to warm up..."
sleep 12

# Check if backend is alive
if ! ps -p $BACKEND_PID > /dev/null; then
    echo "Error: Backend failed to start! Check backend.log for details."
    exit 1
fi

echo "   Backend started successfully!"
echo ""

# 2. Start Localtunnel
echo "2. Launching Localtunnel public proxy on port $PORT..."
echo "---------------------------------------------------------"
echo "Copy the 'your url is' address shown below (starts with https://)"
echo "and enter it into the Server Settings in your mobile app."
echo "---------------------------------------------------------"
echo ""

npx localtunnel --port $PORT
