import cv2
import time
import datetime
import os
from collections import deque

cap = cv2.VideoCapture(0)
fgbg = cv2.createBackgroundSubtractorMOG2()
hog = cv2.HOGDescriptor()
hog.setSVMDetector(cv2.HOGDescriptor_getDefaultPeopleDetector())

output_dir = "recordings"
os.makedirs(output_dir, exist_ok=True)

recording = False
frame_buffer = deque()
last_seen_time = 0
RECORDING_GRACE_PERIOD = 7  # seconds
fps = 20.0
frame_width = int(cap.get(3))
frame_height = int(cap.get(4))

while True:
    ret, frame = cap.read()
    if not ret:
        break

    fgmask = fgbg.apply(frame)
    contours, _ = cv2.findContours(fgmask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    motion_detected = any(cv2.contourArea(cnt) > 1000 for cnt in contours)

    human_detected = False
    if motion_detected:
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        boxes, _ = hog.detectMultiScale(gray, winStride=(8, 8))
        if len(boxes) > 0:
            human_detected = True
            last_seen_time = time.time()

    current_time = time.time()
    if human_detected:
        if not recording:
            timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
            filename = os.path.join(output_dir, f"motion_{timestamp}.mp4")
            print(f"Recording...")
            frame_buffer.clear()
            recording = True
        frame_buffer.append(frame.copy())
    elif recording and (current_time - last_seen_time < RECORDING_GRACE_PERIOD):
        frame_buffer.append(frame.copy())
    elif recording and (current_time - last_seen_time >= RECORDING_GRACE_PERIOD):
        # Trim the last RECORDING_GRACE_PERIOD seconds of frames
        grace_frame_count = int(RECORDING_GRACE_PERIOD * fps)
        trimmed_frames = list(frame_buffer)[:-grace_frame_count] if len(frame_buffer) > grace_frame_count else []
        
        if trimmed_frames:
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            out = cv2.VideoWriter(filename, fourcc, fps, (frame_width, frame_height))
            for f in trimmed_frames:
                out.write(f)
            out.release()
            print(f"Recording saved: {filename}")
        recording = False
        frame_buffer.clear()

    cv2.imshow('Surveillance', frame)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# Clean up if recording when loop ends
if recording:
    grace_frame_count = int(RECORDING_GRACE_PERIOD * fps)
    trimmed_frames = list(frame_buffer)[:-grace_frame_count] if len(frame_buffer) > grace_frame_count else []
    if trimmed_frames:
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        filename = os.path.join(output_dir, f"motion_{timestamp}.mp4")
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = cv2.VideoWriter(filename, fourcc, fps, (frame_width, frame_height))
        for f in trimmed_frames:
            out.write(f)
        out.release()
        print(f"Recording saved: {filename}")

cap.release()
cv2.destroyAllWindows()
