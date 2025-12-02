"""
Simple script to download and convert YOLOv11n chess model to TFLite.
Run this script in the android-chess-assistant directory.

Steps:
1. Downloads best_mobile.onnx from HuggingFace
2. Converts ONNX to TFLite
3. Copies to app/src/main/assets/
"""

import os
import sys
import urllib.request

def download_model():
    """Download the ONNX model from HuggingFace."""
    print("\n" + "="*60)
    print("STEP 1: Downloading ONNX model from HuggingFace")
    print("="*60)
    
    url = "https://huggingface.co/yamero999/chess-piece-detection-yolo11n/resolve/main/best_mobile.onnx"
    output_file = "best_mobile.onnx"
    
    if os.path.exists(output_file):
        print(f"✓ {output_file} already exists, skipping download")
        return output_file
    
    print(f"Downloading from: {url}")
    print("This may take a few minutes (10.6 MB)...")
    
    try:
        urllib.request.urlretrieve(url, output_file)
        size_mb = os.path.getsize(output_file) / (1024 * 1024)
        print(f"✓ Downloaded successfully! Size: {size_mb:.2f} MB")
        return output_file
    except Exception as e:
        print(f"✗ Download failed: {e}")
        print("\nManual download:")
        print(f"1. Visit: https://huggingface.co/yamero999/chess-piece-detection-yolo11n/tree/main")
        print(f"2. Download 'best_mobile.onnx'")
        print(f"3. Place it in this directory")
        return None

def convert_to_tflite(onnx_file):
    """Convert ONNX model to TFLite."""
    print("\n" + "="*60)
    print("STEP 2: Converting ONNX to TFLite")
    print("="*60)
    
    try:
        import onnx
        from onnx_tf.backend import prepare
        import tensorflow as tf
        
        print("Loading ONNX model...")
        onnx_model = onnx.load(onnx_file)
        print("✓ ONNX model loaded")
        
        print("Converting to TensorFlow...")
        tf_rep = prepare(onnx_model)
        print("✓ Converted to TensorFlow")
        
        print("Exporting as SavedModel...")
        tf_rep.export_graph("yolo_savedmodel")
        print("✓ SavedModel created")
        
        print("Converting to TFLite...")
        converter = tf.lite.TFLiteConverter.from_saved_model("yolo_savedmodel")
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS,
            tf.lite.OpsSet.SELECT_TF_OPS
        ]
        
        tflite_model = converter.convert()
        
        output_file = "chess_yolo.tflite"
        with open(output_file, "wb") as f:
            f.write(tflite_model)
        
        size_mb = os.path.getsize(output_file) / (1024 * 1024)
        print(f"✓ TFLite model created: {output_file} ({size_mb:.2f} MB)")
        return output_file
        
    except ImportError as e:
        print(f"✗ Missing dependency: {e}")
        print("\nPlease install required packages:")
        print("pip install onnx onnx-tf tensorflow")
        return None
    except Exception as e:
        print(f"✗ Conversion failed: {e}")
        import traceback
        traceback.print_exc()
        return None

def copy_to_assets(tflite_file):
    """Copy TFLite model to Android assets directory."""
    print("\n" + "="*60)
    print("STEP 3: Copying to Android assets")
    print("="*60)
    
    assets_dir = "app/src/main/assets"
    
    # Create assets directory if it doesn't exist
    os.makedirs(assets_dir, exist_ok=True)
    
    dest_file = os.path.join(assets_dir, "chess_yolo.tflite")
    
    try:
        import shutil
        shutil.copy(tflite_file, dest_file)
        print(f"✓ Copied to: {dest_file}")
        
        size_mb = os.path.getsize(dest_file) / (1024 * 1024)
        print(f"✓ File size: {size_mb:.2f} MB")
        return True
    except Exception as e:
        print(f"✗ Copy failed: {e}")
        return False

def main():
    print("YOLOv11n Chess Model - Easy Converter")
    print("="*60)
    
    # Check if we're in the right directory
    if not os.path.exists("app/build.gradle.kts"):
        print("✗ Error: Not in android-chess-assistant directory")
        print("Please run this script from the project root:")
        print("  cd C:\\Users\\User\\.gemini\\antigravity\\scratch\\android-chess-assistant")
        print("  python download_and_convert.py")
        sys.exit(1)
    
    # Step 1: Download
    onnx_file = download_model()
    if not onnx_file:
        sys.exit(1)
    
    # Step 2: Convert
    tflite_file = convert_to_tflite(onnx_file)
    if not tflite_file:
        sys.exit(1)
    
    # Step 3: Copy to assets
    if not copy_to_assets(tflite_file):
        sys.exit(1)
    
    print("\n" + "="*60)
    print("SUCCESS! Model is ready to use!")
    print("="*60)
    print("\nNext steps:")
    print("1. Build the APK:")
    print("   gradlew assembleDebug")
    print("\n2. Install on your phone:")
    print("   adb install app/build/outputs/apk/debug/app-debug.apk")
    print("\n3. Test the app!")

if __name__ == "__main__":
    main()
