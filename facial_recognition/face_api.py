from flask import Flask, request, jsonify, Response
from flask_cors import CORS
import cv2
import face_recognition
import os
import sys
import json
import time
import threading
import base64
import numpy as np
from io import BytesIO
import importlib.util
from PIL import Image

app = Flask(__name__)
CORS(app)  # Enable CORS for JavaFX requests

JSON_PATH = "accounts.json"
REGISTERED_FACES_PATH = "registered_faces"

# Global variables for face recognition
known_encodings = []
known_labels = []
is_scanning = False
current_user = None
face_present = False
face_scan_thread = None

# Attempt to import plate OCR from plate_ocr module
# Ensure repository root and plate_ocr directory are in sys.path
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
PLATE_OCR_DIR = os.path.join(REPO_ROOT, 'plate_ocr')
# Prepend to sys.path so that local plate_ocr/secrets.py is chosen over stdlib 'secrets'
if PLATE_OCR_DIR not in sys.path:
    sys.path.insert(0, PLATE_OCR_DIR)
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

def _load_plate_ocr_function():
    """Load the plate OCR function from plate_ocr/openalpr_ocr.py with local secrets override.

    This avoids conflicts with Python's stdlib 'secrets' module by loading the
    project's plate_ocr/secrets.py into sys.modules['secrets'] before importing
    openalpr_ocr.
    """
    try:
        # Ensure the local secrets module is used instead of stdlib 'secrets'
        secrets_path = os.path.join(PLATE_OCR_DIR, 'secrets.py')
        if os.path.exists(secrets_path):
            spec = importlib.util.spec_from_file_location('secrets', secrets_path)
            secrets_module = importlib.util.module_from_spec(spec)
            assert spec and spec.loader
            spec.loader.exec_module(secrets_module)
            sys.modules['secrets'] = secrets_module

        ocr_path = os.path.join(PLATE_OCR_DIR, 'openalpr_ocr.py')
        spec = importlib.util.spec_from_file_location('plate_ocr_openalpr', ocr_path)
        ocr_module = importlib.util.module_from_spec(spec)
        assert spec and spec.loader
        spec.loader.exec_module(ocr_module)
        if hasattr(ocr_module, 'ocr'):
            return ocr_module.ocr
        print('Warning: openalpr_ocr.py loaded but no ocr() found')
        return None
    except Exception as e:
        print(f"Warning: Could not import plate OCR module: {e}")
        return None

plate_ocr = _load_plate_ocr_function()

# Global variables for plate scanning
plate_is_scanning = False
plate_current_text = None
plate_current_confidence = None
plate_last_timestamp = 0.0
plate_latest_frame_jpeg = None
plate_video = None
plate_scan_thread = None
plate_camera_index = None
plate_camera_backend = None  # None or cv2 backend id
preferred_camera_index = None
preferred_camera_backend = None
next_ocr_allowed_at = 0.0

def load_registered_faces(registered_dir, account_map_path):
    """Load registered face encodings and account mappings"""
    encodings = []
    labels = []
    
    if not os.path.exists(account_map_path):
        return encodings, labels
        
    with open(account_map_path, 'r') as f:
        account_map = json.load(f)

    for filename in os.listdir(registered_dir):
        if not filename.lower().endswith(('.png', '.jpg', '.jpeg')) or filename.startswith('.'):
            continue

        path = os.path.join(registered_dir, filename)
        if not os.path.exists(path):
            continue
            
        try:
            image = face_recognition.load_image_file(path)
            face_locations = face_recognition.face_locations(image)
            if not face_locations:
                print(f"No face found in {filename}, skipping.")
                continue
            encoding = face_recognition.face_encodings(image, known_face_locations=face_locations)[0]
            encodings.append(encoding)
            labels.append((filename, account_map.get(filename, {})))
        except Exception as e:
            print(f"Error loading {filename}: {e}")
            continue
            
    return encodings, labels

