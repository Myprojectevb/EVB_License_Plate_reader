import cv2
import numpy as np
import time

from openalpr_ocr import ocr

# Set paths to YOLO config and weights
CFG_PATH = "models/yolov4.cfg"
WEIGHTS_PATH = "models/yolov4.weights"
CONFIDENCE_THRESHOLD = 0.5
NMS_THRESHOLD = 0.4

# Load YOLO network
net = cv2.dnn.readNet(WEIGHTS_PATH, CFG_PATH)
net.setPreferableBackend(cv2.dnn.DNN_BACKEND_OPENCV)
layer_names = net.getLayerNames()
output_layers = [layer_names[i - 1] for i in net.getUnconnectedOutLayers().flatten()]

# Dummy OCR function (replace with your real OCR logic)
def run_ocr_on_plate(plate_img):
    j = ocr(plate_img)
    return str(j['results'][0]['plate']) if 'throttled' not in str(j) and len(j['results'])>0 else 'No plate'

# Start video stream
cap = cv2.VideoCapture(0)

print("Starting video stream... Press 'q' to quit.")
last_processed = 0

while True:
    ret, frame = cap.read()
    if not ret:
        print("Failed to capture frame.")
        break

    current_time = time.time()

    # Only process one frame per second
    if current_time - last_processed >= 4:
        last_processed = current_time

        height, width = frame.shape[:2]
        blob = cv2.dnn.blobFromImage(frame, 1 / 255.0, (416, 416), swapRB=True, crop=False)
        net.setInput(blob)
        outputs = net.forward(output_layers)

        boxes = []
        confidences = []

        for output in outputs:
            for detection in output:
                scores = detection[5:]
                class_id = np.argmax(scores)
                confidence = scores[class_id]
                if confidence > CONFIDENCE_THRESHOLD:
                    center_x, center_y, w, h = (detection[0:4] * [width, height, width, height]).astype(int)
                    x = int(center_x - w / 2)
                    y = int(center_y - h / 2)
                    boxes.append([x, y, w, h])
                    confidences.append(float(confidence))

        indices = cv2.dnn.NMSBoxes(boxes, confidences, CONFIDENCE_THRESHOLD, NMS_THRESHOLD)

        if indices is not None and len(indices) > 0:
            for i in indices:
                i = i[0] if isinstance(i, (list, tuple, np.ndarray)) else i
                x, y, w, h = boxes[i]
                cv2.rectangle(frame, (x, y), (x + w, y + h), (0, 255, 0), 2)

                plate_crop = frame[y:y + h, x:x + w]
                if plate_crop.size > 0:
                    text = run_ocr_on_plate(plate_crop)
                    print(text)
                    cv2.putText(frame, text, (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 255), 2)

    cv2.imshow("License Plate Detection + OCR", frame)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
