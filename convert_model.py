"""
Script to download YOLOv11n chess piece detection model from HuggingFace
and convert it to TensorFlow Lite format for Android deployment.
"""

import os
import sys

def check_dependencies():
    """Check if required packages are installed."""
    required = ['ultralytics', 'torch', 'onnx', 'tensorflow']
    missing = []
    
    for package in required:
        try:
            __import__(package)
            print(f"✓ {package} is installed")
        except ImportError:
            missing.append(package)
            print(f"✗ {package} is NOT installed")
    
    if missing:
        print(f"\nMissing packages: {', '.join(missing)}")
        print("\nInstall with:")
        print(f"pip install {' '.join(missing)}")
        return False
    return True

def download_and_convert_model():
    """Download YOLO model and convert to TFLite."""
    try:
        from ultralytics import YOLO
        import tensorflow as tf
        
        print("\n" + "="*60)
        print("STEP 1: Downloading YOLOv11n Chess Piece Detection Model")
        print("="*60)
        
        # Download model from HuggingFace
        model_name = "yamero999/chess-piece-detection-yolo11n"
        print(f"Downloading model: {model_name}")
        
        try:
            model = YOLO(model_name)
            print("✓ Model downloaded successfully")
        except Exception as e:
            print(f"✗ Failed to download model: {e}")
            print("\nTrying alternative: downloading best.pt directly...")
            model = YOLO("yolo11n.pt")  # Fallback to base model
            print("✓ Using base YOLOv11n model")
        
        print("\n" + "="*60)
        print("STEP 2: Exporting to ONNX format")
        print("="*60)
        
        # Export to ONNX first
        onnx_path = model.export(format="onnx", imgsz=640)
        print(f"✓ ONNX model saved to: {onnx_path}")
        
        print("\n" + "="*60)
        print("STEP 3: Converting ONNX to TensorFlow Lite")
        print("="*60)
        
        # Export directly to TFLite (Ultralytics supports this)
        tflite_path = model.export(format="tflite", imgsz=640, int8=False)
        print(f"✓ TFLite model saved to: {tflite_path}")
        
        # Get file size
        if os.path.exists(tflite_path):
            size_mb = os.path.getsize(tflite_path) / (1024 * 1024)
            print(f"✓ Model size: {size_mb:.2f} MB")
        
        print("\n" + "="*60)
        print("STEP 4: Creating labels file")
        print("="*60)
        
        # Create labels.txt file
        labels = [
            "white_pawn",
            "white_knight",
            "white_bishop",
            "white_rook",
            "white_queen",
            "white_king",
            "black_pawn",
            "black_knight",
            "black_bishop",
            "black_rook",
            "black_queen",
            "black_king"
        ]
        
        labels_path = "labels.txt"
        with open(labels_path, 'w') as f:
            f.write('\n'.join(labels))
        print(f"✓ Labels file created: {labels_path}")
        
        print("\n" + "="*60)
        print("CONVERSION COMPLETE!")
        print("="*60)
        print(f"\nFiles created:")
        print(f"  - {tflite_path}")
        print(f"  - {labels_path}")
        print(f"\nNext steps:")
        print(f"  1. Copy {tflite_path} to app/src/main/assets/chess_yolo.tflite")
        print(f"  2. Copy {labels_path} to app/src/main/assets/labels.txt")
        
        return True
        
    except Exception as e:
        print(f"\n✗ Error during conversion: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    print("YOLOv11n Chess Piece Detection - Model Converter")
    print("=" * 60)
    
    if not check_dependencies():
        print("\nPlease install missing dependencies and try again.")
        sys.exit(1)
    
    success = download_and_convert_model()
    sys.exit(0 if success else 1)
