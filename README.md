Smart Data Capture System<br><br>
A lightweight Android + Flask stack for fast voice / image data collection & OCR<br>

Table of Contents
Overview

1.Features

2.Architecture

3.Prerequisites

4.Backend (Flask) setup

5.Android app setup

6.Build & Run

7.REST API

8.Folder structure

9.Troubleshooting

10.License

Overview<br><br>
Smart Data Capture System (SDCS) is an end-to-end sample project that demonstrates how to:

Capture structured data from a mobile device via

Camera (docs / tables / forms / handwriting)

Gallery uploads

Speech-to-text voice input

Send the raw media to a lightweight Flask backend

Process images with Tesseract (printed text) or EasyOCR (handwriting) on the server

Return the extracted text file to the device as a download

Persist & manage results locally on Android (RecyclerView with multi-select & delete)

The entire solution is less than <250 LOC of Python and ≈1.2 kLOC of Android/Kotlin/Java, making it easy to read, hack, and extend.

Features:

| Module             | Capability                                                                                                                    |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
|   Camera Scan      | Inline preview, type picker (Docs / Table / Form / Handwriting), automatic file naming, server round-trip, local save         |
|   Gallery Upload   | Choose an existing image, pick type, same OCR pipeline                                                                        |
|   Voice Input      | Android SpeechRecognizer → backend validation                                                                                 |
|   Records View     | RecyclerView list of all downloaded `.txt` results, long-press multi-select, batch delete, legacy `captured_data.txt` support |
|   Backend          | `/uploadImage` endpoint, GPU toggled EasyOCR, auto-timestamping, location tagging, download-as-attachment                     |

Architecture:<br>
<pre>
┌───────────────────┐        POST image/voice         ┌────────────────────────┐
│  Android app      │ ──────────────────────────────► │  Flask backend         │
│  (Kotlin/Java)    │                                │  /uploadImage          │
│                   │ ◄───────── .txt attachment ─── │  OCR (Tesseract/Easy)  │
└───────────────────┘                                └────────────────────────┘
        │                                                          │
        ▼                                                          ▼
Internal storage                                uploads/&lt;ts&gt;_&lt;type&gt;_&lt;loc&gt;.jpg/.txt
</pre>



Prerequisites:<br>
| Tool               | Version (known good)                                             |
| ------------------ | ---------------------------------------------------------------- |
|   Android Studio   | Iguana / Flamingo 2023.3.1+                                      |
|   Gradle           | 8.x                                                              |
|   Python           | 3.9+                                                             |
|   pip              | 23+                                                              |
|   Tesseract-OCR    | 5.x (install & add to PATH, or edit `pytesseract.tesseract_cmd`) |
|   CUDA + cuDNN     | *Optional* if you want GPU EasyOCR                               |

Backend (Flask) setup:<br>
cd backend
python -m venv venv
source venv/bin/activate          # Windows: venv\Scripts\activate
pip install -r requirements.txt   # Flask, pillow, pytesseract, easyocr
export FLASK_APP=app.py
# Enable GPU for EasyOCR (optional)
export EASYOCR_GPU=true
python app.py                     # listens on 0.0.0.0:5000
<u>Windows note</u>:
Update pytesseract.pytesseract.tesseract_cmd in app.py if Tesseract is in a non-standard location.

Android app setup
Open the smartdatacapturesystem folder in Android Studio.

Allow Gradle to sync and download dependencies.

Plug in a device or start an emulator that has a camera.

Click Run ▶.

The first run will ask for Camera, Microphone, Fine Location, and All-Files (Scoped Storage) permissions.

Build & Run:<br>
| Task                  | Command                     |
| --------------------- | --------------------------- |
|   Backend             | `python app.py`             |
|   Unit-test backend   | `pytest`                    |
|   APK (release)       | `./gradlew assembleRelease` |
|   Clean               | `./gradlew clean`           |

REST API:<br>
POST /uploadImage
| Query param | Description                                        |
| ----------- | -------------------------------------------------- |
| `ts`        | **(optional)** Client timestamp, `yyyyMMdd_HHmmss` |
| `loc`       | **(optional)** Human-readable location string      |
| `file_type` | One of `docs`, `table`, `form`, `handwriting`      |

Body: raw application/octet-stream (JPEG).
Success (200): Returns text/plain attachment with OCR text and headers.
Errors: 400 if no body, 500 on OCR failure.

GET /ping
Health-check: returns pong.

Folder structure:<br>
smartdatacapturesystem/<br>
│<br>
├─ app/                      # Android source<br>
│   ├─ java/com/.../*.java   # Activities, adapters<br>
│   └─ res/                  # Layouts, drawables<br>
│<br>
├─ backend/                  # Flask server<br>
│   ├─ app.py<br>
│   ├─ requirements.txt<br>
│   └─ uploads/              # Auto-created for images & txt<br>
│<br>
└─ README.md                 # You’re here<br>

Troubleshooting:
| Symptom                         | Fix                                                                                                                                    |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
|   `Network error` toast         | Ensure phone/emulator can reach `http://<PC-IP>:5000`. Adjust `BASE_URL` in `RetrofitClient` if backend runs on a different host/port. |
|   Tesseract not found           | Edit `pytesseract.tesseract_cmd` in `backend/app.py` to the full path of `tesseract.exe` or binary.                                    |
|   Permission denied (storage)   | On Android 11+, enable “All files access” in Settings ▸ Apps ▸ SDCS ▸ Permissions.                                                     |
|   EasyOCR CUDA errors           | Set `EASYOCR_GPU=false` or install correct CUDA/cuDNN versions.                                                                        |

License
MIT © 2025 Your Name. Feel free to copy, fork, and improve!
