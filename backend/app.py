from flask import Flask, request, send_file, abort
from datetime import datetime, UTC
import os

from PIL import Image
import pytesseract
import easyocr

app = Flask(__name__)

UPLOAD_FOLDER = os.path.join(os.path.dirname(__file__), "uploads")
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# Path to tesseract executable if needed
pytesseract.pytesseract.tesseract_cmd = r'C:\\Program Files\\Tesseract-OCR\\tesseract.exe'

# Enable GPU for EasyOCR if desired
use_gpu = os.environ.get("EASYOCR_GPU", "false").lower() == "true"

@app.route("/uploadImage", methods=["POST"])
def upload_image():
    img_bytes = request.get_data()
    if not img_bytes:
        abort(400, "No image data")

    # fetch args
    ts = request.args.get("ts") or datetime.now(UTC).strftime("%Y%m%d_%H%M%S")
    loc = request.args.get("loc") or "unknown"
    file_type = request.args.get("file_type") or "unknown"

    # sanitize
    safe_loc = loc.replace(",", "_").replace(" ", "_")
    safe_type = file_type.replace(" ", "_")
    img_filename  = f"{ts}_{safe_type}_{safe_loc}.jpg"
    txt_filename  = f"{ts}_{safe_type}_{safe_loc}.txt"

    # save image
    img_path = os.path.join(UPLOAD_FOLDER, img_filename)
    with open(img_path, "wb") as img_f:
        img_f.write(img_bytes)

    # perform OCR
    normalized = file_type.strip().lower()
    try:
        if normalized in ("docs", "table", "form"):
            img = Image.open(img_path)
            ocr_text = pytesseract.image_to_string(img)
        elif normalized == "handwriting":
            reader = easyocr.Reader(["en"], gpu=use_gpu)
            result = reader.readtext(img_path, detail=0)
            ocr_text = "\n".join(result)
        else:
            ocr_text = "[No OCR performed]"
    except Exception as e:
        ocr_text = f"[OCR failed: {e}]"

    # prepare text file with header + real newlines
    header = (
        f"Captured at: { datetime.now(UTC).strftime('%Y-%m-%d %H:%M:%S UTC') }\n"
        f"Location   : { loc }\n"
        f"Type       : { file_type }\n"
        "----------------------------------------\n\n"
    )
    txt_path = os.path.join(UPLOAD_FOLDER, txt_filename)
    with open(txt_path, "w", encoding="utf-8") as txt_f:
        txt_f.write(header)
        txt_f.write(ocr_text)

    # send back as downloadable attachment
    return send_file(
        txt_path,
        as_attachment=True,
        download_name=txt_filename,
        mimetype="text/plain"
    )

@app.route("/ping", methods=["GET"])
def ping():
    return "pong", 200

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)