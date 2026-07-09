# EVBuddy Charger 2.0 with Face Recognition

A smart EV charging station interface with integrated facial recognition for personalized user experiences.

## Features

- **Face Recognition**: Automatically detects registered users and provides personalized welcome messages
- **Ad Display**: Shows advertisements while scanning for faces
- **User Registration**: New users can register their faces and vehicle information
- **Charging Configuration**: Set charge levels and view charging progress


## Architecture

The system consists of two main components:

1. **Flask Face Recognition API** (`facial_recognition/face_api.py`)
   - Handles face detection and recognition
   - Manages registered user database
   - Provides REST API endpoints for the JavaFX UI

2. **JavaFX User Interface** (`complete_ui/`)
   - Modern, touch-friendly UI
   - Real-time face recognition integration
   - Charging configuration and payment screens

## Quick Start

### Prerequisites

- Python 3.7+
- Java 11+ with JavaFX SDK
- Webcam for face recognition

### Installation (Full Setup)

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd EVBuddy-Charger-2.0
   ```

2. **Create and activate Python virtual environment** (recommended):
   ```bash
   # From repo root
   python3 -m venv plate_ocr_venv
   source plate_ocr_venv/bin/activate
   python --version  # ensure Python 3.9+ works best with face_recognition
   ```

3. **Install API dependencies**:
   ```bash
   # Install dependencies for the Flask + CV API
   pip install -r facial_recognition/api_requirements.txt
   ```

4. **Set up JavaFX SDK**:
   - Download JavaFX SDK from [OpenJFX](https://openjfx.io/)
   - Extract to `~/Downloads/javafx-sdk-24.0.1/`
   - Update the path in `complete_ui/build_and_run.sh` if needed

5. **Run the application (one command)**:
   ```bash
   ./start_evbuddy.sh
   ```

This script will:
- Install Python dependencies for the face recognition API
- Start the Flask API server
- Launch the JavaFX UI
- Handle cleanup when you press Ctrl+C

### Alternative: Run components manually

Terminal A (API):
```bash
cd facial_recognition
source ../plate_ocr_venv/bin/activate
pip install -r api_requirements.txt
python face_api.py
```

Terminal B (UI):
```bash
cd complete_ui
chmod +x build_and_run.sh
./build_and_run.sh
```

### Optional camera selection
- Probe cameras:
```bash
curl -s http://localhost:5001/camera-scan | jq
```
- Select preferred camera for next start:
```bash
curl -X POST http://localhost:5001/select-camera \
  -H 'Content-Type: application/json' \
  -d '{"index":0,"backend":"avfoundation"}'
```

## How It Works

### Face Recognition Flow

1. **Ad Screen**: The app starts showing advertisements while scanning for faces
2. **Registered User Detected**: 
   - Shows "Welcome [Name]!" screen
   - Proceeds to charging configuration
3. **Unregistered User Detected**:
   - Shows "Tap to Get Started" screen
   - Guides through registration process

### API Endpoints

- `POST /start-scanning`: Start face scanning
- `POST /stop-scanning`: Stop face scanning  
- `GET /get-current-user`: Get current detected user
- `POST /register-face`: Register a new face
- `GET /camera-scan`: List available cameras and whether they can read frames
- `POST /select-camera`: Set preferred camera (index/backend) for plate scanning
- `GET /health`: Health check

### User Registration Process

1. **License Plate Registration**: Enter vehicle information
2. **Face Registration**: Capture and register user's face
3. **Account Creation**: Complete account setup

## File Structure

```
EVBuddy-Charger-2.0/
├── facial_recognition/
│   ├── face_api.py              # Flask API for face recognition
│   ├── register_face.py         # Original face registration script
│   ├── accounts.json            # User account database
│   ├── registered_faces/        # Stored face images
│   └── api_requirements.txt     # Python dependencies
├── complete_ui/
│   ├── src/main/java/           # JavaFX application code
│   ├── src/main/resources/      # UI resources and assets
│   └── build_and_run.sh         # Build and run script
├── start_evbuddy.sh             # Main startup script
└── README.md                    # This file
```

## Development

### Adding New Features

1. **Face Recognition**: Modify `facial_recognition/face_api.py`
2. **UI Changes**: Edit JavaFX files in `complete_ui/src/main/java/`
3. **Styling**: Update CSS in `complete_ui/src/main/resources/`

### Testing

- **API Testing**: Use curl or Postman to test Flask endpoints
- **UI Testing**: Run the JavaFX application directly
- **Integration Testing**: Use the startup script

## Troubleshooting

### Common Issues

1. **Camera not found**: Ensure webcam is connected and accessible
2. **JavaFX not found**: Update JavaFX SDK path in build script
3. **API connection failed**: Check if Flask API is running on port 5001
4. **Face recognition not working**: Verify Python dependencies are installed
5. **Black camera feed**: Ensure macOS granted Camera permission to Python, and no other app is using the camera.

### Debug Mode

- **API Debug**: Flask runs in debug mode by default
- **UI Debug**: Check console output for JavaFX errors
- **Face Recognition**: Monitor API logs for detection issues

## Security Considerations

- Face data is stored locally
- Payment information should be handled securely in production
- API endpoints should be secured in production deployment

## License

[Add your license information here]
