#!/bin/bash

echo "Starting EVBuddy Charger 2.0 with Face Recognition..."

# Check if Python and pip are available
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is not installed"
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "facial_recognition/face_api.py" ]; then
    echo "Error: Please run this script from the EVBuddy-Charger-2.0 directory"
    exit 1
fi

# Use existing virtual environment with face_recognition
echo "Setting up face recognition API..."
cd facial_recognition

# Activate existing virtual environment that has face_recognition
# Note: venv is at repo root: ../plate_ocr_venv
if [ -f "../plate_ocr_venv/bin/activate" ]; then
    source ../plate_ocr_venv/bin/activate
else
    echo "Warning: ../plate_ocr_venv not found. Using system Python environment."
fi

# Install Flask dependencies if needed
echo "Installing Flask API dependencies..."
pip install flask flask-cors pillow numpy requests

# Start Flask API in background
echo "Starting face recognition API..."
python face_api.py &
API_PID=$!

# Wait a moment for API to start
sleep 3

# Go back to root directory
cd ..

# Start JavaFX application
echo "Starting JavaFX UI..."
cd complete_ui
chmod +x build_and_run.sh
./build_and_run.sh &
UI_PID=$!

echo "EVBuddy is running!"
echo "Face Recognition API PID: $API_PID"
echo "JavaFX UI PID: $UI_PID"
echo ""
echo "Press Ctrl+C to stop both applications"

# Function to cleanup on exit
cleanup() {
    echo ""
    echo "Stopping EVBuddy applications..."
    kill $API_PID 2>/dev/null || true
    kill $UI_PID 2>/dev/null || true
    exit 0
}

# Set up signal handler
trap cleanup SIGINT SIGTERM

# Wait for UI to exit, then cleanup API automatically
wait $UI_PID
cleanup