def start_face_scanning():
    """Start continuous face scanning in background thread"""
    global is_scanning, current_user, known_encodings, known_labels, face_present
    
    # Load registered faces
    known_encodings, known_labels = load_registered_faces(REGISTERED_FACES_PATH, JSON_PATH)
    
    is_scanning = True
    video = cv2.VideoCapture(0)
    
    if not video.isOpened():
        print("Error: Could not open camera")
        return
    
    print("Starting face scanning...")
    
    while is_scanning:
        ret, frame = video.read()
        if not ret:
            continue
            
        # Process every few frames to reduce CPU usage
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        face_locations = face_recognition.face_locations(rgb_frame)
        face_present = len(face_locations) > 0
        face_encodings = face_recognition.face_encodings(rgb_frame, face_locations)

        for (top, right, bottom, left), face_encoding in zip(face_locations, face_encodings):
            matches = face_recognition.compare_faces(known_encodings, face_encoding, tolerance=0.5)
            if True in matches:
                idx = matches.index(True)
                filename, account_info = known_labels[idx]
                current_user = {
                    "name": account_info.get("name", "Unknown"),
                    "filename": filename,
                    "accounts": account_info.get("accounts", [])
                }
                print(f"Detected registered user: {current_user['name']}")
                break
            else:
                current_user = None
                
        time.sleep(0.5)  # Reduce CPU usage
    
    video.release()
    face_present = False
    print("Face scanning stopped")

def stop_face_scanning():
    """Stop continuous face scanning"""
    global is_scanning, face_scan_thread
    is_scanning = False
    try:
        if face_scan_thread is not None and face_scan_thread.is_alive():
            face_scan_thread.join(timeout=1.0)
    except Exception:
        pass

def start_plate_scanning(preferred_index: int | None = None, preferred_backend: int | None = None):
    """Start continuous license plate scanning in background thread."""
    global plate_is_scanning, plate_video, plate_scan_thread, plate_camera_index, plate_camera_backend

    if plate_is_scanning:
        return

    if plate_ocr is None:
        raise RuntimeError("Plate OCR module not available")

    # Give the face scanner a moment to release the camera
    time.sleep(0.8)

    def open_camera_with_retries() -> tuple[cv2.VideoCapture | None, int | None, int | None]:
        # Try preferred first
        pref_pairs = []
        if preferred_index is not None:
            pref_pairs.append((preferred_index, preferred_backend))
        if preferred_camera_index is not None:
            pref_pairs.append((preferred_camera_index, preferred_camera_backend))

        # Then fallbacks
        indices = list(range(0, 10))
        backends = [None]
        try:
            _ = cv2.CAP_AVFOUNDATION
            backends.append(cv2.CAP_AVFOUNDATION)
        except Exception:
            pass
        for idx in indices:
            for backend in backends:
                pref_pairs.append((idx, backend))

        tried = set()
        for idx, backend in pref_pairs:
            key = (idx, backend)
            if key in tried:
                continue
            tried.add(key)

            cap = cv2.VideoCapture(idx) if backend is None else cv2.VideoCapture(idx, backend)
            if not cap.isOpened():
                try:
                    cap.release()
                except Exception:
                    pass
                continue

            # Validate frames
            ok = False
            for _ in range(12):
                ret, _frame = cap.read()
                if ret:
                    ok = True
                    break
                time.sleep(0.05)

            if ok:
                print(f"Plate scanner using camera index {idx} backend {backend}")
                return cap, idx, backend

            try:
                cap.release()
            except Exception:
                pass

        return None, None, None

    plate_video, plate_camera_index, plate_camera_backend = open_camera_with_retries()
    if plate_video is None or not plate_video.isOpened():
        raise RuntimeError("Could not open camera for plate scanning after retries")

    plate_is_scanning = True

    def _run():
        global plate_is_scanning, plate_current_text, plate_current_confidence, plate_last_timestamp, plate_latest_frame_jpeg, plate_video, plate_camera_index, plate_camera_backend, next_ocr_allowed_at
        last_ocr_time = 0.0
        ocr_interval_sec = 2.5

        consecutive_failures = 0
        consecutive_black = 0
        while plate_is_scanning:
            ret, frame = plate_video.read()
            if not ret:
                consecutive_failures += 1
                time.sleep(0.05)
                if consecutive_failures >= 40:
                    # Try reopen
                    try:
                        if plate_video is not None:
                            plate_video.release()
                    except Exception:
                        pass
                    cap2, idx2, be2 = open_camera_with_retries()
                    if cap2 is not None:
                        plate_video = cap2
                        plate_camera_index, plate_camera_backend = idx2, be2
                        consecutive_failures = 0
                continue
            consecutive_failures = 0

            # Detect fully black frames (e.g., unauthorized/blocked camera)
            try:
                if frame is None or frame.size == 0:
                    consecutive_black += 1
                else:
                    # Compute mean brightness on a downscaled frame
                    small = cv2.resize(frame, (32, 18))
                    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
                    mean_val = float(np.mean(gray))
                    if mean_val < 1.0:
                        consecutive_black += 1
                    else:
                        consecutive_black = 0
                if consecutive_black >= 40:
                    # Reopen camera
                    try:
                        if plate_video is not None:
                            plate_video.release()
                    except Exception:
                        pass
                    cap2, idx2, be2 = open_camera_with_retries()
                    if cap2 is not None:
                        plate_video = cap2
                        plate_camera_index, plate_camera_backend = idx2, be2
                        consecutive_black = 0
                    continue
            except Exception:
                pass

            # Always keep latest frame in JPEG for UI consumption (resize to reduce payload)
            try:
                h, w = frame.shape[:2]
                target_w = 640
                if w > target_w:
                    scale = target_w / float(w)
                    new_size = (int(w * scale), int(h * scale))
                    display_frame = cv2.resize(frame, new_size)
                else:
                    display_frame = frame
                ok, buf = cv2.imencode('.jpg', display_frame, [int(cv2.IMWRITE_JPEG_QUALITY), 80])
                if ok:
                    plate_latest_frame_jpeg = buf.tobytes()
            except Exception:
                pass

            # Run OCR at interval
            now = time.time()
            if now - last_ocr_time >= ocr_interval_sec and now >= next_ocr_allowed_at:
                last_ocr_time = now
                try:
                    result = plate_ocr(frame)
                    # Parse PlateRecognizer-like result
                    if isinstance(result, dict) and 'results' in result and len(result['results']) > 0:
                        top = result['results'][0]
                        plate_current_text = top.get('plate', None)
                        plate_current_confidence = top.get('score', None)
                        plate_last_timestamp = now
                    else:
                        # Keep last known reading; just note timestamp
                        plate_last_timestamp = now
                        try:
                            # Log minimal info to help debugging
                            print(f"Plate OCR no result: {str(result)[:200]}")
                            # Back off if service rejects with 403 or similar
                            if isinstance(result, dict) and str(result.get('status_code')) == '403':
                                # Cooldown 10 minutes
                                next_ocr_allowed_at = time.time() + 600.0
                        except Exception:
                            pass
                except Exception as e:
                    # Do not crash the thread on OCR errors
                    print(f"Plate OCR error: {e}")
            
            # Modest sleep to reduce CPU
            time.sleep(0.02)

        # Cleanup
        try:
            if plate_video is not None:
                plate_video.release()
        except Exception:
            pass

    plate_scan_thread = threading.Thread(target=_run, daemon=True)
    plate_scan_thread.start()

