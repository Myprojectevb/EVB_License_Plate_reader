import cv2
import numpy as np
from PIL import Image
import os
import requests

from secrets import OPENALPR_API_KEY

def isolate_plate_id(image_path: str, debug: bool = True):
    # 1) Load the image in BGR
    img_bgr = cv2.imread(image_path)
    if img_bgr is None:
        raise FileNotFoundError(f"Could not open: {image_path}")
    img_h, img_w = img_bgr.shape[:2]

    # 2) Convert to grayscale
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    Image.fromarray(gray).save("gray.jpg")

    # Increase brightness by adding a constant value of 30 to every pixel (clamped to 255)
    brightened = cv2.convertScaleAbs(gray, alpha=1.0, beta=60)
    Image.fromarray(brightened).save("brightened.jpg")

    blurred = cv2.GaussianBlur(brightened, (21, 21), 0)

    # 3) Otsu’s threshold (binary inverted: letters=white, background=black)
    _, thresh = cv2.threshold(blurred, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)

    # 4) Small morphology close to fill holes (3×3 kernel)
    kernel_close = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
    closed = cv2.morphologyEx(thresh, cv2.MORPH_CLOSE, kernel_close, iterations=1)

    # 5) Find all contours
    contours, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        raise RuntimeError("No contours found in thresholded plate!")

    # Prepare debug image showing all kept boxes
    if debug:
        debug_all = closed.copy()

    # 6) Compute bounding boxes for all contours; optionally discard full‐plate box
    all_bboxes = []
    for cnt in contours:
        x, y, w, h = cv2.boundingRect(cnt)
        # If you still want to drop the single box that covers entire plate, you can uncomment:
        # if x <= 1 and y <= 1 and w >= img_w - 2 and h >= img_h - 2:
        #     continue
        all_bboxes.append((x, y, w, h))

    if not all_bboxes:
        raise RuntimeError("No contours remain after basic filtering!")

    # 7) Draw every kept box in blue on debug_all
    if debug:
        for (x, y, w, h) in all_bboxes:
            cv2.rectangle(debug_all, (x, y), (x + w, y + h), (255, 0, 0), 1)
        cv2.imwrite("debug_all_contours.png", debug_all)

    # 8) Group boxes by height within a tolerance (5% of plate height)
    height_tolerance = 0.05  # 5% of image height
    groups: list[list[tuple[int,int,int,int]]] = []

    for box in all_bboxes:
        x, y, w, h = box
        placed = False
        for group in groups:
            # Compare to representative height of this group (first box’s height)
            _, _, _, h_rep = group[0]
            if abs(h - h_rep) <= height_tolerance * img_h:
                group.append(box)
                placed = True
                break
        if not placed:
            groups.append([box])

    # 9) Discard any group of size 1 (singleton), assuming noise or small text
    larger_groups = [g for g in groups if len(g) > 1]

    # If all groups were singletons (unlikely but possible), revert to original grouping
    if larger_groups:
        candidate_groups = larger_groups
    else:
        candidate_groups = groups

    # 10) Pick the cluster with the largest average height
    best_group = max(candidate_groups, key=lambda g: np.mean([b[3] for b in g]))

    # Compute the union of all boxes in the chosen group
    xs = [x for (x, y, w, h) in best_group] + [x + w for (x, y, w, h) in best_group]
    ys = [y for (x, y, w, h) in best_group] + [y + h for (x, y, w, h) in best_group]
    x0, y0 = int(min(xs)), int(min(ys))
    x1, y1 = int(max(xs)), int(max(ys))

    # 11) Draw the chosen‐cluster boxes (yellow) and the final merged box (green)
    if debug:
        debug_chosen = closed.copy()
        for (x, y, w, h) in best_group:
            cv2.rectangle(debug_chosen, (x, y), (x + w, y + h), (0, 255, 255), 2)
        cv2.imwrite("debug_chosen_group.png", debug_chosen)

        debug_final = debug_all.copy()
        # Highlight chosen group in yellow
        for (x, y, w, h) in best_group:
            cv2.rectangle(debug_final, (x, y), (x + w, y + h), (0, 255, 255), 2)
        # Draw the final merged box in green
        cv2.rectangle(debug_final, (x0, y0), (x1, y1), (0, 255, 0), 2)
        cv2.imwrite("debug_final_region.png", debug_final)

    # 12) Crop the final bounding box (with a tiny padding of 2px)
    pad = 2
    x0p = max(x0 - pad, 0)
    y0p = max(y0 - pad, 0)
    x1p = min(x1 + pad, img_w - 1)
    y1p = min(y1 + pad, img_h - 1)
    cropped_bgr = img_bgr[y0p:y1p, x0p:x1p]

    if debug:
        cv2.imwrite("debug_crop.png", cropped_bgr)

    return (x0p, y0p, x1p - x0p, y1p - y0p), cropped_bgr

def process(img: np.ndarray) -> np.ndarray:
    # Load image with OpenCV and convert BGR → RGB (EasyOCR expects RGB)
    img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

    # 2) (Optional) You can resize or do adaptive thresholding if needed,
    #    but EasyOCR often does fine on the raw RGB image. If you find noise,
    #    you might do:
    gray = cv2.cvtColor(img_rgb, cv2.COLOR_BGR2GRAY)
    # gray = cv2.resize(gray, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    # img_rgb = cv2.cvtColor(gray, cv2.COLOR_GRAY2RGB)
    
    # Convert convert RGB → BGR (save expects RGB) and save to disk
    save = cv2.cvtColor(gray, cv2.COLOR_RGB2BGR)
    save_path = "processed.jpg"
    cv2.imwrite(save_path, save)

    return save_path

def ocr(img: np.ndarray) -> str:
    img_processed_path = process(img)
    plate_text = ocr_openalpr(img_processed_path)
    return plate_text

def ocr_openalpr(img_path: str) -> str:
    API_KEY = OPENALPR_API_KEY
    with open(img_path, 'rb') as img_file:
        response = requests.post(
            'https://api.platerecognizer.com/v1/plate-reader/',
            files=dict(upload=img_file),
            headers={'Authorization': f'Token {API_KEY}'}
        )
    return response.json()

if __name__ == "__main__":
    dir_path = "data_shortlisted"
    for f in os.listdir(dir_path):
        if f.startswith(".") or not f.lower().endswith((".jpg", ".jpeg", ".png", ".bmp")):
            continue
        plate_image_path = os.path.join(dir_path, f)

        img = cv2.imread(plate_image_path)
        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        
        # Run OCR
        plate_id = ocr(img_rgb)['results'][0]['plate']
        print(f"ID for {f}:", repr(plate_id))
        
        while input() != ';':
            continue
