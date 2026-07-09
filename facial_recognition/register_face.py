import cv2
import face_recognition
import os
import json
import time
import random

JSON_PATH = "accounts.json"
REGISTERED_FACES_PATH = "registered_faces"

# Load registered face encodings and account mappings
def load_registered_faces(registered_dir, account_map_path):
    encodings = []
    labels = []
    with open(account_map_path, 'r') as f:
        account_map = json.load(f)

    for filename in os.listdir(registered_dir):
        if not filename.lower().endswith(('.png', '.jpg', '.jpeg')) or filename.startswith('.'):
            continue

        path = os.path.join(registered_dir, filename)
        image = face_recognition.load_image_file(path)
        face_locations = face_recognition.face_locations(image)
        if not face_locations:
            print(f"No face found in {filename}, skipping.")
            continue
        encoding = face_recognition.face_encodings(image, known_face_locations=face_locations)[0]
        encodings.append(encoding)
        labels.append((filename, account_map.get(filename, [])))
    return encodings, labels

def generate_account_number():
    return random.randint(0, 10000)

def update_account_mapping(name, face_img_path, account_num):
    with open(JSON_PATH, "r") as f:
        data = json.load(f)
    data[face_img_path] = {
        "name": name,
        "accounts": [account_num]
    }
    with open(JSON_PATH, "w") as f:
        json.dump(data, f, indent=2)

def register_face():
    video = cv2.VideoCapture(0)
    ret = False
    while not ret:
        ret, frame = video.read()
    print("Face scan complete.")
    name = input("Enter your full legal name: ")
    face_img_path = '_'.join(name.split()) + ".jpeg"
    account_num = generate_account_number()
    print(f"Your account number: {account_num}")

    # Store face scan
    cv2.imwrite(f"registered_faces/{face_img_path}", frame)
    # Update account mapping
    update_account_mapping(name, face_img_path, account_num)

# Process live video
def process_webcam(known_encodings, known_labels, sample_rate=0.5):
    video = cv2.VideoCapture(0)
    last_processed = 0

    while True:
        label = ""
        time.sleep(1)
        ret, frame = video.read()
        if not ret:
            break

        current_time = time.time()
        if current_time - last_processed >= 1.0 / sample_rate:
            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            face_locations = face_recognition.face_locations(rgb_frame)
            face_encodings = face_recognition.face_encodings(rgb_frame, face_locations)

            for (top, right, bottom, left), face_encoding in zip(face_locations, face_encodings):
                matches = face_recognition.compare_faces(known_encodings, face_encoding, tolerance=0.5)
                if True in matches:
                    idx = matches.index(True)
                    filename, accounts = known_labels[idx]
                    label = "registered"
                    print(f"Detected: {filename} — Accounts: {accounts}")
                else:
                    label = "unregistered"

                color = (0, 255, 0) if label == "registered" else (0, 0, 255)
                cv2.rectangle(frame, (left, top), (right, bottom), color, 2)
                cv2.putText(frame, label, (left, top - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)

            last_processed = current_time

        cv2.imshow("Live Face Recognition", frame)
        
        if (label == "registered"):
            account_num = input(f"Choose an account from {accounts}: \n")
            begin = input("Begin charging? y/n: ")
            if (begin == "y"):
                print(f"Charging for account {account_num}.")
                break
        else:
            register = input("Unrecognized. Would you like to create a new account? y/n: ")
            if (register == "y"):
                register_face()
                known_encodings, known_labels = load_registered_faces(REGISTERED_FACES_PATH, JSON_PATH)

        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    video.release()
    cv2.destroyAllWindows()

# Main
if __name__ == "__main__":
    registered_dir = "registered_faces"
    account_map_path = "accounts.json"

    known_encodings, known_labels = load_registered_faces(registered_dir, account_map_path)
    process_webcam(known_encodings, known_labels, sample_rate=5.0)  # 2 frames per second