def stop_plate_scanning():
    """Stop license plate scanning"""
    global plate_is_scanning
    plate_is_scanning = False

@app.route('/start-scanning', methods=['POST'])
def start_scanning():
    """Start face scanning in background thread"""
    try:
        # Start scanning in background thread
        global face_scan_thread
        face_scan_thread = threading.Thread(target=start_face_scanning, daemon=True)
        face_scan_thread.start()
        return jsonify({"status": "success", "message": "Face scanning started"})
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/start-plate-scanning', methods=['POST'])
def start_plate_scanning_endpoint():
    """Start license plate scanning in background thread"""
    try:
        # Stop face scanning to free the camera if running
        stop_face_scanning()
        req = request.get_json(silent=True) or {}
        idx = req.get('index')
        be = req.get('backend')
        backend_id = None
        if isinstance(be, str):
            if be.lower() in ['avfoundation', 'av']:
                try:
                    backend_id = cv2.CAP_AVFOUNDATION
                except Exception:
                    backend_id = None
            else:
                backend_id = None
        elif isinstance(be, int):
            backend_id = be

        start_plate_scanning(idx if isinstance(idx, int) else None, backend_id)
        ready = plate_video is not None and plate_video.isOpened()
        return jsonify({"status": "success", "message": "Plate scanning started", "camera_ready": ready})
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/camera-scan', methods=['GET'])
def camera_scan():
    """Probe camera indices/backends and report which can open and read frames."""
    results = []
    backends = [(None, 'default')]
    try:
        backends.append((cv2.CAP_AVFOUNDATION, 'avfoundation'))
    except Exception:
        pass
    for idx in range(0, 10):
        for backend_id, backend_name in backends:
            opened = False
            read_ok = False
            try:
                cap = cv2.VideoCapture(idx) if backend_id is None else cv2.VideoCapture(idx, backend_id)
                opened = cap.isOpened()
                if opened:
                    for _ in range(6):
                        ret, _ = cap.read()
                        if ret:
                            read_ok = True
                            break
                        time.sleep(0.03)
                try:
                    cap.release()
                except Exception:
                    pass
            except Exception:
                pass
            results.append({
                'index': idx,
                'backend': backend_name,
                'opened': opened,
                'read_ok': read_ok
            })
    return jsonify({'status': 'success', 'results': results})

