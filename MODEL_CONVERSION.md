# YOLO Model Conversion Instructions

The automatic model conversion encountered an issue with TFLite export. Here are manual steps to convert the YOLOv11n chess piece detection model:

## Option 1: Download Pre-converted Model (Recommended)

If a pre-converted `.tflite` model is available from the model author:

1. Visit: https://huggingface.co/yamero999/chess-piece-detection-yolo11n
2. Look for a `.tflite` file in the model files
3. Download and place it in `app/src/main/assets/chess_yolo.tflite`

## Option 2: Manual Conversion

### Step 1: Install Dependencies
```bash
pip install ultralytics onnx tf2onnx tensorflow
```

### Step 2: Download and Export YOLO Model
```python
from ultralytics import YOLO

# Download model
model = YOLO("yamero999/chess-piece-detection-yolo11n")

# Export to ONNX (this works reliably)
model.export(format="onnx", imgsz=640)
```

This will create `yolo11n.onnx`

### Step 3: Convert ONNX to TFLite using TensorFlow

```python
import tensorflow as tf
import onnx
from onnx_tf.backend import prepare

# Load ONNX model
onnx_model = onnx.load("yolo11n.onnx")

# Convert to TensorFlow
tf_rep = prepare(onnx_model)

# Export as SavedModel
tf_rep.export_graph("yolo_savedmodel")

# Convert SavedModel to TFLite
converter = tf.lite.TFLiteConverter.from_saved_model("yolo_savedmodel")
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]

tflite_model = converter.convert()

# Save TFLite model
with open("chess_yolo.tflite", "wb") as f:
    f.write(tflite_model)
```

### Step 4: Copy to Android Project
```bash
cp chess_yolo.tflite app/src/main/assets/
```

## Option 3: Use Alternative Model

If conversion continues to fail, you can use an alternative pre-trained chess piece detection model:

1. **Roboflow Universe**: https://universe.roboflow.com/search?q=chess+pieces
   - Many models have direct TFLite export options
   
2. **YOLOv8 Chess Models**: Search for "chess piece detection yolov8 tflite" on GitHub

## Temporary Workaround

For testing purposes, you can create a dummy model file:
```bash
# This won't work for actual detection, but allows the app to build
touch app/src/main/assets/chess_yolo.tflite
```

The app will load but detection will fail. This is useful for testing the UI and overlay functionality.

## Verification

Once you have the model file:
1. Check file size: should be 6-15 MB
2. Place in `app/src/main/assets/chess_yolo.tflite`
3. Build and run the app
4. Check logcat for "YOLO model loaded successfully"