@app.route('/select-camera', methods=['POST'])
def select_camera():
    """Set preferred camera index/backend for plate scanning; will take effect on next start."""
    global preferred_camera_index, preferred_camera_backend
    try:
        req = request.get_json(force=True)
        idx = req.get('index')
        be = req.get('backend')
        backend_id = None
        if isinstance(be, str):
            if be.lower() in ['avfoundation', 'av']:
                try:
                    backend_id = cv2.CAP_AVFOUNDATION
                except Exception:
                    backend_id = None
        elif isinstance(be, int):
            backend_id = be
        preferred_camera_index = idx if isinstance(idx, int) else None
        preferred_camera_backend = backend_id
        return jsonify({'status': 'success', 'preferred_index': preferred_camera_index, 'preferred_backend': be})
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 400

@app.route('/stop-scanning', methods=['POST'])
def stop_scanning():
    """Stop face scanning"""
    try:
        stop_face_scanning()
        return jsonify({"status": "success", "message": "Face scanning stopped"})
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/stop-plate-scanning', methods=['POST'])
def stop_plate_scanning_endpoint():
    """Stop license plate scanning"""
    try:
        stop_plate_scanning()
        return jsonify({"status": "success", "message": "Plate scanning stopped"})
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/get-current-user', methods=['GET'])
def get_current_user():
    """Get current detected user"""
    global current_user, face_present
    if current_user:
        return jsonify({
            "status": "success", 
            "user": current_user,
            "is_registered": True,
            "face_present": True
        })
    else:
        return jsonify({
            "status": "success", 
            "user": None,
            "is_registered": False,
            "face_present": face_present
        })

@app.route('/plate-reading', methods=['GET'])
def plate_reading():
    """Get the current recognized license plate text and metadata"""
    try:
        data = {
            "status": "success",
            "scanning": plate_is_scanning,
            "plate_text": plate_current_text,
            "confidence": plate_current_confidence,
            "timestamp": plate_last_timestamp,
        }
        return jsonify(data)
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/plate-frame', methods=['GET'])
def plate_frame():
    """Get the latest camera frame as JPEG for UI display"""
    try:
        if plate_latest_frame_jpeg is None:
            return jsonify({"status": "error", "message": "No frame available"}), 404
        return Response(plate_latest_frame_jpeg, mimetype='image/jpeg')
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/register-face', methods=['POST'])
def register_new_face():
    """Register a new face from base64 image data"""
    global known_encodings, known_labels
    
    try:
        data = request.get_json()
        if not data or 'image' not in data or 'name' not in data:
            return jsonify({"status": "error", "message": "Missing image or name data"}), 400
            
        # Decode base64 image
        image_data = base64.b64decode(data['image'].split(',')[1])
        image = Image.open(BytesIO(image_data))
        
        # Convert to numpy array
        image_array = np.array(image)
        
        # Detect face
        face_locations = face_recognition.face_locations(image_array)
        if not face_locations:
            return jsonify({"status": "error", "message": "No face detected in image"}), 400
            
        # Generate face encoding
        face_encoding = face_recognition.face_encodings(image_array, face_locations)[0]
        
        # Save image
        filename = f"{data['name'].replace(' ', '_')}.jpeg"
        image_path = os.path.join(REGISTERED_FACES_PATH, filename)
        image.save(image_path, "JPEG")
        
        # Update accounts.json
        account_num = len(known_labels) + 1000  # Simple account number generation
        
        if not os.path.exists(JSON_PATH):
            account_data = {}
        else:
            with open(JSON_PATH, 'r') as f:
                account_data = json.load(f)
                
        account_data[filename] = {
            "name": data['name'],
            "accounts": [account_num]
        }
        
        with open(JSON_PATH, 'w') as f:
            json.dump(account_data, f, indent=2)
            
        # Reload known faces
        known_encodings, known_labels = load_registered_faces(REGISTERED_FACES_PATH, JSON_PATH)
        
        return jsonify({
            "status": "success", 
            "message": "Face registered successfully",
            "account_number": account_num
        })
        
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({"status": "healthy", "scanning": is_scanning})

if __name__ == '__main__':
    # Ensure directories exist
    os.makedirs(REGISTERED_FACES_PATH, exist_ok=True)
    
    # Load initial face data
    known_encodings, known_labels = load_registered_faces(REGISTERED_FACES_PATH, JSON_PATH)
    
    print("Starting Face Recognition API...")
    print(f"Loaded {len(known_encodings)} registered faces")
    
    app.run(host='0.0.0.0', port=5001, debug=True) 